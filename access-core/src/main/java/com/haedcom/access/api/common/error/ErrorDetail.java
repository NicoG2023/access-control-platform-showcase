package com.haedcom.access.api.common.error;

/**
 * Detalle específico de un error.
 *
 * <p>
 * Usado principalmente para validaciones de campos.
 * </p>
 */
public record ErrorDetail(String field, String message) {
}
