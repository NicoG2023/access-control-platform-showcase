package com.haedcom.access.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantAuditableEntityTest {

    static class DummyTenantAuditableEntity extends TenantAuditableEntity {
        public void triggerOnCreate() {
            onCreate();
        }

        public void triggerOnUpdate() {
            onUpdate();
        }

        public void setCreadoEnUtcForTest(OffsetDateTime value) {
            this.creadoEnUtc = value;
        }

        public void setActualizadoEnUtcForTest(OffsetDateTime value) {
            this.actualizadoEnUtc = value;
        }

        // helper para poder setear el tenant (setIdOrganizacion es protected)
        public void setTenantId(UUID id) {
            setIdOrganizacion(id);
        }
    }

    @Test
    void onCreate_deberiaInicializarAmbasFechas_cuandoCreadoEsNull() {
        DummyTenantAuditableEntity entity = new DummyTenantAuditableEntity();
        entity.setTenantId(UUID.randomUUID());

        entity.triggerOnCreate();

        assertThat(entity.getCreadoEnUtc()).isNotNull();
        assertThat(entity.getActualizadoEnUtc()).isNotNull();
        assertThat(entity.getCreadoEnUtc()).isEqualTo(entity.getActualizadoEnUtc());
    }

    @Test
    void onCreate_deberiaRespetarCreadoEnUtc_yActualizadoEnUtc_siYaExisten() {
        DummyTenantAuditableEntity entity = new DummyTenantAuditableEntity();
        entity.setTenantId(UUID.randomUUID());

        OffsetDateTime creadoManual = OffsetDateTime.now().minusDays(10);
        OffsetDateTime actualizadoViejo = OffsetDateTime.now().minusDays(1);

        entity.setCreadoEnUtcForTest(creadoManual);
        entity.setActualizadoEnUtcForTest(actualizadoViejo);

        entity.triggerOnCreate();

        assertThat(entity.getCreadoEnUtc()).isEqualTo(creadoManual)
                .as("creadoEnUtc no debe sobrescribirse si ya venía seteado");

        assertThat(entity.getActualizadoEnUtc()).isEqualTo(actualizadoViejo)
                .as("actualizadoEnUtc no debe sobrescribirse en onCreate si ya venía seteado");
    }

    @Test
    void onUpdate_deberiaActualizarSoloActualizadoEnUtc() {
        DummyTenantAuditableEntity entity = new DummyTenantAuditableEntity();
        entity.setTenantId(UUID.randomUUID());
        entity.triggerOnCreate();

        OffsetDateTime creadoOriginal = entity.getCreadoEnUtc();
        OffsetDateTime actualizadoOriginal = entity.getActualizadoEnUtc();

        entity.triggerOnUpdate();

        assertThat(entity.getCreadoEnUtc()).isEqualTo(creadoOriginal)
                .as("creadoEnUtc no debe cambiar en actualización");

        // puede ser igual en máquinas rápidas si el reloj no avanza (raro pero posible).
        // por eso usamos >=
        assertThat(entity.getActualizadoEnUtc()).isAfterOrEqualTo(actualizadoOriginal)
                .as("actualizadoEnUtc debe ser posterior o igual (según resolución del clock)");
    }

    @Test
    void onCreate_deberiaUsarHoraActual_aproximada() {
        OffsetDateTime antes = OffsetDateTime.now();

        DummyTenantAuditableEntity entity = new DummyTenantAuditableEntity();
        entity.setTenantId(UUID.randomUUID());
        entity.triggerOnCreate();

        OffsetDateTime despues = OffsetDateTime.now();

        assertThat(entity.getCreadoEnUtc()).isBetween(antes, despues);
        assertThat(entity.getActualizadoEnUtc()).isBetween(antes, despues);
    }
}
