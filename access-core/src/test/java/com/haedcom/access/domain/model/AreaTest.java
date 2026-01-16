package com.haedcom.access.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AreaTest {

    @Test
    void crear_deberiaInicializarCamposObligatorios_yAsignarTenant() {
        UUID orgId = UUID.randomUUID();
        Organizacion org = Organizacion.crear(orgId, "Org Test", "ACTIVO");

        Area area = Area.crear(org, "  Urgencias  ", "  /img/area.png  ");

        assertThat(area.getIdArea()).isNotNull();
        assertThat(area.getIdOrganizacion()).isEqualTo(orgId);

        assertThat(area.getNombre()).isEqualTo("Urgencias");
        assertThat(area.getRutaImagenArea()).isEqualTo("/img/area.png");
    }

    @Test
    void crear_conRutaNull_deberiaPermitirNull() {
        UUID orgId = UUID.randomUUID();
        Organizacion org = Organizacion.crear(orgId, "Org Test", "ACTIVO");

        Area area = Area.crear(org, "Area X", null);

        assertThat(area.getIdArea()).isNotNull();
        assertThat(area.getIdOrganizacion()).isEqualTo(orgId);
        assertThat(area.getRutaImagenArea()).isNull();
    }

    @Test
    void setIdArea_null_deberiaLanzarIllegalArgumentException() {
        Area area =
                Area.crear(Organizacion.crear(UUID.randomUUID(), "Org", "ACTIVO"), "Area", null);

        assertThatThrownBy(() -> area.setIdArea(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idArea no puede ser null");
    }

    @Test
    void setNombre_null_deberiaLanzarIllegalArgumentException() {
        Area area =
                Area.crear(Organizacion.crear(UUID.randomUUID(), "Org", "ACTIVO"), "Area", null);

        assertThatThrownBy(() -> area.setNombre(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("nombre es obligatorio");
    }

    @Test
    void setNombre_blank_deberiaLanzarIllegalArgumentException() {
        Area area =
                Area.crear(Organizacion.crear(UUID.randomUUID(), "Org", "ACTIVO"), "Area", null);

        assertThatThrownBy(() -> area.setNombre("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("nombre es obligatorio");
    }

    @Test
    void setNombre_deberiaHacerTrim() {
        Area area =
                Area.crear(Organizacion.crear(UUID.randomUUID(), "Org", "ACTIVO"), "Area", null);

        area.setNombre("  Pediatría  ");

        assertThat(area.getNombre()).isEqualTo("Pediatría");
    }

    @Test
    void setRutaImagenArea_deberiaHacerTrim_yPermitirNull() {
        Area area = Area.crear(Organizacion.crear(UUID.randomUUID(), "Org", "ACTIVO"), "Area",
                " /x.png ");

        area.setRutaImagenArea("   /nueva.png   ");
        assertThat(area.getRutaImagenArea()).isEqualTo("/nueva.png");

        area.setRutaImagenArea(null);
        assertThat(area.getRutaImagenArea()).isNull();
    }
}
