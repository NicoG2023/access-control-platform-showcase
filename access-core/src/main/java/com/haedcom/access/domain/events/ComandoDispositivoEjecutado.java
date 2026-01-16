package com.haedcom.access.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoComandoDispositivo;

/**
 * Evento: un comando fue ejecutado (o falló) en el dispositivo físico.
 *
 * <p>
 * Este evento representa el "retorno" del microservicio de dispositivos hacia el core. Se emite
 * cuando el microservicio ya intentó ejecutar el comando (abrir puerta, negar con señal, mostrar
 * mensaje, etc.) y tiene un resultado definitivo.
 * </p>
 *
 * <h2>Uso en el core</h2>
 * <ul>
 * <li>Actualizar el {@code estado} del {@code ComandoDispositivo} (CONFIRMADO/FALLIDO)</li>
 * <li>Registrar {@code confirmadoEnUtc}</li>
 * <li>Persistir código/detalle de error cuando aplique</li>
 * <li>Auditar el outcome (opcional)</li>
 * </ul>
 *
 * <h2>Idempotencia</h2>
 * <p>
 * Se recomienda que el consumer del core sea idempotente por {@code (orgId, idComando)}. Un mismo
 * comando puede reportarse dos veces por reintentos de red: el update debe ser seguro.
 * </p>
 *
 * @param orgId tenant
 * @param idComando id del comando emitido por el core
 * @param idIntento id del intento asociado (útil para trazabilidad)
 * @param idDispositivo id del dispositivo
 * @param estadoFinal estado final del comando (típicamente CONFIRMADO o FALLIDO)
 * @param ejecutadoEnUtc timestamp UTC de ejecución/resultado
 * @param codigoError código de error (opcional; solo si FALLIDO)
 * @param detalleError detalle de error (opcional; solo si FALLIDO)
 * @param idEjecucionExterna id/correlación del microservicio o dispositivo (opcional)
 */
public record ComandoDispositivoEjecutado(UUID eventId, UUID orgId, UUID idComando, UUID idIntento,
        UUID idDispositivo, EstadoComandoDispositivo estadoFinal, OffsetDateTime ejecutadoEnUtc,
        String codigoError, String detalleError, String idEjecucionExterna) {
}
