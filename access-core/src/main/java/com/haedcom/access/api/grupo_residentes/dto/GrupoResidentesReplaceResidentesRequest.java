package com.haedcom.access.api.grupo_residentes.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Request para reemplazar completamente los miembros del grupo.
 *
 * <p>
 * Si {@code residentesId} es null, se interpreta como conjunto vacío.
 * </p>
 */
public record GrupoResidentesReplaceResidentesRequest(Set<UUID> residentesId) {
}
