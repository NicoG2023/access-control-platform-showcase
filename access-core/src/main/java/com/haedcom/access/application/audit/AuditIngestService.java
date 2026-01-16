package com.haedcom.access.application.audit;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haedcom.access.domain.events.ComandoDispositivoEjecutado;
import com.haedcom.access.domain.events.ComandoDispositivoEmitido;
import com.haedcom.access.domain.events.DecisionAccesoTomada;
import com.haedcom.access.domain.events.IntentoAccesoRegistrado;
import com.haedcom.access.domain.events.ReglaAccesoChangeRejected;
import com.haedcom.access.domain.events.ReglaAccesoPolicyChanged;
import com.haedcom.access.domain.model.AuditLog;
import com.haedcom.access.domain.repo.AuditLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuditIngestService {

    private static final Logger LOG = Logger.getLogger(AuditIngestService.class);

    /** Debe coincidir con tu constraint UNIQUE */
    private static final String UX_AUDIT_LOG_ORG_EVENT_KEY = "ux_audit_log_org_event_key";

    private final AuditLogRepository auditRepo;
    private final ObjectMapper objectMapper;

    public AuditIngestService(AuditLogRepository auditRepo, ObjectMapper objectMapper) {
        this.auditRepo = Objects.requireNonNull(auditRepo);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void ingest(Object ev) {
        Objects.requireNonNull(ev, "evento es obligatorio");

        OffsetDateTime when = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        String eventType = ev.getClass().getSimpleName();
        String aggregateType = null;
        String aggregateId = null;
        String correlationId = null;
        String payloadJson = null;
        UUID orgId = null;

        if (ev instanceof ComandoDispositivoEjecutado e) {
            orgId = e.orgId();
            aggregateType = "ComandoDispositivo";
            aggregateId = safeToString(e.idComando());
            correlationId = safeToString(e.idIntento());
            payloadJson = toPayloadJson(payloadComandoEjecutado(e));
            when = e.ejecutadoEnUtc() != null ? e.ejecutadoEnUtc() : when;

        } else if (ev instanceof IntentoAccesoRegistrado e) {
            orgId = e.orgId();
            aggregateType = "IntentoAcceso";
            aggregateId = safeToString(e.idIntento());
            correlationId = e.claveIdempotencia();
            payloadJson = toPayloadJson(payloadIntento(e));
            when = e.ocurridoEnUtc() != null ? e.ocurridoEnUtc() : when;

        } else if (ev instanceof DecisionAccesoTomada e) {
            orgId = e.orgId();
            aggregateType = "DecisionAcceso";
            aggregateId = safeToString(e.idDecision());
            correlationId = safeToString(e.idIntento());
            payloadJson = toPayloadJson(payloadDecision(e));
            when = e.decididoEnUtc() != null ? e.decididoEnUtc() : when;

        } else if (ev instanceof ComandoDispositivoEmitido e) {
            orgId = e.orgId();
            aggregateType = "ComandoDispositivo";
            aggregateId = safeToString(e.idComando());
            correlationId = safeToString(e.idIntento());
            payloadJson = toPayloadJson(payloadComando(e));
            when = e.enviadoEnUtc() != null ? e.enviadoEnUtc() : when;

        } else if (ev instanceof ReglaAccesoPolicyChanged e) {
            orgId = e.orgId();
            aggregateType = "ReglaAcceso";
            aggregateId = safeToString(e.idRegla());
            correlationId = safeToString(e.areaId());
            payloadJson = toPayloadJson(payloadReglaPolicyChanged(e));
            when = e.occurredAtUtc() != null ? e.occurredAtUtc() : when;

        } else if (ev instanceof ReglaAccesoChangeRejected e) { // NUEVO
            orgId = e.orgId();
            aggregateType = "ReglaAcceso";
            aggregateId = safeToString(e.reglaId()); // puede ser null en create
            correlationId = safeToString(e.areaId());
            payloadJson = toPayloadJson(payloadReglaRejected(e));
            when = e.occurredAtUtc() != null ? e.occurredAtUtc() : when;
        }

        // Si no reconocimos el evento, evita NPE y no escribas basura
        if (orgId == null) {
            LOG.debugf("audit_skip_unrecognized eventType=%s", eventType);
            return;
        }

        // ✅ Recomendación práctica: eventKey estable por ID/eventId cuando exista
        String eventKey = eventKeyFor(ev, orgId, eventType, aggregateId, when);

        // Dedupe rápido
        if (auditRepo.existsByEventKey(orgId, eventKey)) {
            LOG.debugf("audit_deduped orgId=%s aggregateId=%s eventKey=%s", orgId, aggregateId,
                    eventKey);
            return;
        }

        try {
            AuditLog log = AuditLog.crear(orgId, eventType, aggregateType, aggregateId,
                    correlationId, when, payloadJson);

            // Fuerza usar la key robusta
            log.setEventKey(eventKey);

            auditRepo.persist(log);
            auditRepo.flush();

        } catch (Exception e) {
            if (isLikelyUniqueEventKeyViolation(e)) {
                LOG.debugf("audit_deduped_race orgId=%s aggregateId=%s eventKey=%s", orgId,
                        aggregateId, eventKey);
                return;
            }
            LOG.errorf(e, "audit_ingest_failed orgId=%s aggregateId=%s eventKey=%s", orgId,
                    aggregateId, eventKey);
        }
    }

    /**
     * EventKey robusto: - Si el evento trae un ID propio (eventId) => úsalo - Si el evento tiene
     * aggregateId único por evento => úsalo - Evita timestamps en la identidad cuando no son
     * necesarios
     */
    private String eventKeyFor(Object ev, UUID orgId, String eventType, String aggregateId,
            OffsetDateTime when) {

        // PolicyChanged: eventId único perfecto
        if (ev instanceof ReglaAccesoPolicyChanged e) {
            return orgId + "|ReglaAccesoPolicyChanged|" + e.eventId();
        }

        // Rechazos: también con eventId (lo diseñamos así)
        if (ev instanceof ReglaAccesoChangeRejected e) {
            return orgId + "|ReglaAccesoChangeRejected|" + e.eventId();
        }

        // Eventos con IDs “fuertes” (un evento lógico = un id)
        if (ev instanceof IntentoAccesoRegistrado e) {
            return orgId + "|IntentoAccesoRegistrado|" + e.idIntento();
        }
        if (ev instanceof DecisionAccesoTomada e) {
            return orgId + "|DecisionAccesoTomada|" + e.idDecision();
        }
        if (ev instanceof ComandoDispositivoEmitido e) {
            return orgId + "|ComandoDispositivoEmitido|" + e.idComando();
        }

        if (ev instanceof ComandoDispositivoEjecutado e) {
            if (e.eventId() != null) {
                return orgId + "|ComandoDispositivoEjecutado|" + e.eventId();
            }
            String ts = (e.ejecutadoEnUtc() != null) ? e.ejecutadoEnUtc().toInstant().toString()
                    : "NO_TS";
            return orgId + "|ComandoDispositivoEjecutado|" + e.idComando() + "|" + ts;
        }


        // fallback (tu método original)
        return AuditLog.buildEventKey(orgId, eventType, aggregateId, when);
    }

    // ---------------- Payloads ----------------

    private Map<String, Object> payloadComandoEjecutado(ComandoDispositivoEjecutado ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", safeToString(ev.eventId()));
        m.put("orgId", safeToString(ev.orgId()));
        m.put("idComando", safeToString(ev.idComando()));
        m.put("idIntento", safeToString(ev.idIntento()));
        m.put("idDispositivo", safeToString(ev.idDispositivo()));
        m.put("estadoFinal", ev.estadoFinal() != null ? ev.estadoFinal().name() : null);
        m.put("ejecutadoEnUtc", ev.ejecutadoEnUtc());
        m.put("codigoError", safeTruncate(ev.codigoError(), 60));
        m.put("detalleError", safeTruncate(ev.detalleError(), 250));
        m.put("idEjecucionExterna", safeTruncate(ev.idEjecucionExterna(), 120));
        return m;
    }

    private Map<String, Object> payloadIntento(IntentoAccesoRegistrado ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orgId", safeToString(ev.orgId()));
        m.put("idIntento", safeToString(ev.idIntento()));
        m.put("idDispositivo", safeToString(ev.idDispositivo()));
        m.put("idArea", safeToString(ev.idArea()));
        m.put("direccionPaso", ev.direccionPaso() != null ? ev.direccionPaso().name() : null);
        m.put("metodoAutenticacion",
                ev.metodoAutenticacion() != null ? ev.metodoAutenticacion().name() : null);
        m.put("tipoSujeto", ev.tipoSujeto() != null ? ev.tipoSujeto().name() : null);
        m.put("claveIdempotencia", safeTruncate(ev.claveIdempotencia(), 120));
        m.put("ocurridoEnUtc", ev.ocurridoEnUtc());
        return m;
    }

    private Map<String, Object> payloadDecision(DecisionAccesoTomada ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orgId", safeToString(ev.orgId()));
        m.put("idDecision", safeToString(ev.idDecision()));
        m.put("idIntento", safeToString(ev.idIntento()));
        m.put("resultado", ev.resultado() != null ? ev.resultado().name() : null);
        m.put("codigoMotivo", safeTruncate(ev.codigoMotivo(), 120));
        m.put("detalleMotivo", safeTruncate(ev.detalleMotivo(), 250));
        m.put("decididoEnUtc", ev.decididoEnUtc());
        m.put("expiraEnUtc", ev.expiraEnUtc());
        return m;
    }

    private Map<String, Object> payloadComando(ComandoDispositivoEmitido ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orgId", safeToString(ev.orgId()));
        m.put("idComando", safeToString(ev.idComando()));
        m.put("idIntento", safeToString(ev.idIntento()));
        m.put("idDispositivo", safeToString(ev.idDispositivo()));
        m.put("comando", ev.comando() != null ? ev.comando().name() : null);
        m.put("estado", ev.estado() != null ? ev.estado().name() : null);
        m.put("mensaje", safeTruncate(ev.mensaje(), 120));
        m.put("claveIdempotencia", safeTruncate(ev.claveIdempotencia(), 120));
        m.put("enviadoEnUtc", ev.enviadoEnUtc());
        return m;
    }

    private Map<String, Object> payloadReglaPolicyChanged(ReglaAccesoPolicyChanged ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", safeToString(ev.eventId()));
        m.put("orgId", safeToString(ev.orgId()));
        m.put("areaId", safeToString(ev.areaId()));
        m.put("idRegla", safeToString(ev.idRegla()));
        m.put("changeType", ev.changeType() != null ? ev.changeType().name() : null);
        m.put("occurredAtUtc", ev.occurredAtUtc());
        return m;
    }

    private Map<String, Object> payloadReglaRejected(ReglaAccesoChangeRejected ev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", safeToString(ev.eventId()));
        m.put("orgId", safeToString(ev.orgId()));
        m.put("areaId", safeToString(ev.areaId()));
        m.put("reglaId", safeToString(ev.reglaId()));
        m.put("operation", ev.operation() != null ? ev.operation().name() : null);
        m.put("reasonCode", safeTruncate(ev.reasonCode(), 80));
        m.put("httpStatus", ev.httpStatus());
        m.put("message", safeTruncate(ev.message(), 180));
        m.put("occurredAtUtc", ev.occurredAtUtc());
        return m;
    }

    private String toPayloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"serializationError\":true}";
        }
    }

    private boolean isLikelyUniqueEventKeyViolation(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains(UX_AUDIT_LOG_ORG_EVENT_KEY.toLowerCase())
                        || (m.contains("unique") && m.contains("event_key"))
                        || (m.contains("duplicate") && m.contains("event_key"))) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static String safeToString(Object o) {
        return (o == null) ? null : o.toString();
    }

    private static String safeTruncate(String s, int max) {
        if (s == null)
            return null;
        String v = s.trim();
        if (v.isEmpty())
            return null;
        return v.length() <= max ? v : v.substring(0, max);
    }
}
