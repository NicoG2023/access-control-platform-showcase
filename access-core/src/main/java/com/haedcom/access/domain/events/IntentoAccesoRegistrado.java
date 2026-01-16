package com.haedcom.access.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.TipoDireccionPaso;
import com.haedcom.access.domain.enums.TipoMetodoAutenticacion;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;

/**
 * Evento: se registró un intento de acceso.
 *
 * <p>
 * Se emite después de persistir {@code IntentoAcceso}.
 * </p>
 */
public record IntentoAccesoRegistrado(UUID orgId, UUID idIntento, UUID idDispositivo, UUID idArea,
        TipoDireccionPaso direccionPaso, TipoMetodoAutenticacion metodoAutenticacion,
        TipoSujetoAcceso tipoSujeto, String claveIdempotencia, OffsetDateTime ocurridoEnUtc) {
}
