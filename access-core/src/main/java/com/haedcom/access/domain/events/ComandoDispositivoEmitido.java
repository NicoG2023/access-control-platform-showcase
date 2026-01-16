package com.haedcom.access.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoComandoDispositivo;
import com.haedcom.access.domain.enums.TipoComandoDispositivo;

/**
 * Evento: se emitió un comando hacia un dispositivo.
 *
 * <p>
 * Se emite después de persistir {@code ComandoDispositivo}. El microservicio de dispositivos
 * típicamente consume este evento para ejecutar el comando físico.
 * </p>
 */
public record ComandoDispositivoEmitido(UUID orgId, UUID idComando, UUID idIntento,
        UUID idDispositivo, TipoComandoDispositivo comando, String mensaje,
        EstadoComandoDispositivo estado, String claveIdempotencia, OffsetDateTime enviadoEnUtc) {
}
