package com.haedcom.access.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantOnlyEntityTest {

    static class DummyTenantEntity extends TenantOnlyEntity {

        public void triggerValidateTenant() {
            validateTenant();
        }

        // helper público para invocar el protected setIdOrganizacion desde el test
        public void setTenantId(UUID id) {
            setIdOrganizacion(id);
        }

        // helper para inspeccionar estado interno (opcional, pero válido)
        public UUID getInternalIdOrganizacion() {
            return this.idOrganizacion;
        }
    }

    @Test
    void setIdOrganizacion_deberiaAsignarValor() {
        DummyTenantEntity entity = new DummyTenantEntity();
        UUID orgId = UUID.randomUUID();

        entity.setTenantId(orgId);

        assertThat(entity.getIdOrganizacion()).isEqualTo(orgId);
        assertThat(entity.getInternalIdOrganizacion()).isEqualTo(orgId);
    }

    @Test
    void setIdOrganizacion_null_deberiaLanzarIllegalArgumentException() {
        DummyTenantEntity entity = new DummyTenantEntity();

        assertThatThrownBy(() -> entity.setTenantId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idOrganizacion no puede ser null");
    }

    @Test
    void setOrganizacionTenant_deberiaAsignarSoloId() {
        DummyTenantEntity entity = new DummyTenantEntity();
        UUID orgId = UUID.randomUUID();
        Organizacion org = Organizacion.crear(orgId, "Org Test", "ACTIVO");

        entity.setOrganizacionTenant(org);

        assertThat(entity.getIdOrganizacion()).isEqualTo(orgId);
        assertThat(entity.getInternalIdOrganizacion()).isEqualTo(orgId);
    }

    @Test
    void setOrganizacionTenant_null_deberiaLanzarIllegalArgumentException() {
        DummyTenantEntity entity = new DummyTenantEntity();

        assertThatThrownBy(() -> entity.setOrganizacionTenant(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organizacion no puede ser null");
    }

    @Test
    void validateTenant_sinIdOrganizacion_deberiaLanzarIllegalStateException() {
        DummyTenantEntity entity = new DummyTenantEntity();

        assertThatThrownBy(entity::triggerValidateTenant).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Entidad tenant sin idOrganizacion")
                .hasMessageContaining("DummyTenantEntity");
    }

    @Test
    void validateTenant_conIdOrganizacion_noDeberiaLanzar() {
        DummyTenantEntity entity = new DummyTenantEntity();
        entity.setTenantId(UUID.randomUUID());

        entity.triggerValidateTenant(); // no debe lanzar
    }

    @Test
    void assignTenant_deberiaAsignarValor() {
        DummyTenantEntity entity = new DummyTenantEntity();
        UUID orgId = UUID.randomUUID();

        entity.assignTenant(orgId);

        assertThat(entity.getIdOrganizacion()).isEqualTo(orgId);
    }

    @Test
    void assignTenant_null_deberiaLanzarIllegalArgumentException() {
        DummyTenantEntity entity = new DummyTenantEntity();

        assertThatThrownBy(() -> entity.assignTenant(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("orgId no puede ser null");
    }
}
