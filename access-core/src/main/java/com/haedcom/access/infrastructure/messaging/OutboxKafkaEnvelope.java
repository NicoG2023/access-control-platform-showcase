package com.haedcom.access.infrastructure.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.model.OutboxEvent;

/**
 * Envelope estándar publicado en Kafka para el patrón Transactional Outbox.
 *
 * <p>
 * Este envelope preserva trazabilidad end-to-end: permite correlacionar lo publicado en Kafka con
 * la fila en {@code outbox_event} y con el evento de dominio original.
 * </p>
 *
 * <h2>Contenido</h2>
 * <ul>
 * <li>{@code idEvento}: id único del outbox (correlación)</li>
 * <li>{@code orgId}: tenant ({@link OutboxEvent#getIdOrganizacion()})</li>
 * <li>{@code eventType}: tipo del evento (simple class name)</li>
 * <li>{@code aggregateType}/{@code aggregateId}: trazabilidad por agregado</li>
 * <li>{@code createdAtUtc}: creación del evento en outbox</li>
 * <li>{@code attempts}: número de intentos ya ejecutados</li>
 * <li>{@code payload}: JSON del evento de dominio (string)</li>
 * </ul>
 */
public record OutboxKafkaEnvelope(UUID idEvento, UUID orgId, String eventType, String aggregateType,
        String aggregateId, OffsetDateTime createdAtUtc, int attempts, String payload) {
    /**
     * Crea un envelope a partir de {@link OutboxEvent}.
     *
     * @param e entidad outbox (no null)
     * @return envelope serializable para Kafka
     */
    public static OutboxKafkaEnvelope from(OutboxEvent e) {
        return new OutboxKafkaEnvelope(e.getIdEvento(), e.getIdOrganizacion(), e.getEventType(),
                e.getAggregateType(), e.getAggregateId(), e.getCreatedAtUtc(), e.getAttempts(),
                e.getPayload());
    }
}
