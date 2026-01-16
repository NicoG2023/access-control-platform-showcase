package com.haedcom.access.api.organizacion.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de salida de organización.
 *
 * <p>
 * Incluye metadata de auditoría en UTC proveniente de {@code AuditableEntity}.
 * </p>
 */
public class OrganizacionResponse {

    /** Identificador de la organización (tenant). */
    public UUID idOrganizacion;

    /** Nombre visible de la organización. */
    public String nombre;

    /** Estado actual de la organización. */
    public String estado;

    /** Zona horaria IANA por defecto del tenant. */
    public String timezoneId;

    /** Timestamp de creación en UTC. */
    public OffsetDateTime creadoEnUtc;

    /** Timestamp de última actualización en UTC. */
    public OffsetDateTime actualizadoEnUtc;

    public OrganizacionResponse() {}
}
