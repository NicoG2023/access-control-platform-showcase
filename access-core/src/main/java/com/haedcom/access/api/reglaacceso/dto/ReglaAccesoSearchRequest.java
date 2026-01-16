package com.haedcom.access.api.reglaacceso.dto;

import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoReglaAcceso;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;

public record ReglaAccesoSearchRequest(UUID idArea, UUID idDispositivo, TipoSujetoAcceso tipoSujeto,
        EstadoReglaAcceso estado) {
}
