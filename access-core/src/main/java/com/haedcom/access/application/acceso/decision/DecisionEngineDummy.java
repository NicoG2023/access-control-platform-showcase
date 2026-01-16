package com.haedcom.access.application.acceso.decision;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import com.haedcom.access.application.acceso.decision.model.DecisionContext;
import com.haedcom.access.application.acceso.decision.model.DecisionOutput;
import com.haedcom.access.domain.enums.TipoComandoDispositivo;
import com.haedcom.access.domain.model.CatalogoMotivoDecision;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Dummy implementation of {@link DecisionEngine} for public showcase purposes.
 *
 * <p>
 * This implementation intentionally avoids exposing real business rules, heuristics, or
 * prioritization logic.
 * </p>
 *
 * <p>
 * The production version includes rule matching, time windows, priorities, and additional
 * validations that are not part of this repository.
 * </p>
 */
@Named("decision-engine-dummy")
@ApplicationScoped
public class DecisionEngineDummy implements DecisionEngine {

    private final Clock clock;

    public DecisionEngineDummy(Clock clock) {
        this.clock = (clock != null) ? clock : Clock.systemUTC();
    }

    @Override
    public DecisionOutput evaluate(DecisionContext ctx) {
        Objects.requireNonNull(ctx, "ctx is required");

        OffsetDateTime now = OffsetDateTime.now(clock);

        // Minimal defensive validation
        if (ctx.orgId() == null || ctx.idArea() == null || ctx.tipoSujeto() == null) {
            return DecisionOutput.error(now,
                    CatalogoMotivoDecision.MOTIVO_POLICY_ERROR.getCodigoMotivo(),
                    "Incomplete decision context");
        }

        // Deterministic default decision for showcase purposes
        return DecisionOutput.allow(now, CatalogoMotivoDecision.MOTIVO_ALLOW.getCodigoMotivo(),
                "Default allow decision (showcase)", TipoComandoDispositivo.ABRIR_PUERTA, null);
    }
}
