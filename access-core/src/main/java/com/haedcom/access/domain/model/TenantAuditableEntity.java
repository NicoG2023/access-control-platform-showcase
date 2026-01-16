package com.haedcom.access.domain.model;

import java.time.Clock;
import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@MappedSuperclass
public abstract class TenantAuditableEntity extends TenantOnlyEntity {

    @Column(name = "creado_en_utc", nullable = false, updatable = false)
    protected OffsetDateTime creadoEnUtc;

    @Column(name = "actualizado_en_utc", nullable = false)
    protected OffsetDateTime actualizadoEnUtc;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(Clock.systemUTC());
        if (creadoEnUtc == null)
            creadoEnUtc = now;
        if (actualizadoEnUtc == null)
            actualizadoEnUtc = now;
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEnUtc = OffsetDateTime.now(Clock.systemUTC());
    }

    public OffsetDateTime getCreadoEnUtc() {
        return creadoEnUtc;
    }

    public OffsetDateTime getActualizadoEnUtc() {
        return actualizadoEnUtc;
    }
}
