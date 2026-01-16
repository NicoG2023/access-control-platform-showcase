package com.haedcom.access.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispositivoTest {

    @Test
    void setAreaReferencia_deberiaAsignarIdArea_yArea() {
        Organizacion org = Organizacion.crear(UUID.randomUUID(), "Org", "ACTIVO");
        Area area = Area.crear(org, "Zona 1", null);

        Dispositivo d = new Dispositivo();
        d.setAreaReferencia(area);

        assertThat(d.getIdArea()).isEqualTo(area.getIdArea());
        assertThat(d.getArea()).isSameAs(area);
    }

    @Test
    void setAreaReferencia_null_deberiaLanzar() {
        Dispositivo d = new Dispositivo();

        assertThatThrownBy(() -> d.setAreaReferencia(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("area no puede ser null");
    }
}
