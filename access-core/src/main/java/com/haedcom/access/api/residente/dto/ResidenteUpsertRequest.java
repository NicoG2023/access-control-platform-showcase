package com.haedcom.access.api.residente.dto;

import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResidenteUpsertRequest(@NotBlank @Size(max = 200) String nombre,
        @NotNull TipoDocumentoIdentidad tipoDocumento,
        @NotBlank @Size(max = 30) String numeroDocumento, @Size(max = 200) String correo,
        @Size(max = 30) String telefono, String domicilio) {
}
