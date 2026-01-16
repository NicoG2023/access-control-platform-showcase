package com.haedcom.access.api.reglaacceso.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.TipoAccionAcceso;
import com.haedcom.access.domain.enums.TipoDireccionPaso;
import com.haedcom.access.domain.enums.TipoMetodoAutenticacion;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReglaAccesoUpsertRequest(@NotNull UUID idArea, UUID idDispositivo,

        @NotNull TipoSujetoAcceso tipoSujeto, TipoDireccionPaso direccionPaso,
        TipoMetodoAutenticacion metodoAutenticacion,

        @NotNull TipoAccionAcceso accion,

        OffsetDateTime validoDesdeUtc, OffsetDateTime validoHastaUtc,

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                message = "desdeHoraLocal debe tener formato HH:mm") String desdeHoraLocal,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                message = "hastaHoraLocal debe tener formato HH:mm") String hastaHoraLocal,

        Integer prioridad,

        @Size(max = 250) String mensaje) {
}
