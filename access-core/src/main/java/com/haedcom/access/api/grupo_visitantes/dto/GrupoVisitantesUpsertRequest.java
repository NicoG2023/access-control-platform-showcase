package com.haedcom.access.api.grupo_visitantes.dto;

import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request para crear/actualizar un {@code GrupoVisitantes}.
 *
 * <p>
 * Contiene los campos "editables" del grupo. La membresía ({@code visitantesId}) es opcional aquí:
 * <ul>
 * <li>Si se envía, el service puede usarlo para un replace (dependiendo del endpoint).</li>
 * <li>Si no se envía, la membresía no se modifica en un update.</li>
 * </ul>
 * </p>
 */
public record GrupoVisitantesUpsertRequest(@NotBlank @Size(max = 100) String nombre,
        Set<UUID> visitantesId) {
}
