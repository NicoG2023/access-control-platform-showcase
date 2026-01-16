package com.haedcom.access.api.grupo_visitantes.dto;

import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

/**
 * Request para operaciones sobre la membresía del grupo (visitantes).
 *
 * <p>
 * Se usa en endpoints tipo:
 * <ul>
 * <li>POST /{grupoId}/visitantes (add)</li>
 * <li>DELETE /{grupoId}/visitantes (remove)</li>
 * <li>PUT /{grupoId}/visitantes (replace)</li>
 * </ul>
 * </p>
 */
public record GrupoVisitantesMiembrosRequest(@NotNull Set<UUID> visitantesId) {
}
