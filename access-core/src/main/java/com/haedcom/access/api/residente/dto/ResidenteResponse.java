package com.haedcom.access.api.residente.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoResidente;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;

public record ResidenteResponse(UUID idResidente, UUID idOrganizacion, String nombre,
                TipoDocumentoIdentidad tipoDocumento, String numeroDocumento, String correo,
                String telefono, String domicilio, EstadoResidente estado,
                OffsetDateTime creadoEnUtc, OffsetDateTime actualizadoEnUtc) {
}
