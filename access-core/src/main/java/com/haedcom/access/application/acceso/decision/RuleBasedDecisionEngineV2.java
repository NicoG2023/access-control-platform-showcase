package com.haedcom.access.application.acceso.decision;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import com.haedcom.access.application.acceso.decision.model.DecisionContext;
import com.haedcom.access.application.acceso.decision.model.DecisionOutput;
import com.haedcom.access.domain.enums.TipoComandoDispositivo;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Motor de decisiones de acceso (Rule-based v2).
 *
 * <p>
 * Implementación pura del motor: no depende de entidades JPA ni infraestructura. Consume
 * {@link DecisionContext} y produce {@link DecisionOutput}.
 * </p>
 *
 * <h2>Reglas base</h2>
 * <ul>
 * <li>Si falta info crítica -> ERROR (POLICY_ERROR)</li>
 * <li>Si el dispositivo está inactivo -> DENEGAR (DEVICE_INACTIVE)</li>
 * <li>Si el sujeto es DESCONOCIDO -> DENEGAR (SUBJECT_UNKNOWN)</li>
 * <li>Si el sujeto está resuelto -> PERMITIR (ALLOW)</li>
 * </ul>
 */
@Named("decision-engine-v2")
@ApplicationScoped
public class RuleBasedDecisionEngineV2 implements DecisionEngine {

    /** Motivo cuando el dispositivo está inactivo. */
    public static final String MOTIVO_DEVICE_INACTIVE = "DEVICE_INACTIVE";
    /** Motivo cuando no se pudo resolver el sujeto. */
    public static final String MOTIVO_SUBJECT_UNKNOWN = "SUBJECT_UNKNOWN";
    /** Motivo para errores de política / integración (datos incompletos). */
    public static final String MOTIVO_POLICY_ERROR = "POLICY_ERROR";
    /** Motivo por defecto para permisos en reglas base. */
    public static final String MOTIVO_ALLOW_DEFAULT = "ALLOW";

    private final Clock clock;

    /**
     * Constructor por defecto usando UTC.
     */
    public RuleBasedDecisionEngineV2() {
        this(Clock.systemUTC());
    }

    /**
     * Constructor con {@link Clock} inyectable para testabilidad.
     *
     * @param clock reloj (no null)
     */
    public RuleBasedDecisionEngineV2(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock es obligatorio");
    }

    @Override
    public DecisionOutput evaluate(DecisionContext ctx) {
        Objects.requireNonNull(ctx, "ctx es obligatorio");

        OffsetDateTime now = OffsetDateTime.now(clock);

        // Validaciones defensivas: si faltan datos críticos, ERROR
        if (ctx.orgId() == null || ctx.idIntento() == null || ctx.device() == null) {
            return DecisionOutput.error(now, MOTIVO_POLICY_ERROR, "Contexto incompleto");
        }
        if (ctx.idArea() == null || ctx.direccionPaso() == null
                || ctx.metodoAutenticacion() == null) {
            return DecisionOutput.error(now, MOTIVO_POLICY_ERROR, "Datos del intento incompletos");
        }
        if (ctx.device().idDispositivo() == null || ctx.device().idOrganizacion() == null
                || ctx.device().idArea() == null) {
            return DecisionOutput.error(now, MOTIVO_POLICY_ERROR,
                    "Snapshot de dispositivo incompleto");
        }

        // 1) Dispositivo inactivo
        if (!ctx.device().estadoActivo()) {
            return DecisionOutput.deny(now, MOTIVO_DEVICE_INACTIVE, "Dispositivo inactivo",
                    TipoComandoDispositivo.NEGAR_CON_SEÑAL, "Acceso denegado");
        }

        // 2) Sujeto desconocido
        if (ctx.tipoSujeto() == null || ctx.tipoSujeto() == TipoSujetoAcceso.DESCONOCIDO) {
            return DecisionOutput.deny(now, MOTIVO_SUBJECT_UNKNOWN, "Sujeto no reconocido",
                    TipoComandoDispositivo.NEGAR_CON_SEÑAL, "Acceso denegado");
        }

        // 3) Permitir por defecto cuando el sujeto ya está resuelto
        return DecisionOutput.allow(now, MOTIVO_ALLOW_DEFAULT, null,
                TipoComandoDispositivo.ABRIR_PUERTA, null);
    }
}
