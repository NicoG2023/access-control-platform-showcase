package com.haedcom.access.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento: un comando falló en el microservicio/dispositivo.
 *
 * @param orgId tenant
 * @param idComando id del comando
 * @param idIntento id del intento
 * @param idDispositivo id del dispositivo
 * @param fallidoEnUtc timestamp UTC del resultado
 * @param codigoError código de error (opcional)
 * @param detalleError detalle de error (opcional)
 * @param idEjecucionExterna correlación externa (opcional)
 */
public record ComandoDispositivoFallido(UUID orgId, UUID idComando, UUID idIntento,
        UUID idDispositivo, OffsetDateTime fallidoEnUtc, String codigoError, String detalleError,
        String idEjecucionExterna) {
}
