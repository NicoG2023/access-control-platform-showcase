package com.haedcom.access.api.common.error;

import java.time.OffsetDateTime;

/**
 * Payload estándar para respuestas de error en la API.
 */
public record ApiError(String code, String message, int status, OffsetDateTime timestamp,
        String path) {
    public static ApiError of(String code, String message, int status, String path) {
        return new ApiError(code, message, status, OffsetDateTime.now(), path);
    }
}
