package com.haedcom.access.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.haedcom.access.domain.enums.EstadoResidente;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;

@DisplayName("Residente")
class ResidenteTest {

        private Organizacion organizacionValida() {
                // Usar id null para que la entidad lo genere (más “real”)
                return Organizacion.crear(null, "Hospital Central", "ACTIVO");
        }

        private static void invokePrePersist(Object entity, String methodName) {
                try {
                        Method m = entity.getClass().getDeclaredMethod(methodName);
                        m.setAccessible(true);
                        m.invoke(entity);
                } catch (Exception e) {
                        throw new RuntimeException(
                                        "No se pudo invocar " + methodName + " por reflexión", e);
                }
        }

        private static void invokeTenantValidate(Residente r) {
                try {
                        Method m = TenantOnlyEntity.class.getDeclaredMethod("validateTenant");
                        m.setAccessible(true);
                        m.invoke(r);
                } catch (Exception e) {
                        throw new RuntimeException(
                                        "No se pudo invocar validateTenant() por reflexión", e);
                }
        }

        @Test
        @DisplayName("crear() debe construir un residente válido, asignar id y setear campos")
        void crear_deberiaConstruirUnResidenteValido_yAsignarId_ySetearCampos() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Juan Pérez", TipoDocumentoIdentidad.CC, "123",
                                "juan@mail.com", "3000000000", "Apto 101");

                assertThat(r.getIdResidente()).isNotNull();
                assertThat(r.getNombre()).isEqualTo("Juan Pérez");
                assertThat(r.getTipoDocumento()).isEqualTo(TipoDocumentoIdentidad.CC);
                assertThat(r.getNumeroDocumento()).isEqualTo("123");
                assertThat(r.getCorreo()).isEqualTo("juan@mail.com");
                assertThat(r.getTelefono()).isEqualTo("3000000000");
                assertThat(r.getDomicilio()).isEqualTo("Apto 101");

                // Tenant: el residente debe quedar amarrado a la organización
                assertThat(r.getIdOrganizacion()).isEqualTo(org.getIdOrganizacion());
        }

        @Test
        @DisplayName("crear() debe hacer trim en nombre, numeroDocumento, correo, telefono y domicilio")
        void crear_deberiaHacerTrim_enCampos() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "  Ana  ", TipoDocumentoIdentidad.CC, "  999  ",
                                "  ana@mail.com  ", "  300  ", "  Apto 101  ");

                assertThat(r.getNombre()).isEqualTo("Ana");
                assertThat(r.getNumeroDocumento()).isEqualTo("999");
                assertThat(r.getCorreo()).isEqualTo("ana@mail.com");
                assertThat(r.getTelefono()).isEqualTo("300");
                assertThat(r.getDomicilio()).isEqualTo("Apto 101");
        }

        @Test
        @DisplayName("crear() debe permitir correo/telefono/domicilio en null")
        void crear_deberiaPermitirNulos_enCamposOpcionales() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Ana", TipoDocumentoIdentidad.CC, "999", null,
                                null, null);

                assertThat(r.getCorreo()).isNull();
                assertThat(r.getTelefono()).isNull();
                assertThat(r.getDomicilio()).isNull();
        }

        @Test
        @DisplayName("crear() debe asignar estado por defecto ACTIVO al persistir (simulando @PrePersist)")
        void prePersist_deberiaSetearEstadoPorDefecto() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Ana", TipoDocumentoIdentidad.CC, "999", null,
                                null, null);

                // se forza a null para verificar que @PrePersist lo pone en ACTIVO
                r.setEstado(EstadoResidente.INACTIVO);
                r.setEstado(EstadoResidente.INACTIVO); // solo para dejar claro que podemos
                                                       // cambiarlo
                r.setEstado(EstadoResidente.INACTIVO);

                // ahora se deja en null vía reflexión (porque setter no permite null)
                try {
                        var f = Residente.class.getDeclaredField("estado");
                        f.setAccessible(true);
                        f.set(r, null);
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }

                invokePrePersist(r, "ensureDefaults");
                assertThat(r.getEstado()).isEqualTo(EstadoResidente.ACTIVO);
        }

        @Test
        @DisplayName("@PrePersist debe asignar idResidente si viene null (simulando persistencia)")
        void prePersist_deberiaAsignarIdSiEsNull() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Ana", TipoDocumentoIdentidad.CC, "999", null,
                                null, null);

                // Dejamos id en null por reflexión para simular error humano / construcción
                // incompleta
                try {
                        var f = Residente.class.getDeclaredField("idResidente");
                        f.setAccessible(true);
                        f.set(r, null);
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }

                invokePrePersist(r, "ensureDefaults");
                assertThat(r.getIdResidente()).isNotNull();
        }

        @Test
        @DisplayName("setIdResidente(null) debe lanzar IllegalArgumentException")
        void setIdResidente_conNull_deberiaLanzar() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Ok", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);

                assertThatThrownBy(() -> r.setIdResidente(null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("idResidente no puede ser null");
        }

        @Test
        @DisplayName("setNombre() debe lanzar si es null o blank")
        void setNombre_deberiaLanzarSiEsNullOVacio() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Ok", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);

                assertThatThrownBy(() -> r.setNombre(null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("nombre es obligatorio");

                assertThatThrownBy(() -> r.setNombre("   "))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("nombre es obligatorio");
        }

        @Test
        @DisplayName("setTipoDocumento(null) debe lanzar IllegalArgumentException")
        void setTipoDocumento_deberiaLanzarSiEsNull() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Ok", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);

                assertThatThrownBy(() -> r.setTipoDocumento(null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("tipoDocumento es obligatorio");
        }

        @Test
        @DisplayName("setNumeroDocumento() debe lanzar si es null o blank")
        void setNumeroDocumento_deberiaLanzarSiEsNullOVacio() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Ok", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);

                assertThatThrownBy(() -> r.setNumeroDocumento(null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("numeroDocumento es obligatorio");

                assertThatThrownBy(() -> r.setNumeroDocumento("   "))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("numeroDocumento es obligatorio");
        }

        @Test
        @DisplayName("setNumeroDocumento() debe validar longitud máxima 30")
        void setNumeroDocumento_deberiaValidarLongitudMaxima() {
                Organizacion org = organizacionValida();
                Residente r = Residente.crear(org, "Ok", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);

                String largo31 = "x".repeat(31);

                assertThatThrownBy(() -> r.setNumeroDocumento(largo31))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("numeroDocumento máximo 30 caracteres");
        }

        @Test
        @DisplayName("setCorreo() debe validar longitud máxima 200")
        void setCorreo_deberiaValidarLongitudMaxima() {
                Organizacion org = organizacionValida();
                Residente r = Residente.crear(org, "Ok", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);

                String largo201 = "x".repeat(201);

                assertThatThrownBy(() -> r.setCorreo(largo201))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("correo máximo 200 caracteres");
        }

        @Test
        @DisplayName("setTelefono() debe validar longitud máxima 30")
        void setTelefono_deberiaValidarLongitudMaxima() {
                Organizacion org = organizacionValida();
                Residente r = Residente.crear(org, "Ok", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);

                String largo31 = "x".repeat(31);

                assertThatThrownBy(() -> r.setTelefono(largo31))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("telefono máximo 30 caracteres");
        }

        @Test
        @DisplayName("setDomicilio() debe validar longitud máxima 255")
        void setDomicilio_deberiaValidarLongitudMaxima() {
                Organizacion org = organizacionValida();
                Residente r = Residente.crear(org, "Ok", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);

                String largo256 = "x".repeat(256);

                assertThatThrownBy(() -> r.setDomicilio(largo256))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("domicilio máximo 255 caracteres");
        }

        @Test
        @DisplayName("setEstado(null) debe lanzar IllegalArgumentException")
        void setEstado_conNull_deberiaLanzar() {
                Organizacion org = organizacionValida();
                Residente r = Residente.crear(org, "Ok", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);

                assertThatThrownBy(() -> r.setEstado(null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("estado es obligatorio");
        }

        @Test
        @DisplayName("actualizarDatos() debe aplicar trim y actualizar todos los campos")
        void actualizarDatos_deberiaAplicarTrim_yActualizarCampos() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Inicial", TipoDocumentoIdentidad.CC, "1", null,
                                null, null);

                r.actualizarDatos("  Nuevo  ", TipoDocumentoIdentidad.PA, "  777  ",
                                "  nuevo@mail.com  ", "  311  ", "  Torre 2  ");

                assertThat(r.getNombre()).isEqualTo("Nuevo");
                assertThat(r.getTipoDocumento()).isEqualTo(TipoDocumentoIdentidad.PA);
                assertThat(r.getNumeroDocumento()).isEqualTo("777");
                assertThat(r.getCorreo()).isEqualTo("nuevo@mail.com");
                assertThat(r.getTelefono()).isEqualTo("311");
                assertThat(r.getDomicilio()).isEqualTo("Torre 2");
        }

        @Test
        @DisplayName("actualizarDatos() debe validar obligatorios igual que los setters")
        void actualizarDatos_deberiaValidarObligatorios() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Inicial", TipoDocumentoIdentidad.CC, "1", null,
                                null, null);

                assertThatThrownBy(() -> r.actualizarDatos(null, TipoDocumentoIdentidad.CC, "1",
                                null, null, null)).isInstanceOf(IllegalArgumentException.class)
                                                .hasMessage("nombre es obligatorio");

                assertThatThrownBy(() -> r.actualizarDatos("Ok", null, "1", null, null, null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("tipoDocumento es obligatorio");

                assertThatThrownBy(() -> r.actualizarDatos("Ok", TipoDocumentoIdentidad.CC, "   ",
                                null, null, null)).isInstanceOf(IllegalArgumentException.class)
                                                .hasMessage("numeroDocumento es obligatorio");
        }

        @Test
        @DisplayName("setOrganizacionTenant(null) debe lanzar IllegalArgumentException")
        void setOrganizacionTenant_conNull_deberiaLanzar() {
                Residente r = Residente.crear(organizacionValida(), "Ok", TipoDocumentoIdentidad.CC,
                                "1", null, null, null);

                assertThatThrownBy(() -> r.setOrganizacionTenant(null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("organizacion no puede ser null");
        }

        @Test
        @DisplayName("validateTenant() debe lanzar si no hay idOrganizacion (simulando @PrePersist)")
        void validateTenant_deberiaLanzarSiNoHayTenant() {
                Organizacion org = organizacionValida();

                Residente r = Residente.crear(org, "Ok", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);

                // se rompe el tenant a propósito (reflexión) para simular un bug
                try {
                        var f = TenantOnlyEntity.class.getDeclaredField("idOrganizacion");
                        f.setAccessible(true);
                        f.set(r, null);
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }

                assertThatThrownBy(() -> invokeTenantValidate(r))
                                .hasRootCauseInstanceOf(IllegalStateException.class).rootCause()
                                .hasMessageStartingWith("Entidad tenant sin idOrganizacion:");
        }
}
