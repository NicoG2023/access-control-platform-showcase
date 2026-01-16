package com.haedcom.access.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Registro de auditoría funcional (Nivel B).
 *
 * <p>
 * Persiste eventos de dominio relevantes para trazabilidad y cumplimiento: intentos de acceso,
 * decisiones tomadas y comandos emitidos.
 * </p>
 *
 * <h2>Objetivo</h2>
 * <ul>
 * <li>Auditoría independiente del modelo transaccional principal.</li>
 * <li>Trazabilidad end-to-end por tenant (orgId).</li>
 * <li>Soporte para investigación y diagnóstico post-mortem.</li>
 * </ul>
 *
 * <h2>Diseño</h2>
 * <ul>
 * <li>Entidad simple (append-only recomendado).</li>
 * <li>{@code payloadJson} contiene un snapshot sanitizado del evento.</li>
 * <li>{@code correlationId} permite correlacionar request/gateway/idempotencia.</li>
 * <li>{@code eventKey} soporta idempotencia de auditoría (deduplicación).</li>
 * </ul>
 *
 * <p>
 * Recomendación: evita actualizar filas de auditoría; si necesitas “correcciones”, agrega un evento
 * compensatorio.
 * </p>
 */
@Entity
@Table(name = "audit_log",
        uniqueConstraints = {@UniqueConstraint(name = "ux_audit_log_org_event_key",
                columnNames = {"id_organizacion", "event_key"})},
        indexes = {
                @Index(name = "ix_audit_log_org_occurred",
                        columnList = "id_organizacion, occurred_at_utc"),
                @Index(name = "ix_audit_log_org_correlation",
                        columnList = "id_organizacion, correlation_id"),
                @Index(name = "ix_audit_log_org_aggregate",
                        columnList = "id_organizacion, aggregate_type, aggregate_id")})
public class AuditLog extends TenantOnlyEntity {

    @Id
    @Column(name = "id_audit", nullable = false)
    private UUID idAudit;

    /**
     * Clave idempotente del evento auditado.
     *
     * <p>
     * Permite deduplicar inserciones cuando hay reintentos (replays, retries, etc.). La
     * recomendación es agregar constraint UNIQUE por tenant:
     * {@code UNIQUE(id_organizacion, event_key)}.
     * </p>
     */
    @Column(name = "event_key", nullable = false, length = 220)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "aggregate_type", nullable = true, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = true, length = 60)
    private String aggregateId;

    @Column(name = "correlation_id", nullable = true, length = 120)
    private String correlationId;

    @Column(name = "occurred_at_utc", nullable = false)
    private OffsetDateTime occurredAtUtc;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private OffsetDateTime createdAtUtc;

    protected AuditLog() {
        // Requerido por JPA
    }

    /**
     * Factory method para construir un registro de auditoría.
     *
     * <p>
     * Este método calcula {@code eventKey} de forma estable para deduplicación.
     * </p>
     *
     * @param orgId tenant (obligatorio)
     * @param eventType tipo de evento (obligatorio)
     * @param aggregateType tipo agregado (opcional)
     * @param aggregateId id agregado (opcional)
     * @param correlationId correlación (opcional)
     * @param occurredAtUtc instante del evento (obligatorio)
     * @param payloadJson json sanitizado (obligatorio)
     * @return entidad lista para persistir
     */
    public static AuditLog crear(UUID orgId, String eventType, String aggregateType,
            String aggregateId, String correlationId, OffsetDateTime occurredAtUtc,
            String payloadJson) {

        AuditLog a = new AuditLog();
        a.setIdAudit(UUID.randomUUID());
        a.assignTenant(orgId);

        a.setEventType(eventType);
        a.setAggregateType(aggregateType);
        a.setAggregateId(aggregateId);
        a.setCorrelationId(correlationId);
        a.setOccurredAtUtc(occurredAtUtc);
        a.setPayloadJson(payloadJson);

        // eventKey estable (idempotencia): orgId + eventType + aggregateId + occurredAtUtc
        a.setEventKey(buildEventKey(orgId, eventType, aggregateId, occurredAtUtc));

        return a;
    }

    /**
     * Construye una clave idempotente estable para deduplicación de auditoría.
     *
     * <p>
     * Esta clave se usa para evitar insertar duplicados del <b>mismo evento lógico</b> dentro de un
     * tenant cuando hay reintentos (replays, retries, fallos transitorios, etc.).
     * </p>
     *
     * <h2>Contrato</h2>
     * <ul>
     * <li>Debe ser <b>determinística</b>: mismo input → misma salida.</li>
     * <li>Debe ser <b>estable</b>: cambios de formato romperían dedupe en datos históricos.</li>
     * <li>Se recomienda aplicar una restricción {@code UNIQUE(id_organizacion, event_key)}.</li>
     * </ul>
     *
     * <h2>Campos usados</h2>
     * <p>
     * El esquema recomendado es:
     * {@code orgId + "|" + eventType + "|" + aggregateId + "|" + occurredAtUtcInstant}.
     * </p>
     *
     * <p>
     * Nota: en este diseño se asume que {@code aggregateId} (por ejemplo {@code idIntento},
     * {@code idDecision} o {@code idComando}) está presente para los eventos auditados. Si en el
     * futuro necesitas soportar eventos sin {@code aggregateId}, define explícitamente una
     * estrategia alternativa (por ejemplo incluir {@code correlationId} en la firma y en la
     * composición).
     * </p>
     *
     * @param orgId tenant (idealmente no null)
     * @param eventType tipo de evento (idealmente no null)
     * @param aggregateId id del agregado (idealmente no null / no blank)
     * @param occurredAtUtc instante del evento (idealmente no null)
     * @return clave idempotente estable (no null)
     */
    public static String buildEventKey(UUID orgId, String eventType, String aggregateId,
            OffsetDateTime occurredAtUtc) {

        String o = (orgId != null) ? orgId.toString() : "NO_ORG";
        String t = (eventType != null) ? eventType.trim() : "NO_TYPE";
        String a = (aggregateId != null && !aggregateId.isBlank()) ? aggregateId.trim() : "NO_AGG";
        String ts = (occurredAtUtc != null) ? occurredAtUtc.toInstant().toString() : "NO_TS";

        // Formato simple, legible y estable
        return o + "|" + t + "|" + a + "|" + ts;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAtUtc == null) {
            createdAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        }
    }

    // -------------------------
    // Getters/Setters
    // -------------------------

    public UUID getIdAudit() {
        return idAudit;
    }

    public void setIdAudit(UUID idAudit) {
        if (idAudit == null)
            throw new IllegalArgumentException("idAudit es obligatorio");
        this.idAudit = idAudit;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        String v = normalizeRequired(eventKey, "eventKey");
        if (v.length() > 220)
            throw new IllegalArgumentException("eventKey máximo 220 caracteres");
        this.eventKey = v;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        String v = normalizeRequired(eventType, "eventType");
        if (v.length() > 160)
            throw new IllegalArgumentException("eventType máximo 160 caracteres");
        this.eventType = v;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        String v = normalizeOptional(aggregateType);
        if (v != null && v.length() > 80)
            throw new IllegalArgumentException("aggregateType máximo 80");
        this.aggregateType = v;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        String v = normalizeOptional(aggregateId);
        if (v != null && v.length() > 60)
            throw new IllegalArgumentException("aggregateId máximo 60");
        this.aggregateId = v;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        String v = normalizeOptional(correlationId);
        if (v != null && v.length() > 120)
            throw new IllegalArgumentException("correlationId máximo 120");
        this.correlationId = v;
    }

    public OffsetDateTime getOccurredAtUtc() {
        return occurredAtUtc;
    }

    public void setOccurredAtUtc(OffsetDateTime occurredAtUtc) {
        if (occurredAtUtc == null)
            throw new IllegalArgumentException("occurredAtUtc es obligatorio");
        this.occurredAtUtc = occurredAtUtc;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        String v = normalizeRequired(payloadJson, "payloadJson");
        this.payloadJson = v;
    }

    public OffsetDateTime getCreatedAtUtc() {
        return createdAtUtc;
    }

    private static String normalizeOptional(String s) {
        String v = (s == null) ? null : s.trim();
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String normalizeRequired(String s, String field) {
        String v = (s == null) ? null : s.trim();
        if (v == null || v.isBlank())
            throw new IllegalArgumentException(field + " es obligatorio");
        return v;
    }
}
