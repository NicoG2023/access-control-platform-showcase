package com.haedcom.access.application.dispositivo.dto;

import java.time.OffsetDateTime;
import com.haedcom.access.domain.enums.EstadoComandoDispositivo;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request del microservicio de dispositivos hacia el core para reportar el resultado definitivo de
 * un comando.
 *
 * <h2>Idempotencia</h2>
 * <p>
 * Debe ser idempotente por {@code (orgId, idComando)}: el core no debe reprocesar un comando si ya
 * quedó en estado final.
 * </p>
 *
 * <h2>Convenciones</h2>
 * <ul>
 * <li>{@code ocurridoEnUtc} es opcional; si es null, el core usa {@code now(UTC)}.</li>
 * <li>{@code idEjecucionExterna} es opcional; si viene, el core lo persiste para correlación.</li>
 * <li>Si {@code estado = EJECUTADO_ERROR}, se recomienda enviar al menos un diagnóstico
 * ({@code codigoError} o {@code detalleError}).</li>
 * </ul>
 *
 * @param estado estado final del comando (obligatorio)
 * @param codigoError código de error (opcional; recomendado cuando
 *        {@code estado = EJECUTADO_ERROR})
 * @param detalleError detalle del error (opcional; recomendado cuando
 *        {@code estado = EJECUTADO_ERROR})
 * @param ocurridoEnUtc timestamp UTC del resultado (opcional)
 * @param idEjecucionExterna correlación externa (opcional)
 */
public record ResultadoComandoRequest(@NotNull EstadoComandoDispositivo estado,
        @Size(max = 60) String codigoError, @Size(max = 250) String detalleError,
        OffsetDateTime ocurridoEnUtc, @Size(max = 120) String idEjecucionExterna) {

    /**
     * Validación de coherencia mínima del request.
     *
     * <p>
     * Si el comando falló, debe venir algún diagnóstico (código o detalle).
     * </p>
     */
    @AssertTrue(message = "Si estado es EJECUTADO_ERROR, debe venir codigoError o detalleError")
    public boolean isErrorPayloadConsistent() {
        if (estado != EstadoComandoDispositivo.EJECUTADO_ERROR) {
            return true;
        }
        return (codigoError != null && !codigoError.isBlank())
                || (detalleError != null && !detalleError.isBlank());
    }
}
