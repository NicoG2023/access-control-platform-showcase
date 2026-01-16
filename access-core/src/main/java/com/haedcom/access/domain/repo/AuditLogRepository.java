package com.haedcom.access.domain.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.haedcom.access.domain.model.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio de auditoría funcional ({@link AuditLog}).
 *
 * <p>
 * Provee consultas específicas de auditoría, manteniendo el repositorio libre de lógica de negocio.
 * </p>
 *
 * <h2>Notas</h2>
 * <ul>
 * <li>Este repositorio asume aislamiento multi-tenant por {@code idOrganizacion}.</li>
 * <li>El control transaccional se realiza en la capa de aplicación (service/listener).</li>
 * </ul>
 */
@ApplicationScoped
public class AuditLogRepository extends BaseRepository<AuditLog, UUID> {

    /**
     * Construye el repositorio.
     */
    public AuditLogRepository() {
        super(AuditLog.class);
    }

    /**
     * Lista auditoría por tenant en una ventana de tiempo.
     *
     * @param orgId tenant (obligatorio)
     * @param from inclusive (opcional)
     * @param to exclusive (opcional)
     * @param page página base 0
     * @param size tamaño página
     * @return lista de eventos auditados
     */
    public List<AuditLog> listByTenantAndTimeRange(UUID orgId, OffsetDateTime from,
            OffsetDateTime to, int page, int size) {

        StringBuilder jpql =
                new StringBuilder("select a from AuditLog a where a.idOrganizacion = :orgId");

        if (from != null)
            jpql.append(" and a.occurredAtUtc >= :from");
        if (to != null)
            jpql.append(" and a.occurredAtUtc < :to");

        jpql.append(" order by a.occurredAtUtc desc");

        var q = em.createQuery(jpql.toString(), AuditLog.class).setParameter("orgId", orgId)
                .setFirstResult(page * size).setMaxResults(size);

        if (from != null)
            q.setParameter("from", from);
        if (to != null)
            q.setParameter("to", to);

        return q.getResultList();
    }

    /**
     * Busca el primer evento auditado por correlation id dentro de un tenant.
     *
     * @param orgId tenant
     * @param correlationId id de correlación
     * @return optional con el primer match
     */
    public Optional<AuditLog> findFirstByCorrelationId(UUID orgId, String correlationId) {
        List<AuditLog> rows = em
                .createQuery("select a from AuditLog a "
                        + "where a.idOrganizacion = :orgId and a.correlationId = :cid "
                        + "order by a.occurredAtUtc asc", AuditLog.class)
                .setParameter("orgId", orgId).setParameter("cid", correlationId).setMaxResults(1)
                .getResultList();

        return rows.stream().findFirst();
    }

    /**
     * Indica si ya existe un evento auditado con la misma {@code eventKey} dentro del tenant.
     *
     * @param orgId tenant
     * @param eventKey clave idempotente
     * @return true si existe
     */
    public boolean existsByEventKey(UUID orgId, String eventKey) {
        Long n = em.createQuery(
                "select count(a) from AuditLog a where a.idOrganizacion = :orgId and a.eventKey = :ek",
                Long.class).setParameter("orgId", orgId).setParameter("ek", eventKey)
                .getSingleResult();
        return n != null && n > 0;
    }

}
