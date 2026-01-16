package com.haedcom.access.api.residente.dto;

import com.haedcom.access.domain.enums.EstadoResidente;
import jakarta.validation.constraints.NotNull;

public record ResidenteEstadoRequest(@NotNull EstadoResidente estado) {
}
