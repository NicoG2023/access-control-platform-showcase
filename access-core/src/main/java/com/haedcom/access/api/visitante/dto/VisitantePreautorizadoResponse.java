package com.haedcom.access.api.visitante.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;

/**
 * DTO de respuesta para {@code VisitantePreautorizado}.
 *
 * <p>
 * Expone el {@code residenteId} como UUID (opcional) para evitar forzar la
 * carga LAZY de la relación.
 * </p>
 *
 * @param idVisitante      identificador del visitante
 * @param idOrganizacion   identificador del tenant (organización)
 * @param idResidente      identificador del residente asociado (opcional)
 * @param nombre           nombre del visitante
 * @param tipoDocumento    tipo de documento
 * @param numeroDocumento  número de documento
 * @param correo           correo (opcional)
 * @param telefono         teléfono (opcional)
 * @param creadoEnUtc      fecha/hora de creación (UTC)
 * @param actualizadoEnUtc fecha/hora de última actualización (UTC)
 */
public record VisitantePreautorizadoResponse(
        UUID idVisitante,
        UUID idOrganizacion,
        UUID idResidente,
        String nombre,
        TipoDocumentoIdentidad tipoDocumento,
        String numeroDocumento,
        String correo,
        String telefono,
        OffsetDateTime creadoEnUtc,
        OffsetDateTime actualizadoEnUtc) {
}
