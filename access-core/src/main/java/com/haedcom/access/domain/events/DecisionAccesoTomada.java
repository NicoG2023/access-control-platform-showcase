package com.haedcom.access.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.TipoResultadoDecision;

/**
 * Evento: se tomó una decisión de acceso.
 *
 * <p>
 * Se emite después de persistir {@code DecisionAcceso}.
 * </p>
 */
public record DecisionAccesoTomada(UUID orgId, UUID idDecision, UUID idIntento,
        TipoResultadoDecision resultado, String codigoMotivo, String detalleMotivo,
        OffsetDateTime decididoEnUtc, OffsetDateTime expiraEnUtc) {
}
