package com.haedcom.access.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de dominio para auditoría funcional (Nivel B) que registra un intento de cambio
 * (create/update/changeEstado/delete) sobre {@code ReglaAcceso} que fue rechazado.
 *
 * <p>
 * Este evento NO reemplaza los logs técnicos. Su propósito es:
 * </p>
 * <ul>
 * <li>Dejar trazabilidad de rechazos relevantes (409 duplicados, 400 validación, 404 not
 * found).</li>
 * <li>Permitir análisis posterior de calidad de datos, comportamiento de admins o
 * integraciones.</li>
 * <li>Soportar cumplimiento (quién intentó qué, cuándo, y por qué fue rechazado).</li>
 * </ul>
 *
 * <h2>Diseño</h2>
 * <ul>
 * <li>Incluye {@code eventId} para deduplicación robusta.</li>
 * <li>{@code reglaId} puede ser null en CREATE si aún no existe.</li>
 * <li>{@code reasonCode} es un código estable (no dependas de textos).</li>
 * <li>{@code httpStatus} permite clasificar rápidamente el tipo de rechazo.</li>
 * </ul>
 */
public record ReglaAccesoChangeRejected(UUID eventId, UUID orgId, UUID areaId, UUID reglaId,
        Operation operation, String reasonCode, int httpStatus, String message,
        OffsetDateTime occurredAtUtc) {

    public enum Operation {
        CREATE, UPDATE, CHANGE_ESTADO, DELETE
    }

    public ReglaAccesoChangeRejected {
        if (eventId == null)
            throw new IllegalArgumentException("eventId es obligatorio");
        if (orgId == null)
            throw new IllegalArgumentException("orgId es obligatorio");
        if (areaId == null)
            throw new IllegalArgumentException("areaId es obligatorio");
        if (operation == null)
            throw new IllegalArgumentException("operation es obligatorio");
        if (reasonCode == null || reasonCode.isBlank())
            throw new IllegalArgumentException("reasonCode es obligatorio");
        if (httpStatus <= 0)
            throw new IllegalArgumentException("httpStatus es obligatorio");
        if (occurredAtUtc == null)
            throw new IllegalArgumentException("occurredAtUtc es obligatorio");
    }

    /**
     * Fábrica para consistencia.
     *
     * @param orgId tenant (obligatorio)
     * @param areaId área (obligatorio)
     * @param reglaId regla (opcional, null en CREATE si aún no existe)
     * @param operation operación que se intentó ejecutar (obligatorio)
     * @param reasonCode código estable del rechazo (obligatorio)
     * @param httpStatus estatus asociado (400/404/409/500...) (obligatorio)
     * @param message mensaje sanitizado (opcional)
     * @param nowUtc instante del rechazo (obligatorio)
     */
    public static ReglaAccesoChangeRejected of(UUID orgId, UUID areaId, UUID reglaId,
            Operation operation, String reasonCode, int httpStatus, String message,
            OffsetDateTime nowUtc) {

        return new ReglaAccesoChangeRejected(UUID.randomUUID(), orgId, areaId, reglaId, operation,
                reasonCode, httpStatus, message, nowUtc);
    }
}
