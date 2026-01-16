package com.haedcom.access.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Entidad Outbox para publicación confiable de eventos de dominio (multi-tenant).
 *
 * <p>
 * Implementa el patrón <b>Transactional Outbox</b>: los eventos se persisten en la misma
 * transacción del caso de uso y luego un dispatcher los entrega hacia el bus/microservicios.
 * </p>
 *
 * <h2>Tenant</h2>
 * <p>
 * Hereda {@link TenantOnlyEntity}, por lo que el {@code id_organizacion} se gestiona vía
 * {@link #assignTenant(UUID)}. El campo es <b>obligatorio</b> y se valida en {@code @PrePersist}.
 * </p>
 *
 * <h2>Estados</h2>
 * <ul>
 * <li>{@link OutboxStatus#PENDING}: listo para ser publicado</li>
 * <li>{@link OutboxStatus#PUBLISHED}: publicado exitosamente</li>
 * <li>{@link OutboxStatus#FAILED}: falló tras reintentos (requiere intervención)</li>
 * </ul>
 *
 * <h2>Notas</h2>
 * <ul>
 * <li>El payload se guarda como JSON serializado.</li>
 * <li>El dispatcher incrementa {@code attempts} y marca {@code publishedAtUtc}.</li>
 * </ul>
 */
@Entity
@Table(name = "outbox_event",
        indexes = {@Index(name = "ix_outbox_status", columnList = "status"),
                @Index(name = "ix_outbox_created", columnList = "created_at_utc"),
                @Index(name = "ix_outbox_pending_next_created",
                        columnList = "status, next_attempt_at_utc, created_at_utc")})

public class OutboxEvent extends TenantOnlyEntity {

    @Id
    @Column(name = "id_evento", nullable = false)
    private UUID idEvento;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 120)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OutboxStatus status;

    @Column(name = "created_at_utc", nullable = false)
    private OffsetDateTime createdAtUtc;

    @Column(name = "published_at_utc")
    private OffsetDateTime publishedAtUtc;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at_utc")
    private OffsetDateTime nextAttemptAtUtc;

    /**
     * Información diagnóstica del último error de despacho.
     *
     * <p>
     * Estos campos NO son parte del evento de dominio; son metadata operacional del mecanismo de
     * entrega (outbox).
     * </p>
     */
    @Column(name = "last_error_code", length = 60)
    private String lastErrorCode;

    @Lob
    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "last_error_at_utc")
    private OffsetDateTime lastErrorAtUtc;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "locked_at_utc")
    private OffsetDateTime lockedAtUtc;

    @Column(name = "locked_by", length = 80)
    private String lockedBy;

    // ---------------------------------------------------------------------
    // Getters / Setters
    // ---------------------------------------------------------------------

    public UUID getIdEvento() {
        return idEvento;
    }

    public void setIdEvento(UUID idEvento) {
        this.idEvento = idEvento;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAtUtc() {
        return createdAtUtc;
    }

    public void setCreatedAtUtc(OffsetDateTime createdAtUtc) {
        this.createdAtUtc = createdAtUtc;
    }

    public OffsetDateTime getPublishedAtUtc() {
        return publishedAtUtc;
    }

    public void setPublishedAtUtc(OffsetDateTime publishedAtUtc) {
        this.publishedAtUtc = publishedAtUtc;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public OffsetDateTime getNextAttemptAtUtc() {
        return nextAttemptAtUtc;
    }

    public void setNextAttemptAtUtc(OffsetDateTime nextAttemptAtUtc) {
        this.nextAttemptAtUtc = nextAttemptAtUtc;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public OffsetDateTime getLastErrorAtUtc() {
        return lastErrorAtUtc;
    }

    public void setLastErrorAtUtc(OffsetDateTime lastErrorAtUtc) {
        this.lastErrorAtUtc = lastErrorAtUtc;
    }

    public Integer getLastHttpStatus() {
        return lastHttpStatus;
    }

    public void setLastHttpStatus(Integer lastHttpStatus) {
        this.lastHttpStatus = lastHttpStatus;
    }

    public OffsetDateTime getLockedAtUtc() {
        return lockedAtUtc;
    }

    public void setLockedAtUtc(OffsetDateTime lockedAtUtc) {
        this.lockedAtUtc = lockedAtUtc;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

}
