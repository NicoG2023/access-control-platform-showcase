package com.haedcom.access.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TenantCreatedEntityTest {

    static class DummyCreatedEntity extends TenantCreatedEntity {
        public void triggerOnCreate() {
            onCreate();
        }

        public void setCreadoEnUtcForTest(OffsetDateTime value) {
            this.creadoEnUtc = value;
        }
    }

    @Test
    void onCreate_deberiaInicializarCreadoEnUtc_siEsNull() {
        OffsetDateTime antes = OffsetDateTime.now();

        DummyCreatedEntity entity = new DummyCreatedEntity();
        entity.setIdOrganizacion(UUID.randomUUID()); // para coherencia tenant (aunque aquí no llamamos validateTenant)
        entity.triggerOnCreate();

        OffsetDateTime despues = OffsetDateTime.now();

        assertThat(entity.getCreadoEnUtc()).isNotNull();
        assertThat(entity.getCreadoEnUtc()).isBetween(antes, despues);
    }

    @Test
    void onCreate_noDeberiaSobrescribirCreadoEnUtc_siYaExiste() {
        DummyCreatedEntity entity = new DummyCreatedEntity();
        entity.setIdOrganizacion(UUID.randomUUID());

        OffsetDateTime creadoManual = OffsetDateTime.now().minusDays(3);
        entity.setCreadoEnUtcForTest(creadoManual);

        entity.triggerOnCreate();

        assertThat(entity.getCreadoEnUtc()).isEqualTo(creadoManual);
    }
}
