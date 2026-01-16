package com.haedcom.access.api.organizacion.dto;

/**
 * DTO de entrada para crear una organización (tenant).
 *
 * <p>
 * Notas:
 * </p>
 * <ul>
 * <li>{@code estado} y {@code timezoneId} se reciben como strings para mantener compatibilidad con
 * el modelo actual ({@code Organizacion.estado} es String).</li>
 * <li>{@code timezoneId} debe ser una zona horaria IANA (ej. {@code "America/Bogota"}).</li>
 * </ul>
 */
public class OrganizacionCreateRequest {

    /** Nombre visible de la organización. */
    public String nombre;

    /** Estado (ej. "ACTIVA", "INACTIVA"). */
    public String estado;

    /** Zona horaria IANA por defecto del tenant (ej. "America/Bogota"). */
    public String timezoneId;

    public OrganizacionCreateRequest() {}
}
