package com.haedcom.access.application.acceso.decision;

import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jboss.logging.Logger;
import com.haedcom.access.application.acceso.decision.model.DecisionContext;
import com.haedcom.access.application.acceso.decision.model.DecisionOutput;
import com.haedcom.access.application.time.TenantZoneProvider;
import com.haedcom.access.domain.enums.EstadoReglaAcceso;
import com.haedcom.access.domain.enums.TipoAccionAcceso;
import com.haedcom.access.domain.enums.TipoComandoDispositivo;
import com.haedcom.access.domain.model.CatalogoMotivoDecision;
import com.haedcom.access.domain.model.ReglaAcceso;
import com.haedcom.access.domain.repo.ReglaAccesoRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Motor de decisiones basado en reglas persistidas ({@link ReglaAcceso}).
 *
 * <p>
 * A diferencia de un {@link DecisionEngine} "puro", este componente:
 * </p>
 * <ul>
 * <li>Consulta la base de datos para obtener reglas candidatas del tenant.</li>
 * <li>Aplica matching (área, dispositivo opcional, sujeto, dirección, método, vigencias y ventana
 * diaria).</li>
 * <li>Selecciona la regla ganadora y la traduce a {@link DecisionOutput}.</li> *
 * <li>Evalúa la ventana diaria (HH:mm) usando la zona efectiva del tenant/área provista por
 * {@code TenantZoneProvider}.</li>
 * </ul>
 *
 * <p>
 * El objetivo es mantener {@code AccesoService} como orquestador transaccional, sin lógica de
 * política.
 * </p>
 */
@Named("decision-engine-db-v1")
@ApplicationScoped
public class DecisionEngineService implements DecisionEngine {

    private static final Logger LOG = Logger.getLogger(DecisionEngineService.class);
    private static final ZoneId UTC = ZoneId.of("UTC");

    public static final String MOTIVO_POLICY_ERROR =
            CatalogoMotivoDecision.MOTIVO_POLICY_ERROR.getCodigoMotivo();

    /**
     * TTL por defecto para decisiones PENDIENTE por "control/espera".
     *
     * <p>
     * Si aún no manejas expiración, puedes dejarlo en {@code null} al emitir la decisión.
     * </p>
     */
    private static final int WAIT_TTL_SECONDS = 15;

    private final ReglaAccesoCandidatesProvider candidatesProvider;
    private final Clock clock;
    private final TenantZoneProvider zoneProvider;

    private final MeterRegistry registry;
    private final Counter zoneFallback;
    private final Timer engineTimer;
    private final DistributionSummary candidatesSummary;
    private final Timer matchTimer;
    private final Counter malformedDailyRules;
    private final java.util.concurrent.ConcurrentMap<String, Counter> counterCache =
            new java.util.concurrent.ConcurrentHashMap<>();


    public DecisionEngineService(ReglaAccesoCandidatesProvider candidatesProvider, Clock clock,
            TenantZoneProvider zoneProvider, MeterRegistry registry) {
        this.candidatesProvider =
                Objects.requireNonNull(candidatesProvider, "candidatesProvider es obligatorio");
        this.clock = (clock != null) ? clock : Clock.systemUTC();
        this.zoneProvider = Objects.requireNonNull(zoneProvider, "zoneProvider es obligatorio");
        this.registry = Objects.requireNonNull(registry, "registry es obligatorio");

        this.zoneFallback =
                Counter.builder("access_engine_zone_fallback_total").register(this.registry);
        this.engineTimer = Timer.builder("access_engine_seconds").publishPercentileHistogram(true)
                .register(this.registry);
        this.candidatesSummary = DistributionSummary.builder("access_engine_candidates_count")
                .publishPercentileHistogram(true).register(this.registry);

        this.matchTimer = Timer.builder("access_engine_match_seconds")
                .publishPercentileHistogram(true).register(this.registry);
        this.malformedDailyRules = Counter.builder("access_engine_rules_malformed_daily_total")
                .register(this.registry);
    }

    @Override
    public DecisionOutput evaluate(DecisionContext ctx) {
        return engineTimer.record(() -> doEvaluate(ctx));
    }


    private DecisionOutput doEvaluate(DecisionContext ctx) {
        Objects.requireNonNull(ctx, "ctx es obligatorio");

        OffsetDateTime now = OffsetDateTime.now(clock);
        ZoneId effectiveZone = resolveZoneDefensive(ctx);

        // Validación defensiva mínima (si falta lo crítico, ERROR)
        if (ctx.orgId() == null || ctx.idArea() == null || ctx.tipoSujeto() == null) {
            DecisionOutput out = DecisionOutput.error(now, MOTIVO_POLICY_ERROR,
                    "Contexto incompleto para reglas");
            decisionsCounter(out.resultado().name(), out.codigoMotivo()).increment();
            return out;
        }

        // 1) Traer reglas base cacheadas (no dependientes de nowUtc)
        // Cache key: (orgId, areaId, tipoSujeto)
        List<ReglaAcceso> base =
                candidatesProvider.activeRulesBase(ctx.orgId(), ctx.idArea(), ctx.tipoSujeto());

        if (base == null || base.isEmpty()) {
            DecisionOutput out = fallbackNoRules(now);
            decisionsCounter(out.resultado().name(), out.codigoMotivo()).increment();
            return out;
        }

        candidatesSummary.record(base.size());

        long malformedDaily = base.stream()
                .filter(r -> (r.getDesdeHoraLocal() == null) ^ (r.getHastaHoraLocal() == null))
                .count();

        if (malformedDaily > 0) {
            malformedDailyRules.increment(malformedDaily);
            LOG.warnf("Reglas con ventana diaria malformada=%d orgId=%s areaId=%s sujeto=%s",
                    malformedDaily, ctx.orgId(), ctx.idArea(), ctx.tipoSujeto());
        }

        // 2) Matching final en memoria (incluye filtros “time-sensitive”)
        Optional<ReglaAcceso> best = matchTimer.record(() -> base.stream()
                .filter(r -> r.getEstado() == EstadoReglaAcceso.ACTIVA)
                .filter(r -> r.getIdDispositivo() == null
                        || Objects.equals(r.getIdDispositivo(), ctx.idDispositivo()))
                .filter(r -> r.getDireccionPaso() == null
                        || Objects.equals(r.getDireccionPaso(), ctx.direccionPaso()))
                .filter(r -> r.getMetodoAutenticacion() == null
                        || Objects.equals(r.getMetodoAutenticacion(), ctx.metodoAutenticacion()))
                .filter(r -> r.getValidoDesdeUtc() == null || !r.getValidoDesdeUtc().isAfter(now))
                .filter(r -> r.getValidoHastaUtc() == null || !r.getValidoHastaUtc().isBefore(now))
                .filter(r -> matchVentanaDiaria(r.getDesdeHoraLocal(), r.getHastaHoraLocal(), now,
                        effectiveZone))
                .max(ruleComparator()));


        if (best.isEmpty()) {
            LOG.debugf(
                    "DecisionEngine.no_match orgId=%s areaId=%s intentoId=%s base=%d now=%s zone=%s",
                    ctx.orgId(), ctx.idArea(), ctx.idIntento(), base.size(), now, effectiveZone);
            DecisionOutput out = fallbackNoMatch(now);
            decisionsCounter(out.resultado().name(), out.codigoMotivo()).increment();
            return out;
        }

        ReglaAcceso regla = best.get();
        DecisionOutput out = mapRuleToOutput(regla, now);

        LOG.debugf("DecisionEngineService.match reglaId=%s accion=%s prioridad=%s",
                regla.getIdRegla(), regla.getAccion(), regla.getPrioridad());
        LOG.debugf(
                "DecisionEngine.match orgId=%s areaId=%s intentoId=%s reglaId=%s accion=%s resultado=%s motivo=%s zone=%s",
                ctx.orgId(), ctx.idArea(), ctx.idIntento(), regla.getIdRegla(), regla.getAccion(),
                out.resultado(), out.codigoMotivo(), effectiveZone);

        decisionsCounter(out.resultado().name(), out.codigoMotivo()).increment();
        return out;
    }

    /**
     * Evalúa ventana diaria local en una zona horaria específica.
     *
     * <p>
     * Permite "overnight" (ej. 22:00 -> 06:00). Reglas:
     * </p>
     * <ul>
     * <li>ambos null -> true</li>
     * <li>solo uno null -> false (regla malformada)</li>
     * <li>desde == hasta -> false</li>
     * <li>si desde < hasta: now debe estar en [desde, hasta)</li>
     * <li>si desde > hasta: cruza medianoche; now ∈ [desde, 24h) ∪ [0, hasta)</li>
     * </ul>
     *
     * @param desde inicio (puede ser null si no hay restricción)
     * @param hasta fin (puede ser null si no hay restricción)
     * @param nowUtc instante actual en UTC
     * @param zone zona horaria efectiva (tenant/área)
     */
    private boolean matchVentanaDiaria(LocalTime desde, LocalTime hasta, OffsetDateTime nowUtc,
            ZoneId zone) {

        if (desde == null && hasta == null)
            return true;
        if (desde == null || hasta == null)
            return false;
        if (desde.equals(hasta))
            return false;

        ZoneId z = (zone != null) ? zone : UTC;
        LocalTime nowLocal = nowUtc.atZoneSameInstant(z).toLocalTime();


        // Caso normal: desde < hasta => [desde, hasta)
        if (desde.isBefore(hasta)) {
            return !nowLocal.isBefore(desde) && nowLocal.isBefore(hasta);
        }

        // Overnight: desde > hasta => [desde, 24h) U [0, hasta)
        return !nowLocal.isBefore(desde) || nowLocal.isBefore(hasta);
    }

    // =========================================================================
    // Selección de regla (defensiva)
    // =========================================================================

    /**
     * Comparador defensivo para escoger regla ganadora.
     *
     * <p>
     * Aunque {@link ReglaAccesoRepository#findCandidatesForIntent} ya ordena, se conserva este
     * comparador para robustez si en el futuro cambia el query.
     * </p>
     */
    private Comparator<ReglaAcceso> ruleComparator() {
        return Comparator
                .comparingInt((ReglaAcceso r) -> r.getPrioridad() != null ? r.getPrioridad() : 0)
                .thenComparing(ReglaAcceso::getActualizadoEnUtc,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReglaAcceso::getCreadoEnUtc,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }



    // =========================================================================
    // Mapping a DecisionOutput
    // =========================================================================

    /**
     * Traduce la acción de la regla a un {@link DecisionOutput}.
     *
     * <p>
     * Mapeo sugerido:
     * </p>
     * <ul>
     * <li>{@code PERMITIR} -> PERMITIR + ABRIR_PUERTA</li>
     * <li>{@code DENEGAR} -> DENEGAR + NEGAR_CON_SEÑAL</li>
     * <li>{@code REQUIERE_AUTENTICACION} -> PENDIENTE + sin comando (o comando de "SOLICITAR")</li>
     * <li>{@code CONTROL_REQUIERE_ESPERA} -> PENDIENTE + sin comando, con expiración corta</li>
     * </ul>
     */
    private DecisionOutput mapRuleToOutput(ReglaAcceso r, OffsetDateTime now) {
        TipoAccionAcceso accion = r.getAccion();
        String detalle =
                (r.getMensaje() != null && !r.getMensaje().isBlank()) ? r.getMensaje() : null;

        if (accion == null) {
            return DecisionOutput.error(now,
                    CatalogoMotivoDecision.MOTIVO_POLICY_ERROR.getCodigoMotivo(),
                    "Regla sin acción");
        }

        switch (accion) {
            case PERMITIR:
                return DecisionOutput.allow(now,
                        CatalogoMotivoDecision.MOTIVO_ALLOW.getCodigoMotivo(), detalle,
                        TipoComandoDispositivo.ABRIR_PUERTA, null);

            case DENEGAR:
                return DecisionOutput.deny(now,
                        CatalogoMotivoDecision.MOTIVO_DENY.getCodigoMotivo(), detalle,
                        TipoComandoDispositivo.NEGAR_CON_SEÑAL, "Acceso denegado");

            case REQUIERE_AUTENTICACION:
                return DecisionOutput.pending(now,
                        CatalogoMotivoDecision.MOTIVO_REQUIRE_AUTH.getCodigoMotivo(),
                        detalle != null ? detalle : "Requiere autenticación adicional", null, // comando
                                                                                              // sugerido
                                                                                              // (si
                                                                                              // más
                                                                                              // adelante
                                                                                              // tienes
                                                                                              // uno
                                                                                              // tipo
                                                                                              // SOLICITAR_AUTH)
                        detalle, null); // Expira: podemos poner un TTL si es necesario

            case CONTROL_REQUIERE_ESPERA:
                return DecisionOutput.pending(now,
                        CatalogoMotivoDecision.MOTIVO_WAIT_CONTROL.getCodigoMotivo(),
                        detalle != null ? detalle : "Control requiere espera", null, detalle,
                        now.plusSeconds(WAIT_TTL_SECONDS)); // TTL por espera controlada
            default:
                return DecisionOutput.error(now,
                        CatalogoMotivoDecision.MOTIVO_POLICY_ERROR.getCodigoMotivo(),
                        "Acción no soportada por la política");
        }
    }



    /**
     * Fallback conservador cuando no hay reglas aplicables.
     */
    private DecisionOutput fallbackNoMatch(OffsetDateTime now) {
        return DecisionOutput.deny(now,
                CatalogoMotivoDecision.MOTIVO_NO_MATCHING_RULE.getCodigoMotivo(),
                "Existen reglas, pero ninguna aplica al contexto",
                TipoComandoDispositivo.NEGAR_CON_SEÑAL, "Acceso denegado");
    }


    private ZoneId resolveZoneDefensive(DecisionContext ctx) {
        try {
            ZoneId z = zoneProvider.zoneFor(ctx.orgId(), ctx.idArea());
            if (z == null) {
                zoneFallback.increment();
                LOG.warnf("zoneProvider retornó null. Usando UTC. orgId=%s areaId=%s", ctx.orgId(),
                        ctx.idArea());
                return UTC;
            }
            return z;
        } catch (RuntimeException e) {
            zoneFallback.increment();
            LOG.warnf(e, "No se pudo resolver zona efectiva. Usando UTC. orgId=%s areaId=%s",
                    ctx.orgId(), ctx.idArea());
            return UTC;
        }
    }

    private Counter decisionsCounter(String result, String reason) {
        String r = (result == null || result.isBlank()) ? "unknown" : result;
        String rc = (reason == null || reason.isBlank()) ? "unknown" : reason;
        String key = "access_engine_decisions_total|result=" + r + "|reason=" + rc;
        return counterCache.computeIfAbsent(key,
                k -> Counter.builder("access_engine_decisions_total").tag("result", r)
                        .tag("reason", rc).register(registry));
    }

    private DecisionOutput fallbackNoRules(OffsetDateTime now) {
        return DecisionOutput.deny(now,
                CatalogoMotivoDecision.MOTIVO_NO_RULES_FOR_CONTEXT.getCodigoMotivo(),
                "No existen reglas base para el contexto", TipoComandoDispositivo.NEGAR_CON_SEÑAL,
                "Acceso denegado");
    }
}
