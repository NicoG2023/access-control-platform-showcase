package com.haedcom.access.domain.model;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

@MappedSuperclass
public abstract class TenantOnlyEntity {

    @Column(name = "id_organizacion", nullable = false, updatable = false)
    protected UUID idOrganizacion;

    protected TenantOnlyEntity() {}

    public UUID getIdOrganizacion() {
        return idOrganizacion;
    }

    protected void setIdOrganizacion(UUID idOrganizacion) {
        if (idOrganizacion == null) {
            throw new IllegalArgumentException("idOrganizacion no puede ser null");
        }
        this.idOrganizacion = idOrganizacion;
    }

    /**
     * Asocia la entidad a un tenant.
     *
     * <p>
     * Solo copia el identificador para evitar dependencias fuertes entre agregados.
     * </p>
     */
    public void setOrganizacionTenant(Organizacion org) {
        if (org == null) {
            throw new IllegalArgumentException("organizacion no puede ser null");
        }
        this.idOrganizacion = org.getIdOrganizacion();
    }

    @PrePersist
    protected void validateTenant() {
        if (idOrganizacion == null) {
            throw new IllegalStateException(
                    "Entidad tenant sin idOrganizacion: " + getClass().getSimpleName());
        }
    }

    public void assignTenant(UUID orgId) {
        if (orgId == null) {
            throw new IllegalArgumentException("orgId no puede ser null");
        }
        this.idOrganizacion = orgId;
    }

}
