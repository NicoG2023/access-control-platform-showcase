package com.haedcom.access.api.grupo_residentes.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoGrupo;

/**
 * DTO de salida para {@code GrupoResidentes}.
 *
 * <p>
 * Incluye opcionalmente la lista de residentes (versión resumida) cuando el endpoint lo requiera.
 * </p>
 */
public record GrupoResidentesResponse(UUID idGrupoResidente, UUID idOrganizacion, String nombre,
        EstadoGrupo estado, List<ResidenteResumenResponse> residentes, OffsetDateTime creadoEnUtc,
        OffsetDateTime actualizadoEnUtc) {
}
