package com.haedcom.access.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de dominio que indica que la política de acceso (reglas) cambió para un área del tenant.
 *
 * <p>
 * Se emite cuando se crea, actualiza o cambia el estado de una {@code ReglaAcceso}. El uso
 * principal es invalidar caches distribuidos (vía Kafka) en todos los nodos del cluster.
 * </p>
 *
 * <h2>Granularidad recomendada</h2>
 * <ul>
 * <li>Invalidación por {@code (orgId, areaId)} — suficiente y segura (por wildcards).</li>
 * <li>Opcionalmente, el consumidor podría invalidar también "todo el org" si no cacheas por
 * área.</li>
 * </ul>
 *
 * <h2>Contrato Outbox</h2>
 * <p>
 * Debe exponer {@code orgId()} (requerido por tu {@code OutboxDomainEventPublisher}) y
 * {@code idRegla()} (para trazabilidad: aggregateType=ReglaAcceso).
 * </p>
 */
public record ReglaAccesoPolicyChanged(UUID eventId, UUID orgId, UUID areaId, UUID idRegla,
        ChangeType changeType, OffsetDateTime occurredAtUtc) {

    public enum ChangeType {
        CREATED, UPDATED, ACTIVATED, INACTIVATED, SOFT_DELETED
    }

    public ReglaAccesoPolicyChanged {
        if (eventId == null)
            throw new IllegalArgumentException("eventId es obligatorio");
        if (orgId == null)
            throw new IllegalArgumentException("orgId es obligatorio");
        if (areaId == null)
            throw new IllegalArgumentException("areaId es obligatorio");
        if (idRegla == null)
            throw new IllegalArgumentException("idRegla es obligatorio");
        if (changeType == null)
            throw new IllegalArgumentException("changeType es obligatorio");
        if (occurredAtUtc == null)
            throw new IllegalArgumentException("occurredAtUtc es obligatorio");
    }

    /** Fábrica para consistencia. */
    public static ReglaAccesoPolicyChanged of(UUID orgId, UUID areaId, UUID idRegla,
            ChangeType changeType, OffsetDateTime nowUtc) {
        return new ReglaAccesoPolicyChanged(UUID.randomUUID(), orgId, areaId, idRegla, changeType,
                nowUtc);
    }
}
