package com.haedcom.access.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento: un comando fue confirmado exitosamente por el microservicio/dispositivo.
 *
 * @param orgId tenant
 * @param idComando id del comando
 * @param idIntento id del intento
 * @param idDispositivo id del dispositivo
 * @param confirmadoEnUtc timestamp UTC del resultado
 * @param idEjecucionExterna correlación externa (opcional)
 */
public record ComandoDispositivoConfirmado(UUID orgId, UUID idComando, UUID idIntento,
        UUID idDispositivo, OffsetDateTime confirmadoEnUtc, String idEjecucionExterna) {
}
