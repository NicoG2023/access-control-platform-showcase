package com.haedcom.access.domain.model;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

@MappedSuperclass
public abstract class TenantCreatedEntity extends TenantOnlyEntity {

    @Column(name = "creado_en_utc", nullable = false, updatable = false)
    protected OffsetDateTime creadoEnUtc;

    @PrePersist
    protected void onCreate() {
        if (creadoEnUtc == null)
            creadoEnUtc = OffsetDateTime.now();
    }

    public OffsetDateTime getCreadoEnUtc() {
        return creadoEnUtc;
    }
}
