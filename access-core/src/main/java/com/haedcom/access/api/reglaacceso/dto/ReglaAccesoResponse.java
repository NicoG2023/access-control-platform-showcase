package com.haedcom.access.api.reglaacceso.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoReglaAcceso;
import com.haedcom.access.domain.enums.TipoAccionAcceso;
import com.haedcom.access.domain.enums.TipoDireccionPaso;
import com.haedcom.access.domain.enums.TipoMetodoAutenticacion;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;

public record ReglaAccesoResponse(UUID idRegla, UUID idOrganizacion,

                UUID idArea, UUID idDispositivo,

                TipoSujetoAcceso tipoSujeto, TipoDireccionPaso direccionPaso,
                TipoMetodoAutenticacion metodoAutenticacion,

                TipoAccionAcceso accion,

                OffsetDateTime validoDesdeUtc, OffsetDateTime validoHastaUtc,

                String desdeHoraLocal, String hastaHoraLocal,

                Integer prioridad, EstadoReglaAcceso estado,

                String mensaje,

                OffsetDateTime creadoEnUtc, OffsetDateTime actualizadoEnUtc) {
}
