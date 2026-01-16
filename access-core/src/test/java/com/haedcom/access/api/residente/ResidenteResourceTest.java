package com.haedcom.access.api.residente;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.residente.dto.ResidenteEstadoRequest;
import com.haedcom.access.api.residente.dto.ResidenteResponse;
import com.haedcom.access.api.residente.dto.ResidenteUpsertRequest;
import com.haedcom.access.application.residente.ResidenteService;
import com.haedcom.access.domain.enums.EstadoResidente;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@QuarkusTest
class ResidenteResourceTest {

        @InjectMock
        ResidenteService service;

        private static final String BASE = "/organizaciones/%s/residentes";

        @BeforeEach
        void setup() {
                Mockito.reset(service);
        }

        private ResidenteResponse resp(UUID residenteId, UUID orgId, String nombre,
                        TipoDocumentoIdentidad tipo, String numero, EstadoResidente estado) {

                OffsetDateTime now = OffsetDateTime.now();
                return new ResidenteResponse(residenteId, orgId, nombre, tipo, numero, null, null,
                                null, estado, now, now);
        }

        // -------------------------
        // list
        // -------------------------

        @Test
        void list_deberiaRetornar200_yPageResponse_conDefaults() {
                UUID orgId = UUID.randomUUID();

                var r1 = resp(UUID.randomUUID(), orgId, "A", TipoDocumentoIdentidad.CC, "1",
                                EstadoResidente.ACTIVO);
                var r2 = resp(UUID.randomUUID(), orgId, "B", TipoDocumentoIdentidad.CE, "2",
                                EstadoResidente.INACTIVO);

                // page=0, size=20 (defaults del resource)
                PageResponse<ResidenteResponse> page = PageResponse.of(List.of(r1, r2), 0, 20, 2);

                when(service.list(eq(orgId), isNull(), isNull(), isNull(), isNull(), isNull(),
                                isNull(), eq(0), eq(20))).thenReturn(page);

                given().when().get(String.format(BASE, orgId)).then().statusCode(200)
                                // wrapper
                                .body("items", hasSize(2)).body("total", equalTo(2))
                                .body("page", equalTo(0)).body("size", equalTo(20))
                                // contenido
                                .body("items[0].idOrganizacion", equalTo(orgId.toString()))
                                .body("items[1].idOrganizacion", equalTo(orgId.toString()));
        }

        @Test
        void list_deberiaPasarFiltros_yPaginacion_alServicio() {
                UUID orgId = UUID.randomUUID();

                String q = "juan";
                TipoDocumentoIdentidad tipo = TipoDocumentoIdentidad.CC;
                String numero = "123";
                EstadoResidente estado = EstadoResidente.ACTIVO;
                String sort = "nombre";
                String dir = "desc";
                int pageNum = 2;
                int sizeNum = 5;

                PageResponse<ResidenteResponse> out =
                                PageResponse.of(List.of(), pageNum, sizeNum, 0);

                when(service.list(eq(orgId), eq(q), eq(tipo), eq(numero), eq(estado), eq(sort),
                                eq(dir), eq(pageNum), eq(sizeNum))).thenReturn(out);

                given().queryParam("q", q).queryParam("tipoDocumento", "CC")
                                .queryParam("numeroDocumento", numero)
                                .queryParam("estado", "ACTIVO").queryParam("sort", sort)
                                .queryParam("dir", dir).queryParam("page", pageNum)
                                .queryParam("size", sizeNum).when().get(String.format(BASE, orgId))
                                .then().statusCode(200).body("items", hasSize(0))
                                .body("total", equalTo(0)).body("page", equalTo(pageNum))
                                .body("size", equalTo(sizeNum));
        }

        // -------------------------
        // get
        // -------------------------

        @Test
        void get_deberiaRetornar200() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                var out = resp(residenteId, orgId, "Juan", TipoDocumentoIdentidad.CC, "123",
                                EstadoResidente.ACTIVO);

                when(service.get(orgId, residenteId)).thenReturn(out);

                given().when().get(String.format(BASE, orgId) + "/" + residenteId).then()
                                .statusCode(200)
                                .body("idResidente", equalTo(residenteId.toString()))
                                .body("idOrganizacion", equalTo(orgId.toString()))
                                .body("nombre", equalTo("Juan")).body("estado", equalTo("ACTIVO"));
        }

        // -------------------------
        // create
        // -------------------------

        @Test
        void create_deberiaRetornar201_yLocation() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                var created = resp(residenteId, orgId, "Juan", TipoDocumentoIdentidad.CC, "123",
                                EstadoResidente.ACTIVO);

                when(service.create(any(UUID.class), any(ResidenteUpsertRequest.class)))
                                .thenReturn(created);

                given().contentType("application/json").body("""
                                    {
                                      "nombre":"Juan",
                                      "tipoDocumento":"CC",
                                      "numeroDocumento":"123",
                                      "correo":null,
                                      "telefono":null,
                                      "domicilio":null
                                    }
                                """).when().post(String.format(BASE, orgId)).then().statusCode(201)
                                .header("Location", allOf(
                                                containsString("/organizaciones/" + orgId
                                                                + "/residentes/"),
                                                endsWith("/residentes/" + residenteId)))
                                .body("idResidente", equalTo(residenteId.toString()))
                                .body("idOrganizacion", equalTo(orgId.toString()));
        }

        @Test
        void create_deberiaRetornar400_siFallaValidacion_yTraerDetails() {
                UUID orgId = UUID.randomUUID();

                given().contentType("application/json").body("""
                                    {
                                      "nombre":"   ",
                                      "tipoDocumento":"CC",
                                      "numeroDocumento":""
                                    }
                                """).when().post(String.format(BASE, orgId)).then().statusCode(400)
                                .body("code", equalTo("VALIDATION_ERROR"))
                                .body("message", equalTo("Error de validación en el request"))
                                .body("status", equalTo(400))
                                .body("path", equalTo("/organizaciones/" + orgId + "/residentes"))
                                .body("timestamp", not(emptyOrNullString()))
                                .body("details", notNullValue())
                                .body("details.size()", greaterThanOrEqualTo(1))
                                .body("details.field", hasItem(anyOf(containsString("nombre"),
                                                containsString("numeroDocumento"))));
        }

        // -------------------------
        // update
        // -------------------------

        @Test
        void update_deberiaRetornar200() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                var updated = resp(residenteId, orgId, "Nuevo", TipoDocumentoIdentidad.CE, "999",
                                EstadoResidente.ACTIVO);

                when(service.update(any(UUID.class), any(UUID.class),
                                any(ResidenteUpsertRequest.class))).thenReturn(updated);

                given().contentType("application/json").body("""
                                    {
                                      "nombre":"Nuevo",
                                      "tipoDocumento":"CE",
                                      "numeroDocumento":"999",
                                      "correo":null,
                                      "telefono":null,
                                      "domicilio":null
                                    }
                                """).when().put(String.format(BASE, orgId) + "/" + residenteId)
                                .then().statusCode(200).body("nombre", equalTo("Nuevo"))
                                .body("tipoDocumento", equalTo("CE"))
                                .body("numeroDocumento", equalTo("999"));
        }

        // -------------------------
        // delete
        // -------------------------

        @Test
        void delete_deberiaRetornar204() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                doNothing().when(service).delete(any(UUID.class), any(UUID.class));

                given().when().delete(String.format(BASE, orgId) + "/" + residenteId).then()
                                .statusCode(204).body(emptyOrNullString());
        }

        // -------------------------
        // updateEstado (PATCH)
        // -------------------------

        @Test
        void updateEstado_deberiaRetornar200() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                var updated = resp(residenteId, orgId, "Juan", TipoDocumentoIdentidad.CC, "123",
                                EstadoResidente.INACTIVO);

                when(service.updateEstado(eq(orgId), eq(residenteId),
                                any(ResidenteEstadoRequest.class))).thenReturn(updated);

                given().contentType("application/json").body("""
                                    { "estado": "INACTIVO" }
                                """).when()
                                .patch(String.format(BASE, orgId) + "/" + residenteId + "/estado")
                                .then().statusCode(200).body("estado", equalTo("INACTIVO"))
                                .body("idResidente", equalTo(residenteId.toString()));
        }

        @Test
        void updateEstado_deberiaRetornar400_siFallaValidacion() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                given().contentType("application/json").body("""
                                    { "estado": null }
                                """).when()
                                .patch(String.format(BASE, orgId) + "/" + residenteId + "/estado")
                                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        }

        // -------------------------
        // error mapping
        // -------------------------

        @Test
        void get_deberiaMapear404_aErrorResponse() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                when(service.get(any(UUID.class), any(UUID.class)))
                                .thenThrow(new NotFoundException("Residente no encontrado"));

                given().when().get(String.format(BASE, orgId) + "/" + residenteId).then()
                                .statusCode(404).body("code", equalTo("NOT_FOUND"))
                                .body("message", containsString("Residente no encontrado"))
                                .body("status", equalTo(404))
                                .body("path", equalTo("/organizaciones/" + orgId + "/residentes/"
                                                + residenteId))
                                .body("timestamp", not(emptyOrNullString()))
                                .body("details", nullValue());
        }

        @Test
        void create_deberiaMapear409_aErrorResponse() {
                UUID orgId = UUID.randomUUID();

                when(service.create(any(UUID.class), any(ResidenteUpsertRequest.class)))
                                .thenThrow(new WebApplicationException("Documento duplicado",
                                                Response.Status.CONFLICT));

                given().contentType("application/json").body("""
                                    {
                                      "nombre":"Juan",
                                      "tipoDocumento":"CC",
                                      "numeroDocumento":"123"
                                    }
                                """).when().post(String.format(BASE, orgId)).then().statusCode(409)
                                .body("code", equalTo("CONFLICT"))
                                .body("message", containsString("Documento duplicado"))
                                .body("status", equalTo(409))
                                .body("path", equalTo("/organizaciones/" + orgId + "/residentes"))
                                .body("timestamp", not(emptyOrNullString()))
                                .body("details", nullValue());
        }
}
