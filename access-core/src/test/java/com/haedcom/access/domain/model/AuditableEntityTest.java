package com.haedcom.access.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AuditableEntityTest {

    static class DummyEntity extends AuditableEntity {

        public OffsetDateTime getCreadoEnUtc() {
            return creadoEnUtc;
        }

        public OffsetDateTime getActualizadoEnUtc() {
            return actualizadoEnUtc;
        }

        public void triggerOnCreate() {
            onCreate();
        }

        public void triggerOnUpdate() {
            onUpdate();
        }
    }

    @Test
    void onCreate_deberiaInicializarAmbasFechas() {
        DummyEntity entity = new DummyEntity();

        entity.triggerOnCreate();

        assertThat(entity.getCreadoEnUtc()).isNotNull();
        assertThat(entity.getActualizadoEnUtc()).isNotNull();
        assertThat(entity.getCreadoEnUtc()).isEqualTo(entity.getActualizadoEnUtc());
    }

    @Test
    void onUpdate_deberiaActualizarSoloActualizadoEnUtc() throws InterruptedException {
        DummyEntity entity = new DummyEntity();
        entity.triggerOnCreate();

        OffsetDateTime creadoOriginal = entity.getCreadoEnUtc();
        OffsetDateTime actualizadoOriginal = entity.getActualizadoEnUtc();

        // Esperar un poco para asegurar diferencia de tiempo
        Thread.sleep(10);

        entity.triggerOnUpdate();

        assertThat(entity.getCreadoEnUtc()).isEqualTo(creadoOriginal)
                .as("creadoEnUtc no debe cambiar en actualización");

        assertThat(entity.getActualizadoEnUtc()).isAfter(actualizadoOriginal)
                .as("actualizadoEnUtc debe ser posterior");
    }

    @Test
    void onCreate_deberiaUsarHoraActual() {
        OffsetDateTime antes = OffsetDateTime.now();

        DummyEntity entity = new DummyEntity();
        entity.triggerOnCreate();

        OffsetDateTime despues = OffsetDateTime.now();

        assertThat(entity.getCreadoEnUtc()).isBetween(antes, despues);
        assertThat(entity.getActualizadoEnUtc()).isBetween(antes, despues);
    }
}
