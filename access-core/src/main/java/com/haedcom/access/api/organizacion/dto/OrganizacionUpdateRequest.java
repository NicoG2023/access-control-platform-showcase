package com.haedcom.access.api.organizacion.dto;

/**
 * DTO de entrada para actualizar una organización existente.
 *
 * <p>
 * Modelo de actualización "total" (PUT-like): si un campo viene {@code null}, el service lo
 * interpreta como "no actualizar ese campo".
 * </p>
 *
 * <p>
 * Si prefieres una actualización estricta (todos obligatorios), se puede ajustar para exigir
 * no-null en todos los campos.
 * </p>
 */
public class OrganizacionUpdateRequest {

    /** Nuevo nombre de la organización (opcional). */
    public String nombre;

    /** Nuevo estado (opcional). */
    public String estado;

    /** Nueva zona horaria IANA (opcional). */
    public String timezoneId;

    public OrganizacionUpdateRequest() {}
}
