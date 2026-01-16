package com.haedcom.access.api.common.error;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Respuesta estándar de error para toda la API.
 *
 * <p>
 * Diseñada para:
 * <ul>
 * <li>Errores simples (404, 409, 500)</li>
 * <li>Errores con múltiples detalles (validaciones)</li>
 * <li>Extensión futura (traceId, correlationId, etc.)</li>
 * </ul>
 * </p>
 */
public record ErrorResponse(String code, String message, int status, String path,
        OffsetDateTime timestamp, List<ErrorDetail> details) {

    public static ErrorResponse simple(String code, String message, int status, String path) {
        return new ErrorResponse(code, message, status, path, OffsetDateTime.now(), null);
    }

    public static ErrorResponse withDetails(String code, String message, int status, String path,
            List<ErrorDetail> details) {
        return new ErrorResponse(code, message, status, path, OffsetDateTime.now(), details);
    }
}
