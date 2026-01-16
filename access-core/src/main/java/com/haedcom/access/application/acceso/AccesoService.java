package com.haedcom.access.application.acceso;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import com.fasterxml.jackson.databind.JsonNode;
import com.haedcom.access.application.acceso.decision.DecisionEngine;
import com.haedcom.access.application.acceso.decision.model.DecisionContext;
import com.haedcom.access.application.acceso.decision.model.DecisionOutput;
import com.haedcom.access.application.acceso.decision.model.DeviceSnapshot;
import com.haedcom.access.domain.enums.EstadoComandoDispositivo;
import com.haedcom.access.domain.enums.TipoComandoDispositivo;
import com.haedcom.access.domain.enums.TipoDireccionPaso;
import com.haedcom.access.domain.enums.TipoMetodoAutenticacion;
import com.haedcom.access.domain.enums.TipoResultadoDecision;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import com.haedcom.access.domain.events.ComandoDispositivoEmitido;
import com.haedcom.access.domain.events.DecisionAccesoTomada;
import com.haedcom.access.domain.events.DomainEventPublisher;
import com.haedcom.access.domain.events.IntentoAccesoRegistrado;
import com.haedcom.access.domain.model.CatalogoMotivoDecision;
import com.haedcom.access.domain.model.ComandoDispositivo;
import com.haedcom.access.domain.model.DecisionAcceso;
import com.haedcom.access.domain.model.Dispositivo;
import com.haedcom.access.domain.model.IntentoAcceso;
import com.haedcom.access.domain.repo.CatalogoMotivoDecisionRepository;
import com.haedcom.access.domain.repo.ComandoDispositivoRepository;
import com.haedcom.access.domain.repo.DecisionAccesoRepository;
import com.haedcom.access.domain.repo.DispositivoRepository;
import com.haedcom.access.domain.repo.IntentoAccesoRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;

/**
 * Servicio de aplicación responsable del flujo central de control de acceso.
 *
 * <p>
 * Implementa el caso de uso: <b>registrar un intento</b> (idempotente), <b>evaluar una decisión</b>
 * mediante un motor puro ({@link DecisionEngineV1}) y <b>emitir un comando</b> hacia el
 * dispositivo.
 * </p>
 *
 * <h2>Flujo</h2>
 * <ol>
 * <li>Validar idempotencia del intento</li>
 * <li>Validar existencia del dispositivo en el tenant</li>
 * <li>Persistir {@link IntentoAcceso}</li>
 * <li>Evaluar con {@link DecisionEngineV1} usando snapshots puros ({@link DecisionContext})</li>
 * <li>Persistir {@link DecisionAcceso}</li>
 * <li>Construir y persistir {@link ComandoDispositivo} (si aplica)</li>
 * <li>Publicar eventos de dominio (in-process por ahora)</li>
 * </ol>
 *
 * <h2>Logging estructurado</h2>
 * <p>
 * Este servicio agrega contexto en {@link MDC} para correlación:
 * <ul>
 * <li>{@code orgId}</li>
 * <li>{@code idemKey}</li>
 * <li>{@code intentoId}</li>
 * <li>{@code dispositivoId}</li>
 * <li>{@code decisionId}</li>
 * <li>{@code comandoId}</li>
 * </ul>
 * </p>
 *
 * <p>
 * Recomendación: configura tu logger para emitir JSON y mapear MDC (Quarkus + JBoss Logging).
 * </p>
 */
@ApplicationScoped
public class AccesoService {

        private static final Logger LOG = Logger.getLogger(AccesoService.class);

        /**
         * Motivo fallback cuando el engine retorna un código que no existe en catálogo.
         *
         * <p>
         * Debe existir en {@code catalogo_motivo_decision}.
         * </p>
         */
        private static final String MOTIVO_FALLBACK = "POLICY_ERROR";

        private final DispositivoRepository dispositivoRepo;
        private final IntentoAccesoRepository intentoRepo;
        private final DecisionAccesoRepository decisionRepo;
        private final ComandoDispositivoRepository comandoRepo;
        private final CatalogoMotivoDecisionRepository motivoRepo;
        private final DecisionEngine decisionEngine;
        private final DomainEventPublisher eventPublisher;
        private final Clock clock;

        private final Counter motivosFallbackUsed;
        private final Counter commandsExpected;

        private final Counter commandsEmitted;

        private final MeterRegistry registry;
        private final java.util.concurrent.ConcurrentMap<String, Counter> counterCache =
                        new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentMap<String, Timer> timerCache =
                        new java.util.concurrent.ConcurrentHashMap<>();

        /**
         * Constructor principal.
         *
         * @param dispositivoRepo repositorio de dispositivos
         * @param intentoRepo repositorio de intentos
         * @param decisionRepo repositorio de decisiones
         * @param comandoRepo repositorio de comandos
         * @param motivoRepo repositorio de catálogo de motivos
         * @param decisionEngine motor de decisión (contrato estable)
         * @param eventPublisher publicador de eventos de dominio
         * @param clock reloj (UTC recomendado) para testabilidad
         */
        public AccesoService(DispositivoRepository dispositivoRepo,
                        IntentoAccesoRepository intentoRepo, DecisionAccesoRepository decisionRepo,
                        ComandoDispositivoRepository comandoRepo,
                        CatalogoMotivoDecisionRepository motivoRepo,
                        @Named("decision-engine-v2") DecisionEngine decisionEngine,
                        DomainEventPublisher eventPublisher, Clock clock, MeterRegistry registry) {
                this.registry = Objects.requireNonNull(registry, "registry es obligatorio");
                this.dispositivoRepo = Objects.requireNonNull(dispositivoRepo,
                                "dispositivoRepo es obligatorio");
                this.intentoRepo =
                                Objects.requireNonNull(intentoRepo, "intentoRepo es obligatorio");
                this.decisionRepo =
                                Objects.requireNonNull(decisionRepo, "decisionRepo es obligatorio");
                this.comandoRepo =
                                Objects.requireNonNull(comandoRepo, "comandoRepo es obligatorio");
                this.motivoRepo = Objects.requireNonNull(motivoRepo, "motivoRepo es obligatorio");
                this.decisionEngine = Objects.requireNonNull(decisionEngine,
                                "decisionEngine es obligatorio");
                this.eventPublisher = Objects.requireNonNull(eventPublisher,
                                "eventPublisher es obligatorio");
                this.clock = (clock != null) ? clock : Clock.systemUTC();

                commandsEmitted =
                                Counter.builder("access_commands_emitted_total").register(registry);
                motivosFallbackUsed = Counter.builder("access_motivo_fallback_used_total")
                                .register(registry);
                commandsExpected = Counter.builder("access_commands_expected_total")
                                .register(registry);
        }

        /**
         * Registra un intento de acceso de forma idempotente.
         *
         * <p>
         * Si existe un intento con la misma {@code claveIdempotencia} en el tenant, reconstruye y
         * retorna el mismo resultado sin crear nuevas filas.
         * </p>
         *
         * @param orgId identificador del tenant
         * @param req request proveniente del gateway o dispositivo
         * @return resultado resumido del flujo
         */
        @Transactional
        public RegistrarIntentoResult registrarIntento(UUID orgId, RegistrarIntentoRequest req) {
                Objects.requireNonNull(orgId, "orgId es obligatorio");
                Objects.requireNonNull(req, "req es obligatorio");

                final String claveIdem = normalize(req.claveIdempotencia());
                if (claveIdem == null) {
                        throw new IllegalArgumentException("claveIdempotencia es obligatoria");
                }

                // Contexto de correlación (se limpia en finally)
                MDC.put("orgId", orgId.toString());
                MDC.put("idemKey", claveIdem);

                // Campos del request que ayudan mucho para debug (sin loggear payload crudo)
                if (req.idDispositivo() != null)
                        MDC.put("dispositivoId", req.idDispositivo().toString());
                if (req.idArea() != null)
                        MDC.put("areaId", req.idArea().toString());
                if (req.direccionPaso() != null)
                        MDC.put("direccionPaso", req.direccionPaso().name());
                if (req.metodoAutenticacion() != null)
                        MDC.put("metodoAuth", req.metodoAutenticacion().name());
                if (normalize(req.idGatewaySolicitud()) != null)
                        MDC.put("gatewayReqId", normalize(req.idGatewaySolicitud()));

                final Timer.Sample sample = Timer.start(this.registry);
                String resultTag = "error";
                final long t0 = System.nanoTime();

                try {
                        LOG.debug("Acceso.registrarIntento - inicio");

                        // -----------------------------------------------------------------
                        // 1) Idempotencia
                        // -----------------------------------------------------------------
                        Optional<IntentoAcceso> existente =
                                        intentoRepo.findByIdempotencia(orgId, claveIdem);
                        if (existente.isPresent()) {
                                IntentoAcceso i = existente.get();
                                MDC.put("intentoId", safeUuid(i.getIdIntento()));
                                MDC.put("dispositivoId", safeUuid(i.getIdDispositivo()));

                                RegistrarIntentoResult r = reconstruirResultado(i);

                                long ms = elapsedMs(t0);
                                MDC.put("elapsedMs", Long.toString(ms));
                                LOG.infof("Acceso.registrarIntento - idempotent_hit resultado=%s",
                                                r.resultado());
                                resultTag = "idempotent_hit";
                                attemptsCounter("idempotent_hit").increment();
                                return r;
                        }

                        // -----------------------------------------------------------------
                        // 2) Validar dispositivo en tenant
                        // -----------------------------------------------------------------
                        Dispositivo dispositivo = dispositivoRepo
                                        .findByIdAndOrganizacion(req.idDispositivo(), orgId)
                                        .orElseThrow(() -> new NotFoundException(
                                                        "Dispositivo no encontrado para la organización"));

                        MDC.put("dispositivoId", safeUuid(dispositivo.getIdDispositivo()));

                        OffsetDateTime now = OffsetDateTime.now(clock);

                        // -----------------------------------------------------------------
                        // 3) Persistir intento
                        // -----------------------------------------------------------------
                        IntentoAcceso intento =
                                        crearIntento(orgId, req, dispositivo, claveIdem, now);
                        dbTimer("persist_intento").record(() -> intentoRepo.persist(intento));

                        MDC.put("intentoId", safeUuid(intento.getIdIntento()));

                        publishTimer("IntentoAccesoRegistrado").record(() -> eventPublisher.publish(
                                        new IntentoAccesoRegistrado(orgId, intento.getIdIntento(),
                                                        intento.getIdDispositivo(),
                                                        intento.getIdArea(),
                                                        intento.getDireccionPaso(),
                                                        intento.getMetodoAutenticacion(),
                                                        intento.getTipoSujeto(),
                                                        intento.getClaveIdempotencia(),
                                                        intento.getOcurridoEnUtc())));

                        // -----------------------------------------------------------------
                        // 4) Evaluar decisión con engine (snapshots puros)
                        // -----------------------------------------------------------------
                        DeviceSnapshot deviceSnapshot = new DeviceSnapshot(
                                        dispositivo.getIdDispositivo(),
                                        dispositivo.getIdOrganizacion(), // proviene de
                                                                         // TenantOnlyEntity
                                        dispositivo.getIdArea(), dispositivo.getNombre(),
                                        dispositivo.getModelo(),
                                        dispositivo.getIdentificadorExterno(),
                                        dispositivo.isEstadoActivo());

                        DecisionContext ctx = new DecisionContext(orgId, intento.getIdIntento(),
                                        intento.getIdDispositivo(), intento.getIdArea(),
                                        intento.getDireccionPaso(),
                                        intento.getMetodoAutenticacion(), intento.getTipoSujeto(),
                                        deviceSnapshot);

                        DecisionOutput out =
                                        engineTimer().record(() -> decisionEngine.evaluate(ctx));
                        if (out != null && out.comandoSugerido() != null) {
                                commandsExpected.increment();
                        }
                        countDecisionMetrics(out);

                        // Log “de negocio” (sin datos sensibles)
                        if (out == null) {
                                LOG.warn("Acceso.decision - resultado=null (engine devolvió null)");
                        } else {
                                LOG.infof("Acceso.decision - resultado=%s motivo=%s cmd=%s",
                                                out.resultado(), out.codigoMotivo(),
                                                out.comandoSugerido());
                        }


                        // -----------------------------------------------------------------
                        // 5) Persistir decisión
                        // -----------------------------------------------------------------
                        if (out == null) {
                                // si quieres: métrica específica
                                decisionReasonCounter("engine_null").increment();
                                throw new IllegalStateException("DecisionEngine devolvió null");
                        }

                        DecisionAcceso decision = construirDecision(orgId, intento, out);
                        dbTimer("persist_decision").record(() -> decisionRepo.persist(decision));

                        MDC.put("decisionId", safeUuid(decision.getIdDecision()));
                        MDC.put("decisionResultado",
                                        decision.getResultado() != null
                                                        ? decision.getResultado().name()
                                                        : null);
                        MDC.put("decisionMotivo",
                                        decision.getMotivo() != null
                                                        ? decision.getMotivo().getCodigoMotivo()
                                                        : null);

                        publishTimer("DecisionAccesoTomada").record(() -> eventPublisher.publish(
                                        new DecisionAccesoTomada(orgId, decision.getIdDecision(),
                                                        intento.getIdIntento(),
                                                        decision.getResultado(),
                                                        decision.getMotivo().getCodigoMotivo(),
                                                        decision.getDetalleMotivo(),
                                                        decision.getDecididoEnUtc(),
                                                        decision.getExpiraEnUtc())));

                        // -----------------------------------------------------------------
                        // 6) Construir/persistir comando (si aplica)
                        // -----------------------------------------------------------------
                        ComandoDispositivo comando = construirComandoDesdeDecisionOutput(orgId,
                                        intento, dispositivo, out);
                        if (out != null && out.comandoSugerido() != null && comando == null) {
                                commandsGapCounter().increment();
                        }
                        if (comando != null) {
                                commandsEmitted.increment();
                                dbTimer("persist_comando")
                                                .record(() -> comandoRepo.persist(comando));

                                MDC.put("comandoId", safeUuid(comando.getIdComando()));
                                MDC.put("comandoTipo",
                                                comando.getComando() != null
                                                                ? comando.getComando().name()
                                                                : null);
                                publishTimer("ComandoDispositivoEmitido")
                                                .record(() -> eventPublisher.publish(
                                                                new ComandoDispositivoEmitido(orgId,
                                                                                comando.getIdComando(),
                                                                                intento.getIdIntento(),
                                                                                comando.getIdDispositivo(),
                                                                                comando.getComando(),
                                                                                comando.getMensaje(),
                                                                                comando.getEstado(),
                                                                                comando.getClaveIdempotencia(),
                                                                                comando.getEnviadoEnUtc())));
                        }
                        dbTimer("flush").record(intentoRepo::flush);

                        RegistrarIntentoResult result =
                                        RegistrarIntentoResult.from(intento, decision, comando);

                        long ms = elapsedMs(t0);
                        MDC.put("elapsedMs", Long.toString(ms));
                        LOG.infof("Acceso.registrarIntento - ok resultado=%s", result.resultado());
                        resultTag = "ok";
                        attemptsCounter("ok").increment();
                        return result;
                } catch (NotFoundException e) {
                        // 404 esperado: dispositivo no pertenece al tenant o no existe
                        long ms = elapsedMs(t0);
                        MDC.put("elapsedMs", Long.toString(ms));
                        LOG.warn("Acceso.registrarIntento - not_found", e);
                        resultTag = "not_found";
                        attemptsCounter("not_found").increment();
                        throw e;
                } catch (WebApplicationException e) {
                        // Para capturar 400/409/etc provenientes de JAX-RS (no solo
                        // IllegalArgumentException)
                        int status = (e.getResponse() != null) ? e.getResponse().getStatus() : 500;

                        long ms = elapsedMs(t0);
                        MDC.put("elapsedMs", Long.toString(ms));

                        if (status == 404) {
                                // por si algo lanzó 404 que no sea NotFoundException (raro, pero
                                // posible)
                                resultTag = "not_found";
                                attemptsCounter("not_found").increment();
                                LOG.warn("Acceso.registrarIntento - not_found(web)", e);
                        } else if (status == 409) {
                                resultTag = "conflict";
                                attemptsCounter("conflict").increment();
                                LOG.warn("Acceso.registrarIntento - conflict", e);
                        } else if (status >= 400 && status < 500) {
                                resultTag = "bad_request";
                                attemptsCounter("bad_request").increment();
                                LOG.warn("Acceso.registrarIntento - bad_request(web)", e);
                        } else {
                                resultTag = "error";
                                attemptsCounter("error").increment();
                                LOG.error("Acceso.registrarIntento - error(web)", e);
                        }
                        throw e;

                } catch (IllegalArgumentException e) {
                        long ms = elapsedMs(t0);
                        MDC.put("elapsedMs", Long.toString(ms));
                        LOG.warn("Acceso.registrarIntento - bad_request", e);
                        resultTag = "bad_request";
                        attemptsCounter("bad_request").increment();
                        throw e;
                } catch (RuntimeException e) {
                        // Errores inesperados / integridad / etc.
                        long ms = elapsedMs(t0);
                        MDC.put("elapsedMs", Long.toString(ms));
                        LOG.error("Acceso.registrarIntento - error", e);
                        resultTag = "error";
                        attemptsCounter("error").increment();
                        throw e;
                } finally {
                        // Limpieza para no “contaminar” el hilo (importante en runtimes con thread
                        // reuse)
                        sample.stop(flowTimer(resultTag));
                        clearMdc();
                }
        }

        // =====================================================================
        // Builders
        // =====================================================================

        /**
         * Construye un {@link IntentoAcceso} inicializado correctamente.
         */
        private IntentoAcceso crearIntento(UUID orgId, RegistrarIntentoRequest req,
                        Dispositivo dispositivo, String claveIdem, OffsetDateTime now) {
                IntentoAcceso i = new IntentoAcceso();
                i.setIdIntento(UUID.randomUUID());
                i.setIdDispositivo(dispositivo.getIdDispositivo());
                i.setIdArea(req.idArea());
                i.setDireccionPaso(req.direccionPaso());
                i.setMetodoAutenticacion(req.metodoAutenticacion());
                i.setTipoSujeto(TipoSujetoAcceso.DESCONOCIDO);
                i.setReferenciaCredencial(normalize(req.referenciaCredencial()));
                i.setCargaCruda(req.cargaCruda());
                i.setClaveIdempotencia(claveIdem);
                i.setIdGatewaySolicitud(normalize(req.idGatewaySolicitud()));
                i.setOcurridoEnUtc(req.ocurridoEnUtc() != null ? req.ocurridoEnUtc() : now);
                i.assignTenant(orgId);
                return i;
        }

        /**
         * Traduce el {@link DecisionOutput} a una entidad {@link DecisionAcceso} persistible.
         */
        private DecisionAcceso construirDecision(UUID orgId, IntentoAcceso intento,
                        DecisionOutput out) {
                Objects.requireNonNull(out, "DecisionOutput no puede ser null");

                DecisionAcceso d = new DecisionAcceso();
                d.setIdDecision(UUID.randomUUID());
                d.setIntento(intento);
                d.setResultado(out.resultado());
                d.setDetalleMotivo(out.detalleMotivo());
                d.setDecididoEnUtc(out.decididoEnUtc() != null ? out.decididoEnUtc()
                                : OffsetDateTime.now(clock));
                d.setExpiraEnUtc(out.expiraEnUtc());
                d.assignTenant(orgId);

                String codigo = normalize(out.codigoMotivo());
                CatalogoMotivoDecision motivo = resolveMotivoOrFallback(codigo);
                d.setMotivo(motivo);

                return d;
        }

        /**
         * Resuelve el motivo por código; si no existe, intenta el fallback.
         */
        private CatalogoMotivoDecision resolveMotivoOrFallback(String codigo) {
                String target = (codigo != null) ? codigo : MOTIVO_FALLBACK;

                Optional<CatalogoMotivoDecision> primary = motivoRepo.findByCodigo(target);
                if (primary.isPresent())
                        return primary.get();

                // Fallback
                Optional<CatalogoMotivoDecision> fallback =
                                motivoRepo.findByCodigo(MOTIVO_FALLBACK);
                if (fallback.isPresent()) {
                        LOG.warnf("Motivo no encontrado en catálogo: %s. Usando fallback: %s",
                                        target, MOTIVO_FALLBACK);
                        motivosFallbackUsed.increment();
                        return fallback.get();
                }

                throw new IllegalStateException(
                                "Falta CatalogoMotivoDecision requerido. Crea el motivo: " + target
                                                + " (y/o el fallback " + MOTIVO_FALLBACK + ")");
        }

        /**
         * Construye el {@link ComandoDispositivo} según el comando sugerido por el motor.
         */
        private ComandoDispositivo construirComandoDesdeDecisionOutput(UUID orgId,
                        IntentoAcceso intento, Dispositivo dispositivo, DecisionOutput out) {
                if (out == null || out.comandoSugerido() == null)
                        return null;

                ComandoDispositivo c = new ComandoDispositivo();
                c.setIdComando(UUID.randomUUID());
                c.setIntento(intento);
                c.setIdDispositivo(dispositivo.getIdDispositivo());
                c.setComando(out.comandoSugerido());
                c.setMensaje(safeMsg(out.mensajeSugerido()));
                c.setEstado(EstadoComandoDispositivo.ENVIADO);
                c.setEnviadoEnUtc(out.decididoEnUtc() != null ? out.decididoEnUtc()
                                : OffsetDateTime.now(clock));
                c.setClaveIdempotencia("CMD:" + intento.getClaveIdempotencia() + ":"
                                + out.comandoSugerido().name());
                c.assignTenant(orgId);
                return c;
        }

        /**
         * Reconstruye el resultado para idempotencia.
         *
         * <p>
         * Nota: este método NO vuelve a publicar eventos, ni re-emite comandos: solo devuelve el
         * estado ya persistido.
         * </p>
         */
        private RegistrarIntentoResult reconstruirResultado(IntentoAcceso intento) {
                DecisionAcceso decision = decisionRepo
                                .findByIntento(intento.getIdOrganizacion(), intento.getIdIntento())
                                .orElse(null);

                ComandoDispositivo comando = comandoRepo
                                .listByIntento(intento.getIdOrganizacion(), intento.getIdIntento())
                                .stream().findFirst().orElse(null);

                return RegistrarIntentoResult.from(intento, decision, comando);
        }

        // =====================================================================
        // Utilidades
        // =====================================================================

        private Counter commandsGapCounter() {
                return cachedCounter("access_commands_gap_total");
        }


        private Timer publishTimer(String event) {
                String e = (event == null || event.isBlank()) ? "unknown" : event;
                String key = "access_publish_seconds|event=" + e;
                return timerCache.computeIfAbsent(key,
                                k -> Timer.builder("access_publish_seconds").tag("event", e)
                                                .publishPercentileHistogram(true)
                                                .register(registry));
        }


        private Timer dbTimer(String phase) {
                String p = (phase == null || phase.isBlank()) ? "unknown" : phase;
                String key = "access_db_seconds|phase=" + p;
                return timerCache.computeIfAbsent(key,
                                k -> Timer.builder("access_db_seconds").tag("phase", p)
                                                .publishPercentileHistogram(true)
                                                .register(registry));
        }


        private Timer engineTimer() {
                return timerCache.computeIfAbsent("access_engine_seconds",
                                k -> Timer.builder("access_engine_seconds")
                                                .publishPercentileHistogram(true)
                                                .register(registry));
        }


        private Timer flowTimer(String result) {
                String r = (result == null || result.isBlank()) ? "unknown" : result;
                String key = "access_flow_seconds|result=" + r;
                return timerCache.computeIfAbsent(key,
                                k -> Timer.builder("access_flow_seconds").tag("result", r)
                                                .publishPercentileHistogram(true)
                                                .register(registry));
        }

        private Counter decisionsCounter(String result) {
                String r = (result == null || result.isBlank()) ? "unknown" : result;
                return cachedCounter("access_decisions_total", "result", r);
        }

        private Counter decisionReasonCounter(String bucket) {
                String b = (bucket == null || bucket.isBlank()) ? "unknown" : bucket;
                return cachedCounter("access_decision_reasons_total", "bucket", b);
        }



        private String normalize(String s) {
                if (s == null)
                        return null;
                String v = s.trim();
                return v.isBlank() ? null : v;
        }

        private String safeMsg(String msg) {
                if (msg == null)
                        return null;
                String v = msg.trim();
                return v.length() <= 120 ? v : v.substring(0, 120);
        }

        private static String safeUuid(UUID id) {
                return id != null ? id.toString() : null;
        }

        private static long elapsedMs(long startNano) {
                return (System.nanoTime() - startNano) / 1_000_000L;
        }

        /**
         * Limpia únicamente las keys que este servicio agrega.
         */
        private static void clearMdc() {
                MDC.remove("orgId");
                MDC.remove("idemKey");
                MDC.remove("intentoId");
                MDC.remove("dispositivoId");
                MDC.remove("areaId");
                MDC.remove("direccionPaso");
                MDC.remove("metodoAuth");
                MDC.remove("gatewayReqId");
                MDC.remove("decisionId");
                MDC.remove("decisionResultado");
                MDC.remove("decisionMotivo");
                MDC.remove("comandoId");
                MDC.remove("comandoTipo");
                MDC.remove("elapsedMs");
        }

        private void countDecisionMetrics(DecisionOutput out) {
                if (out == null) {
                        decisionsCounter("ERROR").increment();
                        return;
                }
                if (out.resultado() == null) {
                        decisionsCounter("ERROR").increment();
                        decisionReasonCounter("missing").increment();
                        return;
                }



                TipoResultadoDecision r = out.resultado();
                switch (r) {
                        case PERMITIR -> decisionsCounter("PERMITIR").increment();
                        case DENEGAR -> decisionsCounter("DENEGAR").increment();
                        case PENDIENTE -> decisionsCounter("PENDIENTE").increment();
                        case ERROR -> decisionsCounter("ERROR").increment();
                        default -> decisionsCounter("ERROR").increment();
                }


                String codigo = normalize(out.codigoMotivo());
                if ("NO_MATCHING_RULE".equals(codigo)) {
                        decisionReasonCounter("no_match").increment();
                } else if ("POLICY_ERROR".equals(codigo)) {
                        decisionReasonCounter("policy_error").increment();
                } else if (codigo == null) {
                        decisionReasonCounter("missing").increment();
                } else {
                        decisionReasonCounter("other").increment();
                }

        }

        private Counter attemptsCounter(String result) {
                String r = (result == null || result.isBlank()) ? "unknown" : result;
                return cachedCounter("access_attempts_total", "result", r);
        }

        private Counter cachedCounter(String name, String... tags) {
                StringBuilder key = new StringBuilder(name);
                for (int i = 0; i < tags.length; i += 2) {
                        key.append('|').append(tags[i]).append('=').append(tags[i + 1]);
                }
                return counterCache.computeIfAbsent(key.toString(),
                                k -> Counter.builder(name).tags(tags).register(registry));
        }

        // =====================================================================
        // DTOs
        // =====================================================================

        /**
         * Request para registrar un intento de acceso.
         *
         * @param idDispositivo id del dispositivo que origina el intento
         * @param idArea área donde ocurre el intento
         * @param direccionPaso entrada/salida
         * @param metodoAutenticacion método de autenticación reportado por el dispositivo
         * @param referenciaCredencial referencia opaca (plantilla biométrica, QR, etc.)
         * @param cargaCruda payload crudo (json) para auditoría
         * @param claveIdempotencia clave idempotente por tenant
         * @param idGatewaySolicitud id opcional del gateway
         * @param ocurridoEnUtc timestamp del evento (si null, se usa now)
         */
        public record RegistrarIntentoRequest(UUID idDispositivo, UUID idArea,
                        TipoDireccionPaso direccionPaso,
                        TipoMetodoAutenticacion metodoAutenticacion, String referenciaCredencial,
                        JsonNode cargaCruda, String claveIdempotencia, String idGatewaySolicitud,
                        OffsetDateTime ocurridoEnUtc) {
        }

        /**
         * Resultado resumido del flujo de acceso para responder al gateway.
         */
        public record RegistrarIntentoResult(UUID idIntento, TipoResultadoDecision resultado,
                        UUID idDecision, UUID idComando, TipoComandoDispositivo comandoEmitido,
                        EstadoComandoDispositivo estadoComando) {
                public static RegistrarIntentoResult from(IntentoAcceso intento,
                                DecisionAcceso decision, ComandoDispositivo comando) {
                        return new RegistrarIntentoResult(intento.getIdIntento(),
                                        decision != null ? decision.getResultado()
                                                        : TipoResultadoDecision.ERROR,
                                        decision != null ? decision.getIdDecision() : null,
                                        comando != null ? comando.getIdComando() : null,
                                        comando != null ? comando.getComando() : null,
                                        comando != null ? comando.getEstado() : null);
                }
        }
}
