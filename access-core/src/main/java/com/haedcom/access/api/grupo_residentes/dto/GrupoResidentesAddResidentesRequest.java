package com.haedcom.access.api.grupo_residentes.dto;

import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;

/**
 * Request para agregar miembros al grupo.
 */
public record GrupoResidentesAddResidentesRequest(
        @NotEmpty(message = "residentesId no puede ser vacío") Set<UUID> residentesId) {
}
