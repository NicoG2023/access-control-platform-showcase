package com.haedcom.access.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisitaTest {

    @Test
    void setAreaDestinoReferencia_deberiaAsignarIdAreaDestino_yArea() {
        Organizacion org = Organizacion.crear(UUID.randomUUID(), "Org", "ACTIVO");
        Area area = Area.crear(org, "Destino", null);

        Visita v = new Visita();
        v.setAreaDestinoReferencia(area);

        assertThat(v.getIdAreaDestino()).isEqualTo(area.getIdArea());
        assertThat(v.getAreaDestino()).isSameAs(area);
    }

    @Test
    void setAreaDestinoReferencia_null_deberiaLanzar() {
        Visita v = new Visita();

        assertThatThrownBy(() -> v.setAreaDestinoReferencia(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("areaDestino no puede ser null");
    }

    @Test
    void setMotivo_deberiaValidarLongitud_yTrim() {
        Visita v = new Visita();
        v.setMotivo("  Hola  ");
        assertThat(v.getMotivo()).isEqualTo("Hola");

        assertThatThrownBy(() -> v.setMotivo("a".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Motivo excede longitud máxima");
    }
}
