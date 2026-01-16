package com.haedcom.access.application.reglaacceso;

import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jboss.logging.Logger;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.reglaacceso.dto.ReglaAccesoEstadoRequest;
import com.haedcom.access.api.reglaacceso.dto.ReglaAccesoResponse;
import com.haedcom.access.api.reglaacceso.dto.ReglaAccesoSearchRequest;
import com.haedcom.access.api.reglaacceso.dto.ReglaAccesoUpsertRequest;
import com.haedcom.access.application.time.TenantZoneProvider;
import com.haedcom.access.domain.enums.EstadoReglaAcceso;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import com.haedcom.access.domain.events.DomainEventPublisher;
import com.haedcom.access.domain.events.ReglaAccesoChangeRejected;
import com.haedcom.access.domain.events.ReglaAccesoPolicyChanged;
import com.haedcom.access.domain.model.Area;
import com.haedcom.access.domain.model.Dispositivo;
import com.haedcom.access.domain.model.ReglaAcceso;
import com.haedcom.access.domain.repo.AreaRepository;
import com.haedcom.access.domain.repo.DispositivoRepository;
import com.haedcom.access.domain.repo.ReglaAccesoRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Servicio de aplicación para CRUD de {@link ReglaAcceso}.
 *
 * <h2>Propósito</h2> Mantener reglas consistentes y seguras en un contexto <b>multi-tenant</b>:
 * <ul>
 * <li>Validación de pertenencia al tenant (área/dispositivo/regla).</li>
 * <li>Validación de ventanas temporales:
 * <ul>
 * <li><b>Vigencia UTC</b>: {@code validoDesdeUtc / validoHastaUtc}.</li>
 * <li><b>Ventana diaria HH:mm</b>: se valida aquí (formato y consistencia), pero su interpretación
 * por zona horaria se hace en el motor de decisión usando {@link TenantZoneProvider}.</li>
 * </ul>
 * </li>
 * <li>Prevención de reglas duplicadas lógicas (misma firma de matching + misma acción).</li>
 * <li>Soft-delete recomendado vía {@link EstadoReglaAcceso}.</li>
 * </ul>
 *
 * <h2>Auditoría funcional (Nivel B)</h2>
 * <ul>
 * <li>Para cambios exitosos publica {@link ReglaAccesoPolicyChanged} (invalida caches
 * distribuídos).</li>
 * <li>Para rechazos relevantes publica {@link ReglaAccesoChangeRejected} (best-effort).</li>
 * </ul>
 *
 * <p>
 * Importante: {@link ReglaAccesoChangeRejected} exige {@code areaId} no-null. Por lo tanto,
 * rechazos tipo 404 cuando aún no se conoce el área (regla inexistente) <b>no se auditan</b> (por
 * diseño).
 * </p>
 */
@ApplicationScoped
public class ReglaAccesoService {

    private static final Logger LOG = Logger.getLogger(ReglaAccesoService.class);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final ReglaAccesoRepository reglaRepo;
    private final AreaRepository areaRepo;
    private final DispositivoRepository dispositivoRepo;
    private final TenantZoneProvider zoneProvider;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    // ---- métricas (names/tags)
    private static final String M_RULE_OPS_TOTAL = "access_rule_ops_total";
    private static final String M_RULE_OP_SECONDS = "access_rule_op_seconds";
    private static final String M_RULE_REJECTS_TOTAL = "access_rule_rejects_total";
    private static final String M_RULE_POLICY_CHANGED_TOTAL = "access_rule_policy_changed_total";
    private static final String M_RULE_DUP_CONFLICTS_TOTAL =
            "access_rule_duplicate_conflicts_total";
    private static final String M_RULE_PRECONDITION_FAILED_TOTAL =
            "access_rule_precondition_failed_total";
    private static final String M_RULE_ZONE_FALLBACK_TOTAL = "access_rule_zone_fallback_total";
    private static final String M_RULE_VALIDATION_FAILED_TOTAL =
            "access_rule_validation_failed_total";
    private static final String M_RULE_FEATURE_USED_TOTAL = "access_rule_feature_used_total";

    // Counters “multi-tag” (se obtienen con helper counter(...))
    private final MeterRegistry registry;

    // contadores simples
    private final Counter duplicateConflicts;
    private final Counter zoneFallback;

    private final java.util.concurrent.ConcurrentMap<String, Counter> counterCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.ConcurrentMap<String, Timer> timerCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Constructor principal.
     *
     * @param reglaRepo repositorio de reglas (no null)
     * @param areaRepo repositorio de áreas (no null)
     * @param dispositivoRepo repositorio de dispositivos (no null)
     * @param zoneProvider resolvedor de zona efectiva (no null). Se usa aquí solo para validación
     *        defensiva
     * @param eventPublisher publicador de eventos de dominio (no null)
     * @param clock reloj (UTC recomendado) para testabilidad
     */
    public ReglaAccesoService(ReglaAccesoRepository reglaRepo, AreaRepository areaRepo,
            DispositivoRepository dispositivoRepo, TenantZoneProvider zoneProvider,
            DomainEventPublisher eventPublisher, Clock clock, MeterRegistry registry) {

        this.reglaRepo = Objects.requireNonNull(reglaRepo, "reglaRepo es obligatorio");
        this.areaRepo = Objects.requireNonNull(areaRepo, "areaRepo es obligatorio");
        this.dispositivoRepo =
                Objects.requireNonNull(dispositivoRepo, "dispositivoRepo es obligatorio");
        this.zoneProvider = Objects.requireNonNull(zoneProvider, "zoneProvider es obligatorio");
        this.eventPublisher =
                Objects.requireNonNull(eventPublisher, "eventPublisher es obligatorio");
        this.clock = (clock != null) ? clock : Clock.systemUTC();
        this.registry = Objects.requireNonNull(registry, "registry es obligatorio");

        this.duplicateConflicts =
                Counter.builder(M_RULE_DUP_CONFLICTS_TOTAL).register(this.registry);

        this.zoneFallback = Counter.builder(M_RULE_ZONE_FALLBACK_TOTAL).register(this.registry);
    }

    // =========================================================================
    // LIST
    // =========================================================================

    /**
     * Lista reglas del tenant con filtros y paginación.
     *
     * <p>
     * Diseñado para endpoints de administración.
     * </p>
     *
     * @param orgId tenant (obligatorio)
     * @param filters filtros opcionales
     * @param page página base-0 (>= 0)
     * @param size tamaño de página (1..200)
     * @return página de reglas
     */
    @Transactional
    public PageResponse<ReglaAccesoResponse> list(UUID orgId, ReglaAccesoSearchRequest filters,
            int page, int size) {
        return timedOp(Op.LIST, () -> {
            requireOrg(orgId, Op.LIST);
            requirePaging(page, size, Op.LIST);

            UUID idArea = (filters != null) ? filters.idArea() : null;
            UUID idDispositivo = (filters != null) ? filters.idDispositivo() : null;
            TipoSujetoAcceso tipoSujeto = (filters != null) ? filters.tipoSujeto() : null;
            EstadoReglaAcceso estado = (filters != null) ? filters.estado() : null;

            List<ReglaAccesoResponse> items =
                    reglaRepo.searchByOrganizacion(orgId, idArea, idDispositivo, tipoSujeto, null, // direccionPaso
                            null, // metodoAutenticacion
                            null, // accion
                            estado, page, size).stream().map(this::toResponse).toList();

            long total = reglaRepo.countSearchByOrganizacion(orgId, idArea, idDispositivo,
                    tipoSujeto, null, null, null, estado);

            return PageResponse.of(items, page, size, total);
        });
    }

    // =========================================================================
    // GET
    // =========================================================================

    /**
     * Obtiene una regla por id dentro del tenant.
     *
     * @param orgId tenant (obligatorio)
     * @param reglaId id de la regla (obligatorio)
     * @return regla encontrada
     * @throws NotFoundException si no existe o no pertenece al tenant
     */
    @Transactional
    public ReglaAccesoResponse get(UUID orgId, UUID reglaId) {
        return timedOp(Op.GET, () -> {
            requireOrg(orgId, Op.GET);
            if (reglaId == null) {
                precondFailedCounter(Op.GET, "reglaId").increment();
                throw new IllegalArgumentException("reglaId es obligatorio");
            }
            return toResponse(getReglaOrThrow(orgId, reglaId));
        });
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * Crea una regla de acceso dentro del tenant.
     *
     * <p>
     * En caso de rechazo (400/404/409), se publica {@link ReglaAccesoChangeRejected} (best-effort),
     * siempre que se conozca {@code areaId}.
     * </p>
     *
     * @param orgId tenant (obligatorio)
     * @param req payload (obligatorio)
     * @return regla creada
     */
    @Transactional
    public ReglaAccesoResponse create(UUID orgId, ReglaAccesoUpsertRequest req) {
        requireOrg(orgId, Op.CREATE);
        Objects.requireNonNull(req, "req es obligatorio");

        final UUID areaIdForAudit = req.idArea();

        try {
            ReglaAccesoResponse resp = timedOp(Op.CREATE, () -> {
                // 1) Referencias tenant-safe
                Area area = getAreaOrThrow(Op.CREATE, orgId, req.idArea());
                Dispositivo dispositivo = (req.idDispositivo() != null)
                        ? getDispositivoOrThrow(Op.CREATE, orgId, req.idDispositivo())
                        : null;

                // 2) Zona efectiva defensiva
                ZoneId effectiveZone = resolveZoneDefensive(orgId, area.getIdArea());
                LOG.debugf("ReglaAccesoService.create zone=%s orgId=%s areaId=%s", effectiveZone,
                        orgId, area.getIdArea());

                // 3) Validaciones temporales
                validateVigenciaUtc(Op.CREATE, req.validoDesdeUtc(), req.validoHastaUtc());
                LocalTime desdeHora =
                        parseHHmmOrNull(Op.CREATE, req.desdeHoraLocal(), "desdeHoraLocal");
                LocalTime hastaHora =
                        parseHHmmOrNull(Op.CREATE, req.hastaHoraLocal(), "hastaHoraLocal");
                validateVentanaDiaria(Op.CREATE, desdeHora, hastaHora);

                // 4) Consistencia área-dispositivo
                validateDispositivoPerteneceArea(Op.CREATE, dispositivo, area);

                // 5) Anti-duplicados
                boolean dup = reglaRepo.existsDuplicateRule(orgId, area.getIdArea(),
                        req.tipoSujeto(), req.idDispositivo(), req.direccionPaso(),
                        req.metodoAutenticacion(), req.accion(), req.validoDesdeUtc(),
                        req.validoHastaUtc(), desdeHora, hastaHora, null);


                if (dup) {
                    duplicateConflicts.increment();
                    throw new WebApplicationException(
                            "Ya existe una regla equivalente para ese criterio",
                            Response.Status.CONFLICT);
                }

                // 6) Crear y persistir
                ReglaAcceso r = ReglaAcceso.crear(orgId, area.getIdArea(), req.tipoSujeto(),
                        req.idDispositivo(), req.direccionPaso(), req.metodoAutenticacion(),
                        req.accion(), req.validoDesdeUtc(), req.validoHastaUtc(), desdeHora,
                        hastaHora, req.prioridad(), req.mensaje());

                reglaRepo.persist(r);
                dbTimer(Op.CREATE).record(reglaRepo::flush);


                // 6b) Feature adoption: contar solo si la regla se creó exitosamente
                if (req.validoDesdeUtc() != null)
                    featureUsedCounter("vigencia_utc", Op.CREATE).increment();
                if (req.desdeHoraLocal() != null)
                    featureUsedCounter("daily_window", Op.CREATE).increment();
                if (req.idDispositivo() != null)
                    featureUsedCounter("device_scoped", Op.CREATE).increment();


                // 7) Evento de cambio exitoso (invalida caches)
                OffsetDateTime nowUtc = OffsetDateTime.now(clock);
                policyChangedCounter(ReglaAccesoPolicyChanged.ChangeType.CREATED).increment();
                publishTimer(Op.CREATE).record(() -> eventPublisher
                        .publish(ReglaAccesoPolicyChanged.of(orgId, r.getIdArea(), r.getIdRegla(),
                                ReglaAccesoPolicyChanged.ChangeType.CREATED, nowUtc)));

                return toResponse(r);
            });

            return resp;


        } catch (NotFoundException e) {
            // No siempre se puede auditar: si no hay areaId (p.ej. req mal) se omitirá.
            auditReject(orgId, areaIdForAudit, null, ReglaAccesoChangeRejected.Operation.CREATE,
                    mapReasonCode(404), 404, e.getMessage());
            throw e;

        } catch (WebApplicationException e) {
            int status = statusOf(e, 400);
            auditReject(orgId, areaIdForAudit, null, ReglaAccesoChangeRejected.Operation.CREATE,
                    mapReasonCode(status), status, e.getMessage());
            throw e;

        } catch (RuntimeException e) {
            auditReject(orgId, areaIdForAudit, null, ReglaAccesoChangeRejected.Operation.CREATE,
                    "UNEXPECTED_ERROR", 500, e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * Actualiza una regla existente dentro del tenant.
     *
     * <p>
     * En caso de rechazo (400/404/409), se publica {@link ReglaAccesoChangeRejected} (best-effort),
     * siempre que se conozca {@code areaId}.
     * </p>
     *
     * @param orgId tenant (obligatorio)
     * @param reglaId id de la regla (obligatorio)
     * @param req payload (obligatorio)
     * @return regla actualizada
     */
    @Transactional
    public ReglaAccesoResponse update(UUID orgId, UUID reglaId, ReglaAccesoUpsertRequest req) {
        requireOrg(orgId, Op.UPDATE);
        if (reglaId == null) {
            precondFailedCounter(Op.UPDATE, "reglaId").increment();
            throw new IllegalArgumentException("reglaId es obligatorio");
        }

        Objects.requireNonNull(req, "req es obligatorio");

        final UUID areaIdForAudit = req.idArea();

        try {
            return timedOp(Op.UPDATE, () -> {
                ReglaAcceso r = getReglaOrThrow(orgId, reglaId);

                Area area = getAreaOrThrow(Op.UPDATE, orgId, req.idArea());
                Dispositivo dispositivo = (req.idDispositivo() != null)
                        ? getDispositivoOrThrow(Op.UPDATE, orgId, req.idDispositivo())
                        : null;

                ZoneId effectiveZone = resolveZoneDefensive(orgId, area.getIdArea());
                LOG.debugf("ReglaAccesoService.update zone=%s orgId=%s areaId=%s reglaId=%s",
                        effectiveZone, orgId, area.getIdArea(), reglaId);

                validateVigenciaUtc(Op.UPDATE, req.validoDesdeUtc(), req.validoHastaUtc());
                LocalTime desdeHora =
                        parseHHmmOrNull(Op.UPDATE, req.desdeHoraLocal(), "desdeHoraLocal");
                LocalTime hastaHora =
                        parseHHmmOrNull(Op.UPDATE, req.hastaHoraLocal(), "hastaHoraLocal");
                validateVentanaDiaria(Op.UPDATE, desdeHora, hastaHora);

                validateDispositivoPerteneceArea(Op.UPDATE, dispositivo, area);

                boolean dup = reglaRepo.existsDuplicateRule(orgId, area.getIdArea(),
                        req.tipoSujeto(), req.idDispositivo(), req.direccionPaso(),
                        req.metodoAutenticacion(), req.accion(), req.validoDesdeUtc(),
                        req.validoHastaUtc(), desdeHora, hastaHora, reglaId);

                if (dup) {
                    duplicateConflicts.increment();
                    throw new WebApplicationException(
                            "Ya existe otra regla equivalente para ese criterio",
                            Response.Status.CONFLICT);
                }

                // Aplicar cambios
                r.setIdArea(area.getIdArea());
                r.setTipoSujeto(req.tipoSujeto());

                r.setIdDispositivo(req.idDispositivo());
                r.setDireccionPaso(req.direccionPaso());
                r.setMetodoAutenticacion(req.metodoAutenticacion());

                r.setAccion(req.accion());

                r.setValidoDesdeUtc(req.validoDesdeUtc());
                r.setValidoHastaUtc(req.validoHastaUtc());

                r.setDesdeHoraLocal(desdeHora);
                r.setHastaHoraLocal(hastaHora);

                if (req.prioridad() != null)
                    r.setPrioridad(req.prioridad());
                r.setMensaje(req.mensaje());

                dbTimer(Op.UPDATE).record(reglaRepo::flush);

                if (req.validoDesdeUtc() != null)
                    featureUsedCounter("vigencia_utc", Op.UPDATE).increment();
                if (req.desdeHoraLocal() != null)
                    featureUsedCounter("daily_window", Op.UPDATE).increment();
                if (req.idDispositivo() != null)
                    featureUsedCounter("device_scoped", Op.UPDATE).increment();

                OffsetDateTime nowUtc = OffsetDateTime.now(clock);
                ReglaAccesoPolicyChanged.ChangeType ct =
                        ReglaAccesoPolicyChanged.ChangeType.UPDATED;
                policyChangedCounter(ct).increment();
                publishTimer(Op.UPDATE).record(() -> eventPublisher.publish(ReglaAccesoPolicyChanged
                        .of(orgId, r.getIdArea(), r.getIdRegla(), ct, nowUtc)));
                return toResponse(r);
            });

        } catch (NotFoundException e) {
            auditReject(orgId, areaIdForAudit, reglaId, ReglaAccesoChangeRejected.Operation.UPDATE,
                    mapReasonCode(404), 404, e.getMessage());
            throw e;

        } catch (WebApplicationException e) {
            int status = statusOf(e, 400);
            auditReject(orgId, areaIdForAudit, reglaId, ReglaAccesoChangeRejected.Operation.UPDATE,
                    mapReasonCode(status), status, e.getMessage());
            throw e;

        } catch (RuntimeException e) {
            auditReject(orgId, areaIdForAudit, reglaId, ReglaAccesoChangeRejected.Operation.UPDATE,
                    "UNEXPECTED_ERROR", 500, e.getMessage());
            throw e;
        }
    }


    // =========================================================================
    // CHANGE STATUS
    // =========================================================================

    /**
     * Cambia el estado de la regla (ACTIVA/INACTIVA).
     *
     * <p>
     * En caso de rechazo (400/404), se publica {@link ReglaAccesoChangeRejected} (best-effort)
     * siempre que se conozca {@code areaId}. Para {@code NotFoundException} (regla inexistente),
     * típicamente no se conoce área y se omitirá (por contrato del evento).
     * </p>
     *
     * @param orgId tenant (obligatorio)
     * @param reglaId id regla (obligatorio)
     * @param req payload con estado (obligatorio)
     * @return regla actualizada
     */
    @Transactional
    public ReglaAccesoResponse changeEstado(UUID orgId, UUID reglaId,
            ReglaAccesoEstadoRequest req) {
        requireOrg(orgId, Op.CHANGE_ESTADO);
        if (reglaId == null) {
            precondFailedCounter(Op.CHANGE_ESTADO, "reglaId").increment();
            throw new IllegalArgumentException("reglaId es obligatorio");
        }

        Objects.requireNonNull(req, "req es obligatorio");

        final java.util.concurrent.atomic.AtomicReference<UUID> areaIdForAudit =
                new java.util.concurrent.atomic.AtomicReference<>(null);

        try {
            return timedOp(Op.CHANGE_ESTADO, () -> {
                ReglaAcceso r = getReglaOrThrow(orgId, reglaId);
                areaIdForAudit.set(r.getIdArea());

                EstadoReglaAcceso before = r.getEstado();
                EstadoReglaAcceso after = req.estado();

                r.setEstado(after);
                dbTimer(Op.CHANGE_ESTADO).record(reglaRepo::flush);

                if (before != after) {
                    ReglaAccesoPolicyChanged.ChangeType ct = mapEstadoChange(before, after);
                    if (ct != null) {
                        OffsetDateTime nowUtc = OffsetDateTime.now(clock);
                        policyChangedCounter(ct).increment();
                        publishTimer(Op.CHANGE_ESTADO)
                                .record(() -> eventPublisher.publish(ReglaAccesoPolicyChanged
                                        .of(orgId, r.getIdArea(), r.getIdRegla(), ct, nowUtc)));
                    }
                }
                return toResponse(r);
            });

        } catch (NotFoundException e) {
            auditReject(orgId, areaIdForAudit.get(), reglaId,
                    ReglaAccesoChangeRejected.Operation.CHANGE_ESTADO, mapReasonCode(404), 404,
                    e.getMessage());
            throw e;

        } catch (WebApplicationException e) {
            int status = statusOf(e, 400);
            auditReject(orgId, areaIdForAudit.get(), reglaId,
                    ReglaAccesoChangeRejected.Operation.CHANGE_ESTADO, mapReasonCode(status),
                    status, e.getMessage());
            throw e;

        } catch (RuntimeException e) {
            auditReject(orgId, areaIdForAudit.get(), reglaId,
                    ReglaAccesoChangeRejected.Operation.CHANGE_ESTADO, "UNEXPECTED_ERROR", 500,
                    e.getMessage());
            throw e;
        }
    }



    // =========================================================================
    // DELETE (soft)
    // =========================================================================

    /**
     * “Elimina” una regla vía soft-delete (INACTIVA).
     *
     * <p>
     * En caso de rechazo 404 (regla inexistente), no hay área conocida y no se audita (por contrato
     * del evento). Para rechazos posteriores a cargar la regla (p.ej. validaciones internas), se
     * audita usando el {@code areaId} de la regla.
     * </p>
     *
     * @param orgId tenant (obligatorio)
     * @param reglaId id regla (obligatorio)
     */
    @Transactional
    public void delete(UUID orgId, UUID reglaId) {
        timedOp(Op.DELETE, () -> {
            requireOrg(orgId, Op.DELETE);
            if (reglaId == null) {
                precondFailedCounter(Op.DELETE, "reglaId").increment();
                throw new IllegalArgumentException("reglaId es obligatorio");
            }
            UUID areaIdForAudit = null;

            try {
                ReglaAcceso r = getReglaOrThrow(orgId, reglaId);
                areaIdForAudit = r.getIdArea();

                r.setEstado(EstadoReglaAcceso.INACTIVA);
                dbTimer(Op.DELETE).record(reglaRepo::flush);

                OffsetDateTime nowUtc = OffsetDateTime.now(clock);
                ReglaAccesoPolicyChanged.ChangeType ct =
                        ReglaAccesoPolicyChanged.ChangeType.SOFT_DELETED;

                policyChangedCounter(ct).increment();
                publishTimer(Op.DELETE).record(() -> eventPublisher.publish(ReglaAccesoPolicyChanged
                        .of(orgId, r.getIdArea(), r.getIdRegla(), ct, nowUtc)));

                return null;

            } catch (NotFoundException e) {
                auditReject(orgId, null, reglaId, ReglaAccesoChangeRejected.Operation.DELETE,
                        mapReasonCode(404), 404, e.getMessage());
                throw e;

            } catch (WebApplicationException e) {
                int status = statusOf(e, 400);
                auditReject(orgId, areaIdForAudit, reglaId,
                        ReglaAccesoChangeRejected.Operation.DELETE, mapReasonCode(status), status,
                        e.getMessage());
                throw e;

            } catch (RuntimeException e) {
                auditReject(orgId, areaIdForAudit, reglaId,
                        ReglaAccesoChangeRejected.Operation.DELETE, "UNEXPECTED_ERROR", 500,
                        e.getMessage());
                throw e;
            }
        });
    }

    // =========================================================================
    // Auditoría de rechazos (helpers)
    // =========================================================================

    /**
     * Publica un evento de auditoría de rechazo de forma <b>best-effort</b>.
     *
     * <p>
     * Este método <b>nunca</b> debe propagar excepciones. Si la emisión falla, se registra en DEBUG
     * y se continúa para no afectar el flujo principal.
     * </p>
     *
     * <p>
     * Dado que {@link ReglaAccesoChangeRejected} exige {@code areaId} no-null, si {@code areaId} no
     * se conoce en el punto del rechazo, la auditoría se omite (se loguea en DEBUG).
     * </p>
     *
     * @param orgId tenant (obligatorio)
     * @param areaId área (obligatorio para auditar; si es null se omite)
     * @param reglaId regla (opcional)
     * @param op operación (obligatorio)
     * @param reasonCode código estable (obligatorio)
     * @param httpStatus estatus HTTP (obligatorio)
     * @param message mensaje human-readable (sanitizado)
     */
    private void auditReject(UUID orgId, UUID areaId, UUID reglaId,
            ReglaAccesoChangeRejected.Operation op, String reasonCode, int httpStatus,
            String message) {

        try {

            boolean canAudit = (orgId != null && areaId != null && op != null && reasonCode != null
                    && !reasonCode.isBlank());
            ruleRejectsCounter(mapOp(op), reasonCode, canAudit).increment();

            if (orgId == null) {
                return;
            }

            if (areaId == null) {
                LOG.debugf(
                        "audit_reject_skip missing areaId orgId=%s op=%s reglaId=%s status=%s code=%s",
                        orgId, op, reglaId, httpStatus, reasonCode);
                return;
            }
            if (op == null) {
                LOG.debugf("audit_reject_skip missing op orgId=%s areaId=%s reglaId=%s", orgId,
                        areaId, reglaId);
                return;
            }
            if (reasonCode == null || reasonCode.isBlank()) {
                LOG.debugf(
                        "audit_reject_skip missing reasonCode orgId=%s areaId=%s reglaId=%s op=%s",
                        orgId, areaId, reglaId, op);
                return;
            }

            OffsetDateTime nowUtc = OffsetDateTime.now(clock);
            eventPublisher.publish(ReglaAccesoChangeRejected.of(orgId, areaId, reglaId, op,
                    reasonCode, httpStatus, safeMsg(message), nowUtc));

        } catch (Exception e) {
            LOG.debugf(e,
                    "audit_reject_publish_failed (ignored) orgId=%s areaId=%s reglaId=%s op=%s",
                    orgId, areaId, reglaId, op);
        }
    }

    /**
     * Traduce un status HTTP a un {@code reasonCode} estable.
     *
     * <p>
     * Si necesitas granularidad por validación específica, lo ideal es levantar excepciones
     * especializadas (p.ej. {@code DuplicateRuleException}) y mapearlas aquí sin depender del
     * texto.
     * </p>
     */
    private static String mapReasonCode(int status) {
        if (status == 409)
            return "DUPLICATE_RULE";
        if (status == 404)
            return "NOT_FOUND";
        if (status == 400)
            return "VALIDATION_ERROR";
        if (status >= 500)
            return "UNEXPECTED_ERROR";
        return "WEB_ERROR";
    }


    /**
     * Extrae el status de un {@link WebApplicationException}.
     */
    private static int statusOf(WebApplicationException e, int defaultStatus) {
        try {
            Response r = e.getResponse();
            return (r != null) ? r.getStatus() : defaultStatus;
        } catch (Exception ignore) {
            return defaultStatus;
        }
    }

    /**
     * Sanitiza mensajes para auditoría (evita payloads gigantes).
     */
    private static String safeMsg(String s) {
        if (s == null)
            return null;
        String v = s.trim();
        if (v.isBlank())
            return null;
        return v.length() <= 180 ? v : v.substring(0, 180);
    }

    // =========================================================================
    // Helpers (tenant-safe + validaciones)
    // =========================================================================

    private void requireOrg(UUID orgId, Op op) {
        if (orgId == null) {
            precondFailedCounter(op, "orgId").increment();
            throw new IllegalArgumentException("orgId es obligatorio");
        }
    }

    private void requirePaging(int page, int size, Op op) {
        if (page < 0) {
            precondFailedCounter(op, "page").increment();
            throw new IllegalArgumentException("page debe ser >= 0");
        }
        if (size <= 0) {
            precondFailedCounter(op, "size").increment();
            throw new IllegalArgumentException("size debe ser > 0");
        }
        if (size > 200) {
            precondFailedCounter(op, "size").increment();
            throw new IllegalArgumentException("size no debe exceder 200");
        }
    }

    private ReglaAcceso getReglaOrThrow(UUID orgId, UUID reglaId) {
        return reglaRepo.findById(reglaId).filter(r -> orgId.equals(r.getIdOrganizacion()))
                .orElseThrow(() -> new NotFoundException("Regla de acceso no encontrada"));
    }

    private Area getAreaOrThrow(Op op, UUID orgId, UUID idArea) {
        if (idArea == null) {
            precondFailedCounter(op, "idArea").increment();
            throw new IllegalArgumentException("idArea es obligatorio");
        }
        return areaRepo.findByIdAndOrganizacion(idArea, orgId)
                .orElseThrow(() -> new NotFoundException("Área no encontrada"));
    }

    private Dispositivo getDispositivoOrThrow(Op op, UUID orgId, UUID idDispositivo) {
        if (idDispositivo == null) {
            precondFailedCounter(op, "idDispositivo").increment();
            throw new IllegalArgumentException("idDispositivo es obligatorio");
        }
        return dispositivoRepo.findByIdAndOrganizacion(idDispositivo, orgId)
                .orElseThrow(() -> new NotFoundException("Dispositivo no encontrado"));
    }

    // ===============================
    // Helpers de métricas (único punto)
    // ===============================

    private Counter featureUsedCounter(String feature, Op op) {
        String f = (feature == null || feature.isBlank()) ? "unknown" : feature;
        return cachedCounter(M_RULE_FEATURE_USED_TOTAL, "feature", f, "op", op.tag);
    }

    private Counter validationFailedCounter(Op op, String check) {
        String c = (check == null || check.isBlank()) ? "unknown" : check;
        return cachedCounter(M_RULE_VALIDATION_FAILED_TOTAL, "op", op.tag, "check", c);
    }

    private Counter precondFailedCounter(Op op, String field) {
        String f = (field == null || field.isBlank()) ? "unknown" : field;
        return cachedCounter(M_RULE_PRECONDITION_FAILED_TOTAL, "op", op.tag, "field", f);
    }

    // helper para mapear ReglaAccesoChangeRejected.Operation a Op (tags consistentes)
    private static Op mapOp(ReglaAccesoChangeRejected.Operation op) {
        if (op == null)
            return Op.UNKNOWN; // default defensivo
        return switch (op) {
            case CREATE -> Op.CREATE;
            case UPDATE -> Op.UPDATE;
            case CHANGE_ESTADO -> Op.CHANGE_ESTADO;
            case DELETE -> Op.DELETE;
        };
    }

    private Timer opTimer(Op op, Result result) {
        String key = M_RULE_OP_SECONDS + "|op=" + op.tag + "|result=" + result.tag;

        return timerCache.computeIfAbsent(key,
                k -> Timer.builder(M_RULE_OP_SECONDS).tag("op", op.tag).tag("result", result.tag)
                        .publishPercentileHistogram(true)
                        // Nota: los percentiles "directos" en Prometheus no siempre los usarás,
                        // pero no molestan si te sirven localmente.
                        .publishPercentiles(0.5, 0.95, 0.99).register(registry));
    }

    private Timer dbTimer(Op op) {
        return timerCache.computeIfAbsent("db|op=" + op.tag,
                k -> Timer.builder("access_rule_db_seconds").tag("op", op.tag)
                        .publishPercentileHistogram(true).register(registry));
    }

    private Timer publishTimer(Op op) {
        return timerCache.computeIfAbsent("pub|op=" + op.tag,
                k -> Timer.builder("access_rule_event_publish_seconds").tag("op", op.tag)
                        .publishPercentileHistogram(true).register(registry));
    }



    private enum Op {
        LIST("list"), GET("get"), CREATE("create"), UPDATE("update"), CHANGE_ESTADO(
                "change_estado"), DELETE("delete"), UNKNOWN("unknown");

        final String tag;

        Op(String tag) {
            this.tag = tag;
        }
    }

    private enum Result {
        OK("ok"), NOT_FOUND("not_found"), BAD_REQUEST("bad_request"), CONFLICT("conflict"), ERROR(
                "error");

        final String tag;

        Result(String tag) {
            this.tag = tag;
        }
    }

    private Counter ruleOpsCounter(Op op, Result result) {
        return cachedCounter(M_RULE_OPS_TOTAL, "op", op.tag, "result", result.tag);
    }

    private Counter cachedCounter(String name, String... tags) {
        StringBuilder key = new StringBuilder(name);
        for (int i = 0; i < tags.length; i += 2) {
            key.append('|').append(tags[i]).append('=').append(tags[i + 1]);
        }
        return counterCache.computeIfAbsent(key.toString(),
                k -> Counter.builder(name).tags(tags).register(registry));
    }


    /**
     * Rechazos: audited=true/false, con reasonCode estable. reasonCode recomendado: NOT_FOUND /
     * VALIDATION_ERROR / DUPLICATE_RULE / UNEXPECTED_ERROR / WEB_ERROR
     */
    private Counter ruleRejectsCounter(Op op, String reasonCode, boolean audited) {
        String rc = (reasonCode == null || reasonCode.isBlank()) ? "UNKNOWN" : reasonCode;
        return cachedCounter(M_RULE_REJECTS_TOTAL, "op", op.tag, "reason", rc, "audited",
                Boolean.toString(audited));
    }

    /** Eventos de policy change emitidos. */
    private Counter policyChangedCounter(ReglaAccesoPolicyChanged.ChangeType type) {
        String t = (type != null) ? type.name() : "UNKNOWN";
        return cachedCounter(M_RULE_POLICY_CHANGED_TOTAL, "type", t);
    }

    /** Ejecuta el bloque y siempre registra timer+counter ok/error según corresponda. */
    private <T> T timedOp(Op op, java.util.concurrent.Callable<T> body) {
        final Timer.Sample sample = Timer.start(registry);

        Result result = Result.OK;
        try {
            T out = body.call();
            ruleOpsCounter(op, Result.OK).increment();
            return out;

        } catch (NotFoundException e) {
            result = Result.NOT_FOUND;
            ruleOpsCounter(op, result).increment();
            throw e;

        } catch (WebApplicationException e) {
            int status = statusOf(e, 400);

            // Mapeo un poco más fino de status -> result
            if (status == 409)
                result = Result.CONFLICT;
            else if (status == 404)
                result = Result.NOT_FOUND;
            else if (status >= 500)
                result = Result.ERROR;
            else
                result = Result.BAD_REQUEST;

            ruleOpsCounter(op, result).increment();
            throw e;

        } catch (RuntimeException e) {
            result = Result.ERROR;
            ruleOpsCounter(op, result).increment();
            throw e;

        } catch (Exception e) {
            result = Result.ERROR;
            ruleOpsCounter(op, result).increment();
            throw new RuntimeException(e);

        } finally {
            // Timer por op + result (nuevo)
            sample.stop(opTimer(op, result));
        }
    }

    /**
     * Resuelve la zona efectiva de forma defensiva.
     *
     * <p>
     * Útil para detectar configuraciones inválidas de timezone en DB y mejorar trazabilidad.
     * </p>
     */
    private ZoneId resolveZoneDefensive(UUID orgId, UUID areaId) {
        try {
            ZoneId z = zoneProvider.zoneFor(orgId, areaId);
            if (z == null) {
                zoneFallback.increment();
                return ZoneId.of("UTC");
            }
            return z;
        } catch (RuntimeException e) {
            zoneFallback.increment();
            LOG.warnf(e, "No se pudo resolver zona efectiva orgId=%s areaId=%s. Usando UTC.", orgId,
                    areaId);
            return ZoneId.of("UTC");
        }
    }

    /**
     * Verifica consistencia: si se especifica dispositivo, debe pertenecer al área.
     */
    private void validateDispositivoPerteneceArea(Op op, Dispositivo dispositivo, Area area) {
        if (dispositivo == null)
            return;

        if (dispositivo.getIdArea() != null && area != null && area.getIdArea() != null
                && !dispositivo.getIdArea().equals(area.getIdArea())) {
            validationFailedCounter(op, "device_area_mismatch").increment();
            throw new WebApplicationException("El dispositivo no pertenece al área indicada",
                    Response.Status.BAD_REQUEST);
        }
    }

    /**
     * Valida ventana de vigencia UTC:
     * <ul>
     * <li>ambos null => OK</li>
     * <li>solo uno null => 400</li>
     * <li>desde >= hasta => 400</li>
     * </ul>
     */
    private void validateVigenciaUtc(Op op, OffsetDateTime desde, OffsetDateTime hasta) {
        if (desde == null && hasta == null)
            return;

        if (desde == null || hasta == null) {
            validationFailedCounter(op, "vigencia_missing_pair").increment();
            throw new WebApplicationException(
                    "validoDesdeUtc y validoHastaUtc deben venir ambos o ninguno",
                    Response.Status.BAD_REQUEST);
        }
        if (!desde.isBefore(hasta)) {
            validationFailedCounter(op, "vigencia_range_invalid").increment();
            throw new WebApplicationException(
                    "validoDesdeUtc debe ser estrictamente menor que validoHastaUtc",
                    Response.Status.BAD_REQUEST);
        }
    }

    private LocalTime parseHHmmOrNull(Op op, String v, String field) {
        if (v == null)
            return null;
        String s = v.trim();
        if (s.isBlank())
            return null;

        try {
            return LocalTime.parse(s, HHMM);
        } catch (DateTimeParseException e) {
            validationFailedCounter(op, "hhmm_format").increment();
            throw new WebApplicationException(field + " debe tener formato HH:mm",
                    Response.Status.BAD_REQUEST);
        }
    }

    private void validateVentanaDiaria(Op op, LocalTime desde, LocalTime hasta) {
        if (desde == null && hasta == null)
            return;

        if (desde == null || hasta == null) {
            validationFailedCounter(op, "daily_window_missing_pair").increment();
            throw new WebApplicationException(
                    "desdeHoraLocal y hastaHoraLocal deben venir ambos o ninguno",
                    Response.Status.BAD_REQUEST);
        }
        if (desde.equals(hasta)) {
            validationFailedCounter(op, "daily_window_equal").increment();
            throw new WebApplicationException(
                    "desdeHoraLocal y hastaHoraLocal no pueden ser iguales",
                    Response.Status.BAD_REQUEST);
        }
        // Overnight permitido, no exigimos desde < hasta
    }

    // =========================================================================
    // Mapper
    // =========================================================================

    /**
     * Mapea entidad a DTO de respuesta.
     *
     * <p>
     * Se exponen IDs para evitar inicializar relaciones LAZY.
     * </p>
     */
    private ReglaAccesoResponse toResponse(ReglaAcceso r) {
        return new ReglaAccesoResponse(r.getIdRegla(), r.getIdOrganizacion(), r.getIdArea(),
                r.getIdDispositivo(), r.getTipoSujeto(), r.getDireccionPaso(),
                r.getMetodoAutenticacion(), r.getAccion(), r.getValidoDesdeUtc(),
                r.getValidoHastaUtc(),
                r.getDesdeHoraLocal() != null ? r.getDesdeHoraLocal().format(HHMM) : null,
                r.getHastaHoraLocal() != null ? r.getHastaHoraLocal().format(HHMM) : null,
                r.getPrioridad(), r.getEstado(), r.getMensaje(), r.getCreadoEnUtc(),
                r.getActualizadoEnUtc());
    }

    private static ReglaAccesoPolicyChanged.ChangeType mapEstadoChange(EstadoReglaAcceso before,
            EstadoReglaAcceso after) {
        if (before == null || after == null)
            return ReglaAccesoPolicyChanged.ChangeType.UPDATED;

        if (before != EstadoReglaAcceso.ACTIVA && after == EstadoReglaAcceso.ACTIVA) {
            return ReglaAccesoPolicyChanged.ChangeType.ACTIVATED;
        }
        if (before == EstadoReglaAcceso.ACTIVA && after != EstadoReglaAcceso.ACTIVA) {
            return ReglaAccesoPolicyChanged.ChangeType.INACTIVATED;
        }
        return ReglaAccesoPolicyChanged.ChangeType.UPDATED;
    }
}
