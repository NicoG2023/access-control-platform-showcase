package com.haedcom.access.api.reglaacceso.dto;

import com.haedcom.access.domain.enums.EstadoReglaAcceso;
import jakarta.validation.constraints.NotNull;

public record ReglaAccesoEstadoRequest(@NotNull EstadoReglaAcceso estado) {
}
