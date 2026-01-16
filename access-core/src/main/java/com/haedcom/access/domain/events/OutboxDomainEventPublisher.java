package com.haedcom.access.domain.events;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haedcom.access.domain.enums.OutboxStatus;
import com.haedcom.access.domain.model.OutboxEvent;
import com.haedcom.access.domain.repo.OutboxEventRepository;
import com.haedcom.access.infrastructure.events.Outbox;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Implementación de {@link DomainEventPublisher} basada en <b>Transactional Outbox</b>.
 *
 * <p>
 * En lugar de publicar directamente en un bus (Kafka/HTTP/etc.), este publicador <b>persiste</b> el
 * evento en la tabla {@code outbox_event} dentro de la <b>misma transacción</b> del caso de uso.
 * </p>
 *
 * <h2>Beneficio principal</h2>
 * <p>
 * Garantiza consistencia entre cambios en base de datos y mensajes de integración: si la
 * transacción hace commit, el evento queda almacenado y será despachado eventualmente. Si la
 * transacción hace rollback, el evento NO se publica.
 * </p>
 *
 * <h2>Despacho</h2>
 * <p>
 * Un componente separado (p.ej. {@code OutboxDispatcher}) consume eventos
 * {@link OutboxStatus#PENDING}, intenta entregarlos y actualiza el estado a
 * {@link OutboxStatus#PUBLISHED} o {@link OutboxStatus#FAILED}.
 * </p>
 *
 * <h2>Contrato mínimo del evento</h2>
 * <ul>
 * <li><b>Obligatorio:</b> el evento debe exponer un método público {@code orgId()} (tipo UUID) para
 * resolver el tenant.</li>
 * <li><b>Recomendado:</b> exponer un id del agregado para trazabilidad: {@code idComando()},
 * {@code idIntento()} o {@code idDecision()}.</li>
 * </ul>
 *
 * <h2>Trazabilidad (aggregateType / aggregateId)</h2>
 * <p>
 * Para observabilidad y debugging, el outbox guarda:
 * </p>
 * <ul>
 * <li>{@code eventType}: nombre del evento (simple class name)</li>
 * <li>{@code aggregateType}: nombre lógico del agregado (heurística)</li>
 * <li>{@code aggregateId}: identificador del agregado (heurística)</li>
 * </ul>
 *
 * <p>
 * Heurística actual:
 * </p>
 * <ul>
 * <li>Si existe {@code idComando()} → {@code aggregateType=ComandoDispositivo}</li>
 * <li>Si existe {@code idIntento()} → {@code aggregateType=IntentoAcceso}</li>
 * <li>Si existe {@code idDecision()} → {@code aggregateType=DecisionAcceso}</li>
 * <li>Si no se encuentra ninguno → {@code aggregateType=UNKNOWN}, {@code aggregateId=UNKNOWN}</li>
 * </ul>
 *
 * <p>
 * Nota: esta versión usa reflexión para extraer {@code orgId()} y el id del agregado. Si luego
 * quieres endurecer el contrato, podemos migrar a una interfaz {@code DomainEvent} con métodos
 * explícitos (sin reflexión).
 * </p>
 */
@ApplicationScoped
@Outbox
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private static final String AGG_UNKNOWN = "UNKNOWN";

    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Constructor.
     *
     * @param outboxRepo repositorio de outbox (obligatorio)
     * @param objectMapper serializador JSON (obligatorio)
     * @param clock reloj inyectable para testabilidad (si es null, usa UTC)
     */
    public OutboxDomainEventPublisher(OutboxEventRepository outboxRepo, ObjectMapper objectMapper,
            Clock clock) {
        this.outboxRepo = Objects.requireNonNull(outboxRepo, "outboxRepo es obligatorio");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper es obligatorio");
        this.clock = (clock != null) ? clock : Clock.systemUTC();
    }

    /**
     * Persiste un evento de dominio en el outbox.
     *
     * <p>
     * Este método debe ejecutarse dentro de la transacción del caso de uso para asegurar
     * atomicidad. Si falla la serialización o la persistencia, se lanza excepción para <b>forzar
     * rollback</b>.
     * </p>
     *
     * <p>
     * Campos clave que se almacenan:
     * </p>
     * <ul>
     * <li>{@code idEvento}: UUID aleatorio</li>
     * <li>{@code idOrganizacion}: extraído de {@code orgId()}</li>
     * <li>{@code eventType}: nombre del evento</li>
     * <li>{@code aggregateType}/{@code aggregateId}: heurística de trazabilidad</li>
     * <li>{@code payload}: JSON del evento</li>
     * <li>{@code status}: {@link OutboxStatus#PENDING}</li>
     * <li>{@code createdAtUtc}: timestamp UTC actual</li>
     * </ul>
     *
     * @param event evento de dominio (no null)
     * @throws IllegalArgumentException si el evento no expone {@code orgId()}
     * @throws IllegalStateException si no es posible serializar/persistir el evento en el outbox
     */
    @Override
    @Transactional
    public void publish(Object event) {
        Objects.requireNonNull(event, "event es obligatorio");

        try {
            OutboxEvent e = new OutboxEvent();
            e.setIdEvento(UUID.randomUUID());
            e.assignTenant(extractOrgId(event));

            // Tipos para diagnóstico
            e.setEventType(event.getClass().getName());
            e.setAggregateType(extractAggregateType(event));
            e.setAggregateId(extractAggregateId(event));

            // Payload
            e.setPayload(objectMapper.writeValueAsString(event));

            // Estado inicial
            e.setStatus(OutboxStatus.PENDING);
            e.setAttempts(0);
            e.setCreatedAtUtc(OffsetDateTime.now(clock));
            e.setNextAttemptAtUtc(null);

            outboxRepo.persist(e);

        } catch (IllegalArgumentException ex) {
            // Contrato roto (evento sin orgId)
            throw ex;
        } catch (Exception ex) {
            // Esto SÍ debe romper la transacción: outbox es parte del contrato de entrega
            // confiable.
            throw new IllegalStateException("No se pudo persistir evento en outbox", ex);
        }
    }

    /**
     * Extrae el tenant ({@code orgId}) desde el evento.
     *
     * <p>
     * Requiere que el evento exponga un método público {@code orgId()} que retorne {@link UUID}.
     * </p>
     *
     * @param event evento
     * @return orgId del evento
     * @throws IllegalArgumentException si el evento no expone {@code orgId()} o no retorna UUID
     */
    private UUID extractOrgId(Object event) {
        try {
            Object v = event.getClass().getMethod("orgId").invoke(event);
            if (v instanceof UUID id) {
                return id;
            }
            throw new IllegalArgumentException(
                    "Evento orgId() no retorna UUID: " + event.getClass());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Evento sin orgId(): " + event.getClass(), e);
        }
    }

    /**
     * Determina el {@code aggregateType} para trazabilidad.
     *
     * <p>
     * Heurística basada en la presencia de métodos comunes de id de agregado. Esto permite
     * clasificar eventos por "familia" (p.ej. ComandoDispositivo) aunque el {@code eventType} sea
     * más específico.
     * </p>
     *
     * @param event evento
     * @return tipo lógico del agregado o {@code UNKNOWN} si no se puede inferir
     */
    private String extractAggregateType(Object event) {
        if (hasMethod(event, "idComando")) {
            return "ComandoDispositivo";
        }
        if (hasMethod(event, "idIntento")) {
            return "IntentoAcceso";
        }
        if (hasMethod(event, "idDecision")) {
            return "DecisionAcceso";
        }
        if (hasMethod(event, "idRegla"))
            return "ReglaAcceso";
        return AGG_UNKNOWN;
    }

    /**
     * Determina el {@code aggregateId} para trazabilidad.
     *
     * <p>
     * Heurística:
     * </p>
     * <ol>
     * <li>Si existe {@code idComando()} lo usa</li>
     * <li>si no, intenta {@code idIntento()}</li>
     * <li>si no, intenta {@code idDecision()}</li>
     * </ol>
     *
     * <p>
     * Si no se encuentra ninguno, retorna {@code UNKNOWN}. Se prefiere un valor estable (en vez de
     * null) para facilitar consultas y evitar violaciones si la columna es NOT NULL.
     * </p>
     *
     * @param event evento
     * @return id del agregado como string o {@code UNKNOWN}
     */
    private String extractAggregateId(Object event) {
        Object id = tryInvoke(event, "idComando");
        if (id == null) {
            id = tryInvoke(event, "idIntento");
        }
        if (id == null) {
            id = tryInvoke(event, "idDecision");
        }
        if (id == null) {
            id = tryInvoke(event, "idRegla");
        }
        return (id != null) ? id.toString() : AGG_UNKNOWN;
    }

    /**
     * Verifica si el evento expone un método público con el nombre indicado (sin parámetros).
     *
     * @param event evento
     * @param name nombre del método
     * @return {@code true} si el método existe
     */
    private static boolean hasMethod(Object event, String name) {
        try {
            event.getClass().getMethod(name);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Invoca de forma segura un método sin parámetros si existe.
     *
     * <p>
     * Si el método no existe o falla la invocación, retorna null. Se usa como heurística de
     * extracción sin romper la publicación.
     * </p>
     *
     * @param event evento
     * @param name nombre del método a invocar
     * @return valor retornado por el método, o null si no está disponible
     */
    private static Object tryInvoke(Object event, String name) {
        try {
            return event.getClass().getMethod(name).invoke(event);
        } catch (Exception ignored) {
            return null;
        }
    }
}
