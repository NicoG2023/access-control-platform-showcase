package com.haedcom.access.api.visitante.dto;

import java.util.UUID;

import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request de creación/actualización (upsert) para
 * {@code VisitantePreautorizado}.
 *
 * <p>
 * Notas:
 * <ul>
 * <li>{@code idResidente} es opcional: permite asociar el visitante a un
 * residente (anfitrión).</li>
 * <li>Las validaciones de formato/longitud se aplican aquí; reglas de unicidad
 * se validan en el service.</li>
 * </ul>
 * </p>
 *
 * @param idResidente     id del residente asociado (opcional)
 * @param nombre          nombre completo del visitante
 * @param tipoDocumento   tipo de documento del visitante
 * @param numeroDocumento número de documento del visitante
 * @param correo          correo del visitante (opcional)
 * @param telefono        teléfono del visitante (opcional)
 */
public record VisitantePreautorizadoUpsertRequest(
        UUID idResidente,
        @NotBlank @Size(max = 200) String nombre,
        @NotNull TipoDocumentoIdentidad tipoDocumento,
        @NotBlank @Size(max = 30) String numeroDocumento,
        @Size(max = 200) String correo,
        @Size(max = 30) String telefono) {
}
