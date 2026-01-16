package com.haedcom.access.api.grupo_residentes.dto;

import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoResidente;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;

/**
 * DTO resumido de {@code Residente} para respuestas anidadas (por ejemplo, dentro de un grupo).
 */
public record ResidenteResumenResponse(UUID idResidente, String nombre,
        TipoDocumentoIdentidad tipoDocumento, String numeroDocumento, EstadoResidente estado) {
}
