package com.haedcom.access.api.dispositivo.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de respuesta para {@code Dispositivo}.
 *
 * <p>
 * Expone referencias como UUID (por ejemplo {@code idArea}) para evitar forzar la carga LAZY de
 * relaciones al serializar.
 * </p>
 *
 * @param idDispositivo identificador del dispositivo
 * @param idOrganizacion identificador del tenant (organización)
 * @param idArea identificador del área a la que pertenece el dispositivo
 * @param nombre nombre del dispositivo
 * @param modelo modelo del dispositivo (opcional)
 * @param identificadorExterno identificador externo global (opcional, pero único si existe)
 * @param estadoActivo indica si el dispositivo está activo
 * @param creadoEnUtc fecha/hora de creación (UTC)
 * @param actualizadoEnUtc fecha/hora de última actualización (UTC)
 */
public record DispositivoResponse(UUID idDispositivo, UUID idOrganizacion, UUID idArea,
        String nombre, String modelo, String identificadorExterno, boolean estadoActivo,
        OffsetDateTime creadoEnUtc, OffsetDateTime actualizadoEnUtc) {
}
