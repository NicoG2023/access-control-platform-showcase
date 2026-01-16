package com.haedcom.access.infrastructure.messaging;

import java.time.OffsetDateTime;

/**
 * Mensaje enviado a DLQ cuando el consumer de auditoría falla.
 *
 * <p>
 * Guarda el payload original + metadata mínima de error para diagnóstico.
 * </p>
 */
public record AuditDlqMessage(OffsetDateTime failedAtUtc, String errorClass, String errorMessage,
        String originalPayload) {
    public static AuditDlqMessage from(Exception ex, String originalPayload) {
        String msg = ex.getMessage();
        if (msg != null && msg.length() > 600)
            msg = msg.substring(0, 600);
        return new AuditDlqMessage(OffsetDateTime.now(java.time.ZoneOffset.UTC),
                ex.getClass().getName(), msg, originalPayload);
    }
}
