package com.haedcom.access.api.grupo_residentes.dto;

import com.haedcom.access.domain.enums.EstadoGrupo;
import jakarta.validation.constraints.NotNull;

/**
 * Request para actualizar únicamente el estado de un grupo.
 */
public record GrupoResidentesEstadoRequest(
        @NotNull(message = "estado es obligatorio") EstadoGrupo estado) {
}
