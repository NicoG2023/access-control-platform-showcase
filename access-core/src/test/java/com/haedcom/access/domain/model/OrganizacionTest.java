package com.haedcom.access.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Organizacion")
class OrganizacionTest {

    @Test
    @DisplayName("crear() con ID específico debe asignarlo y setear campos")
    void crear_conIdEspecifico_deberiaAsignarlo_ySetearCampos() {
        UUID id = UUID.randomUUID();

        Organizacion o = Organizacion.crear(id, "Hospital Central", "ACTIVO");

        assertThat(o.getIdOrganizacion()).isEqualTo(id);
        assertThat(o.getNombre()).isEqualTo("Hospital Central");
        assertThat(o.getEstado()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("crear() con ID null debe generar uno automáticamente y setear campos")
    void crear_conIdNull_deberiaGenerarUno_ySetearCampos() {
        Organizacion o = Organizacion.crear(null, "Conjunto Residencial", "ACTIVO");

        assertThat(o.getIdOrganizacion()).isNotNull();
        assertThat(o.getNombre()).isEqualTo("Conjunto Residencial");
        assertThat(o.getEstado()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("crear() múltiples veces debe generar IDs diferentes")
    void crear_multipleVeces_deberiaGenerarIdsDiferentes() {
        Organizacion o1 = Organizacion.crear(null, "Org 1", "ACTIVO");
        Organizacion o2 = Organizacion.crear(null, "Org 2", "ACTIVO");

        assertThat(o1.getIdOrganizacion()).isNotEqualTo(o2.getIdOrganizacion());
    }

    @Test
    @DisplayName("setIdOrganizacion() no debe permitir null")
    void setIdOrganizacion_conNull_deberiaLanzarExcepcion() {
        Organizacion o = Organizacion.crear(UUID.randomUUID(), "X", "ACTIVO");

        assertThatThrownBy(() -> o.setIdOrganizacion(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idOrganizacion no puede ser null");
    }

    @Test
    @DisplayName("setNombre() debe hacer trim de espacios")
    void setNombre_deberiaHacerTrim() {
        Organizacion o = Organizacion.crear(null, "X", "ACTIVO");

        o.setNombre("  Nuevo Nombre  ");

        assertThat(o.getNombre()).isEqualTo("Nuevo Nombre");
    }

    @Test
    @DisplayName("setNombre() no debe permitir null")
    void setNombre_conNull_deberiaLanzarExcepcion() {
        Organizacion o = Organizacion.crear(null, "X", "ACTIVO");

        assertThatThrownBy(() -> o.setNombre(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nombre obligatorio");
    }

    @Test
    @DisplayName("setNombre() no debe permitir solo espacios en blanco")
    void setNombre_conSoloEspacios_deberiaLanzarExcepcion() {
        Organizacion o = Organizacion.crear(null, "X", "ACTIVO");

        assertThatThrownBy(() -> o.setNombre("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nombre obligatorio");
    }

    @Test
    @DisplayName("setNombre() no debe permitir cadena vacía")
    void setNombre_conCadenaVacia_deberiaLanzarExcepcion() {
        Organizacion o = Organizacion.crear(null, "X", "ACTIVO");

        assertThatThrownBy(() -> o.setNombre(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nombre obligatorio");
    }

    @Test
    @DisplayName("setEstado() debe hacer trim de espacios")
    void setEstado_deberiaHacerTrim() {
        Organizacion o = Organizacion.crear(null, "X", "ACTIVO");

        o.setEstado("  INACTIVO  ");

        assertThat(o.getEstado()).isEqualTo("INACTIVO");
    }

    @Test
    @DisplayName("setEstado() no debe permitir null")
    void setEstado_conNull_deberiaLanzarExcepcion() {
        Organizacion o = Organizacion.crear(null, "X", "ACTIVO");

        assertThatThrownBy(() -> o.setEstado(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Estado obligatorio");
    }

    @Test
    @DisplayName("setEstado() no debe permitir solo espacios en blanco")
    void setEstado_conSoloEspacios_deberiaLanzarExcepcion() {
        Organizacion o = Organizacion.crear(null, "X", "ACTIVO");

        assertThatThrownBy(() -> o.setEstado("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Estado obligatorio");
    }

    @Test
    @DisplayName("setEstado() no debe permitir cadena vacía")
    void setEstado_conCadenaVacia_deberiaLanzarExcepcion() {
        Organizacion o = Organizacion.crear(null, "X", "ACTIVO");

        assertThatThrownBy(() -> o.setEstado(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Estado obligatorio");
    }
}
