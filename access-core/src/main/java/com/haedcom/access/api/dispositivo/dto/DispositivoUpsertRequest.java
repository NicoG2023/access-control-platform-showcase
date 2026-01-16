package com.haedcom.access.api.dispositivo.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request de creación/actualización (upsert) para {@code Dispositivo}.
 *
 * <p>
 * Notas:
 * <ul>
 * <li>{@code idArea} es obligatorio: un dispositivo siempre pertenece a un área.</li>
 * <li>{@code identificadorExterno} es opcional, pero si se envía debe ser único (global).</li>
 * <li>{@code estadoActivo} es obligatorio para evitar estados implícitos.</li>
 * </ul>
 * </p>
 *
 * @param idArea identificador del área del dispositivo (obligatorio)
 * @param nombre nombre del dispositivo (obligatorio)
 * @param modelo modelo del dispositivo (opcional)
 * @param identificadorExterno identificador externo (opcional, único)
 * @param estadoActivo estado de activación del dispositivo (obligatorio)
 */
public record DispositivoUpsertRequest(@NotNull UUID idArea,
        @NotBlank @Size(max = 100) String nombre, @Size(max = 50) String modelo,
        @Size(max = 120) String identificadorExterno, @NotNull Boolean estadoActivo) {
}
