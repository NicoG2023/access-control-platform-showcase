package com.haedcom.access.api.grupo_visitantes.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoGrupo;

/**
 * DTO de salida para {@code GrupoVisitantes}.
 *
 * <p>
 * Incluye opcionalmente la lista de visitantes (cuando el caso de uso requiere detalle).
 * </p>
 */
public record GrupoVisitantesResponse(UUID idGrupoVisitante, UUID idOrganizacion, String nombre,
        EstadoGrupo estado, List<VisitanteLiteResponse> visitantes, OffsetDateTime creadoEnUtc,
        OffsetDateTime actualizadoEnUtc) {
}
