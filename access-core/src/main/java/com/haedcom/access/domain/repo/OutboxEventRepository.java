package com.haedcom.access.domain.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.haedcom.access.domain.enums.OutboxStatus;
import com.haedcom.access.domain.model.OutboxEvent;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio para {@link OutboxEvent}.
 *
 * <p>
 * Provee consultas y operaciones de soporte para implementar el patrón <b>Transactional Outbox</b>:
 * los eventos se persisten en la misma transacción del caso de uso, y un dispatcher los entrega de
 * forma eventual a integraciones externas (HTTP/Kafka/etc.).
 * </p>
 *
 * <h2>Estados y conceptos operacionales</h2>
 * <ul>
 * <li><b>PENDING</b>: evento pendiente de publicar.</li>
 * <li><b>PUBLISHED</b>: evento publicado exitosamente.</li>
 * <li><b>FAILED</b>: evento marcado como fallo definitivo (no retryable o max reintentos).</li>
 * </ul>
 *
 * <h2>READY / INFLIGHT (diagnóstico)</h2>
 * <ul>
 * <li><b>READY</b>: evento PENDING listo para despacho ({@code next_attempt_at_utc} es null o ya
 * venció).</li>
 * <li><b>INFLIGHT</b>: evento PENDING reclamado por un dispatcher ({@code locked_at_utc != null}).
 * Esto es <i>diagnóstico</i> (no reemplaza locks DB).</li>
 * </ul>
 *
 * <h2>Concurrencia</h2>
 * <p>
 * El claim se basa en {@code FOR UPDATE SKIP LOCKED} para permitir múltiples instancias sin doble
 * procesamiento: cada transacción toma locks exclusivos sobre filas distintas y omite las
 * bloqueadas.
 * </p>
 *
 * <h2>Recuperación de inflight atascados</h2>
 * <p>
 * Como {@code locked_at_utc/locked_by} son campos de diagnóstico (no locks reales), una instancia
 * que muere podría dejar eventos en "inflight" lógico. Para evitar que queden pegados, el método de
 * claim puede <b>reclamar</b> eventos cuyo {@code locked_at_utc} esté vencido por TTL.
 * </p>
 */
@ApplicationScoped
public class OutboxEventRepository extends BaseRepository<OutboxEvent, UUID> {

    public OutboxEventRepository() {
        super(OutboxEvent.class);
    }

    /**
     * Retorna eventos con estado {@link OutboxStatus#PENDING} ordenados por creación (FIFO
     * aproximado).
     *
     * <p>
     * Útil para escenarios simples o debugging. No aplica backoff ni locking.
     * </p>
     *
     * @param limit máximo de eventos a retornar
     * @return lista de eventos PENDING
     */
    public List<OutboxEvent> findPending(int limit) {
        return em.createQuery("""
                select e
                from OutboxEvent e
                where e.status = :status
                order by e.createdAtUtc asc
                """, OutboxEvent.class).setParameter("status", OutboxStatus.PENDING)
                .setMaxResults(limit).getResultList();
    }

    /**
     * Retorna y bloquea eventos {@code PENDING} listos para despacho (READY) usando
     * {@code FOR UPDATE SKIP LOCKED}.
     *
     * <p>
     * Se considera "ready" si:
     * </p>
     * <ul>
     * <li>{@code next_attempt_at_utc} es null, o</li>
     * <li>{@code next_attempt_at_utc <= now (UTC)}.</li>
     * </ul>
     *
     * <h3>Reclaim de inflight vencidos (TTL)</h3>
     * <p>
     * Este método también puede reclamar eventos que quedaron con {@code locked_at_utc} no-null
     * (inflight lógico) si dicho lock está vencido por TTL. Esto ayuda a recuperación ante caídas
     * de procesos antes de limpiar {@code locked_at_utc/locked_by}.
     * </p>
     *
     * <p>
     * Regla: se permite reclamar si:
     * </p>
     * <ul>
     * <li>{@code locked_at_utc is null}, o</li>
     * <li>{@code locked_at_utc <= now(UTC) - lockTtlSeconds}</li>
     * </ul>
     *
     * @param limit máximo de eventos a reclamar en esta corrida
     * @param lockTtlSeconds TTL del lock lógico (diagnóstico). Si es {@code <= 0}, se asume "sin
     *        TTL" (solo locked_at_utc null).
     * @return lista de eventos PENDING listos, bloqueados para esta transacción
     */
    @SuppressWarnings("unchecked")
    public List<OutboxEvent> findPendingReadyForUpdateSkipLocked(int limit, long lockTtlSeconds) {

        // Si lockTtlSeconds <= 0, no permitimos reclaim de inflight.
        String ttlClause = (lockTtlSeconds > 0)
                ? " (locked_at_utc is null or locked_at_utc <= ((now() at time zone 'utc') - (?2 * interval '1 second'))) "
                : " locked_at_utc is null ";

        String sql = """
                select *
                from outbox_event
                where status = 'PENDING'
                  and (next_attempt_at_utc is null
                       or next_attempt_at_utc <= (now() at time zone 'utc'))
                  and %s
                order by created_at_utc asc
                for update skip locked
                limit ?1
                """.formatted(ttlClause);

        var q = em.createNativeQuery(sql, OutboxEvent.class).setParameter(1, limit);

        if (lockTtlSeconds > 0) {
            q.setParameter(2, lockTtlSeconds);
        }

        return (List<OutboxEvent>) q.getResultList();
    }

    /**
     * Cuenta eventos por estado.
     *
     * @param status estado del outbox
     * @return cantidad de eventos en ese estado
     */
    public long countByStatus(OutboxStatus status) {
        return em.createQuery("""
                select count(e)
                from OutboxEvent e
                where e.status = :status
                """, Long.class).setParameter("status", status).getSingleResult();
    }

    /**
     * Edad del evento {@code PENDING} más viejo en segundos (lag).
     *
     * @return segundos desde {@code created_at_utc} del PENDING más viejo, o null si no hay PENDING
     */
    public Long oldestPendingAgeSeconds() {
        Object r = em
                .createNativeQuery(
                        """
                                select cast(extract(epoch from ((now() at time zone 'utc') - min(created_at_utc))) as bigint)
                                from outbox_event
                                where status = 'PENDING'
                                """)
                .getSingleResult();
        return r == null ? null : ((Number) r).longValue();
    }

    /**
     * Cuenta eventos {@code PENDING} en estado <b>INFLIGHT</b> (diagnóstico):
     * {@code locked_at_utc != null}.
     *
     * @return cantidad de PENDING inflight
     */
    public long countInflightPending() {
        Object r = em.createNativeQuery("""
                select count(*)
                from outbox_event
                where status = 'PENDING'
                  and locked_at_utc is not null
                """).getSingleResult();
        return ((Number) r).longValue();
    }

    /**
     * Cuenta eventos {@code PENDING} listos para despacho (READY).
     *
     * @return cantidad de PENDING ready
     */
    public long countReadyPending() {
        Object r = em.createNativeQuery("""
                select count(*)
                from outbox_event
                where status = 'PENDING'
                  and (next_attempt_at_utc is null
                       or next_attempt_at_utc <= (now() at time zone 'utc'))
                """).getSingleResult();
        return ((Number) r).longValue();
    }

    /**
     * Edad del evento <b>INFLIGHT</b> más viejo (segundos) según {@code locked_at_utc}.
     *
     * @return segundos desde {@code locked_at_utc} del inflight más viejo, o null si no hay
     *         inflight
     */
    public Long oldestInflightAgeSeconds() {
        Object r = em
                .createNativeQuery(
                        """
                                select cast(extract(epoch from ((now() at time zone 'utc') - min(locked_at_utc))) as bigint)
                                from outbox_event
                                where status = 'PENDING'
                                  and locked_at_utc is not null
                                """)
                .getSingleResult();
        return r == null ? null : ((Number) r).longValue();
    }

    /**
     * Libera locks lógicos vencidos (diagnóstico) dejando {@code locked_at_utc/locked_by} en null.
     *
     * <p>
     * Esto es opcional si ya haces reclaim por TTL en el claim. Puede ser útil para mantenimiento o
     * para que el gauge de inflight no se quede alto cuando hay eventos atorados por caídas.
     * </p>
     *
     * @param lockTtlSeconds TTL del lock lógico; libera los que tengan
     *        {@code locked_at_utc <= now-ttl}
     * @return cantidad de filas actualizadas
     */
    public int releaseExpiredLocks(long lockTtlSeconds) {
        if (lockTtlSeconds <= 0) {
            return 0;
        }
        Object r = em.createNativeQuery("""
                update outbox_event
                set locked_at_utc = null,
                    locked_by = null
                where status = 'PENDING'
                  and locked_at_utc is not null
                  and locked_at_utc <= ((now() at time zone 'utc') - (?1 * interval '1 second'))
                """).setParameter(1, lockTtlSeconds).executeUpdate();
        return (r instanceof Number n) ? n.intValue() : 0;
    }

    /**
     * Edad del evento PENDING más viejo que está READY para despacho (segundos).
     *
     * READY = next_attempt_at_utc is null OR next_attempt_at_utc <= now(UTC)
     *
     * @return segundos desde created_at_utc del READY más viejo, o null si no hay READY
     */
    public Long oldestReadyPendingAgeSeconds() {
        Object r = em
                .createNativeQuery(
                        """
                                select cast(extract(epoch from ((now() at time zone 'utc') - min(created_at_utc))) as bigint)
                                from outbox_event
                                where status = 'PENDING'
                                  and (next_attempt_at_utc is null
                                       or next_attempt_at_utc <= (now() at time zone 'utc'))
                                """)
                .getSingleResult();

        return r == null ? null : ((Number) r).longValue();
    }

    /**
     * Reclama (claim) un evento específico para procesamiento por una instancia, de forma atómica.
     *
     * <p>
     * Este método está pensado para reforzar la garantía de "un solo envío" cuando el flujo es:
     * </p>
     * <ol>
     * <li>El dispatcher reclama un lote de IDs (TX corta).</li>
     * <li>El processor procesa cada ID en TX separada ({@code REQUIRES_NEW}).</li>
     * </ol>
     *
     * <p>
     * Sin este claim atómico por ID, existe una ventana donde dos nodos podrían terminar enviando
     * el mismo evento (p.ej. por reinicios, reclaim por TTL, o condiciones de carrera).
     * </p>
     *
     * <h3>Reglas de claim</h3>
     * <ul>
     * <li>Solo reclama si {@code status = PENDING}.</li>
     * <li>Solo reclama si:
     * <ul>
     * <li>{@code locked_at_utc is null} (libre), o</li>
     * <li>{@code locked_at_utc < lockExpiredBeforeUtc} (lock lógico vencido), o</li>
     * <li>{@code locked_by = instanceId} (re-entrante por misma instancia).</li>
     * </ul>
     * </li>
     * </ul>
     *
     * @param idEvento id del evento outbox (obligatorio)
     * @param instanceId id de la instancia que reclama (obligatorio)
     * @param nowUtc timestamp actual en UTC (obligatorio)
     * @param lockExpiredBeforeUtc umbral UTC: locks con {@code locked_at_utc < umbral} se
     *        consideran vencidos (obligatorio)
     * @return 1 si el evento fue reclamado; 0 si no (otro nodo lo tiene reclamado o no aplica)
     */
    public int claimForProcessing(UUID idEvento, String instanceId, OffsetDateTime nowUtc,
            OffsetDateTime lockExpiredBeforeUtc) {

        Objects.requireNonNull(idEvento, "idEvento es obligatorio");
        Objects.requireNonNull(instanceId, "instanceId es obligatorio");
        Objects.requireNonNull(nowUtc, "nowUtc es obligatorio");
        Objects.requireNonNull(lockExpiredBeforeUtc, "lockExpiredBeforeUtc es obligatorio");

        Object r = em.createNativeQuery("""
                update outbox_event
                   set locked_at_utc = ?3,
                       locked_by     = ?2
                 where id_evento     = ?1
                   and status        = 'PENDING'
                   and (
                        locked_at_utc is null
                        or locked_at_utc < ?4
                        or locked_by = ?2
                   )
                """).setParameter(1, idEvento).setParameter(2, instanceId).setParameter(3, nowUtc)
                .setParameter(4, lockExpiredBeforeUtc).executeUpdate();

        return (r instanceof Number n) ? n.intValue() : 0;
    }

    /**
     * Limpia el lock lógico del evento (diagnóstico) solo si pertenece a la instancia dada.
     *
     * <p>
     * Es importante limpiar el lock de forma "ownership-safe" para evitar que una instancia elimine
     * el lock de otra por accidente (condición de carrera).
     * </p>
     *
     * @param idEvento id del evento outbox (obligatorio)
     * @param instanceId id de la instancia (obligatorio)
     * @return 1 si se limpió; 0 si no era dueño (o no existía)
     */
    public int clearLockIfOwned(UUID idEvento, String instanceId) {
        Objects.requireNonNull(idEvento, "idEvento es obligatorio");
        Objects.requireNonNull(instanceId, "instanceId es obligatorio");

        Object r = em.createNativeQuery("""
                update outbox_event
                   set locked_at_utc = null,
                       locked_by     = null
                 where id_evento     = ?1
                   and locked_by     = ?2
                """).setParameter(1, idEvento).setParameter(2, instanceId).executeUpdate();

        return (r instanceof Number n) ? n.intValue() : 0;
    }

}
