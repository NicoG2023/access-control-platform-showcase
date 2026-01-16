package com.haedcom.access.api.grupo_visitantes.dto;

import com.haedcom.access.domain.enums.EstadoGrupo;
import jakarta.validation.constraints.NotNull;

/**
 * Request explícito para actualizar el estado de un grupo.
 *
 * <p>
 * Se separa del upsert para evitar cambios accidentales de estado.
 * </p>
 */
public record GrupoVisitantesEstadoRequest(@NotNull EstadoGrupo estado) {
}
