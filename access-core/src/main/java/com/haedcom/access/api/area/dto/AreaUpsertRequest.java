package com.haedcom.access.api.area.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para creación y actualización de áreas.
 *
 * <p>
 * Se utiliza tanto en operaciones de creación (POST) como de actualización (PUT). Las validaciones
 * aquí definidas se ejecutan antes de que el request llegue a la capa de servicio.
 * </p>
 */
public record AreaUpsertRequest(

        /**
         * Nombre del área.
         *
         * <p>
         * Debe ser único dentro de la organización.
         * </p>
         */
        @NotBlank(message = "El nombre del área es obligatorio") @Size(max = 60,
                message = "El nombre del área no puede exceder 60 caracteres") String nombre,

        /**
         * Ruta o URL de la imagen asociada al área (opcional).
         */
        @Size(max = 255,
                message = "La ruta de la imagen no puede exceder 255 caracteres") String rutaImagenArea) {
}
