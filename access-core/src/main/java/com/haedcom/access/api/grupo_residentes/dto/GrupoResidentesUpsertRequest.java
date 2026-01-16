package com.haedcom.access.api.grupo_residentes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request para crear/actualizar un grupo de residentes.
 */
public record GrupoResidentesUpsertRequest(@NotBlank(message = "nombre es obligatorio") @Size(
        max = 100, message = "nombre máximo 100 caracteres") String nombre) {
}
