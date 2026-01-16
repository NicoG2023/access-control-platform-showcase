package com.haedcom.access.api.area.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de respuesta para la entidad Area.
 *
 * <p>
 * Representa un área registrada dentro de una organización (tenant). Este DTO es inmutable y se usa
 * exclusivamente para respuestas hacia el cliente.
 * </p>
 *
 * @param idArea identificador único del área
 * @param idOrganizacion identificador de la organización (tenant)
 * @param nombre nombre del área
 * @param rutaImagenArea ruta o URL de la imagen asociada al área (opcional)
 * @param creadoEnUtc fecha de creación en UTC
 * @param actualizadoEnUtc fecha de última actualización en UTC
 */
public record AreaResponse(UUID idArea, UUID idOrganizacion, String nombre, String rutaImagenArea,
        OffsetDateTime creadoEnUtc, OffsetDateTime actualizadoEnUtc) {
}
