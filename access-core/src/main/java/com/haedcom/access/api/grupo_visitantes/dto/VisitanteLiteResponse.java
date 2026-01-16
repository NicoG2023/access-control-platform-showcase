package com.haedcom.access.api.grupo_visitantes.dto;

import java.util.UUID;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;

/**
 * DTO liviano para representar un visitante dentro de la respuesta del grupo.
 *
 * <p>
 * Se evita devolver toda la entidad (y sus relaciones LAZY) para mantener el contrato estable.
 * </p>
 */
public record VisitanteLiteResponse(UUID idVisitante, String nombre,
        TipoDocumentoIdentidad tipoDocumento, String numeroDocumento) {
}
