package com.haedcom.access.api.visitante;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
import com.haedcom.access.api.visitante.dto.VisitantePreautorizadoResponse;
import com.haedcom.access.api.visitante.dto.VisitantePreautorizadoUpsertRequest;
import com.haedcom.access.application.visitantePreautorizado.VisitantePreautorizadoService;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@QuarkusTest
class VisitantePreautorizadoResourceTest {

    @InjectMock
    VisitantePreautorizadoService service;

    private static final String BASE = "/organizaciones/%s/visitantes-preautorizados";

    @BeforeEach
    void setup() {
        Mockito.reset(service);
    }

    private VisitantePreautorizadoResponse resp(UUID visitanteId, UUID orgId, UUID residenteId,
            String nombre, TipoDocumentoIdentidad tipo, String numero) {

        OffsetDateTime now = OffsetDateTime.now();
        return new VisitantePreautorizadoResponse(visitanteId, orgId, residenteId, nombre, tipo,
                numero, null, null, now, now);
    }

    // -------------------------
    // list
    // -------------------------

    @Test
    void list_deberiaRetornar200_yPageResponse_conDefaults() {
        UUID orgId = UUID.randomUUID();

        var v1 = resp(UUID.randomUUID(), orgId, null, "Ana", TipoDocumentoIdentidad.CC, "1");
        var v2 = resp(UUID.randomUUID(), orgId, UUID.randomUUID(), "Beto",
                TipoDocumentoIdentidad.PA, "2");

        // page=0, size=20 (defaults del resource)
        PageResponse<VisitantePreautorizadoResponse> page =
                PageResponse.of(List.of(v1, v2), 0, 20, 2);

        when(service.list(eq(orgId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(0), eq(20))).thenReturn(page);

        given().when().get(String.format(BASE, orgId)).then().statusCode(200)
                // wrapper
                .body("items", hasSize(2)).body("total", equalTo(2)).body("page", equalTo(0))
                .body("size", equalTo(20))
                // contenido
                .body("items[0].idOrganizacion", equalTo(orgId.toString()))
                .body("items[1].idOrganizacion", equalTo(orgId.toString()));
    }

    @Test
    void list_deberiaPasarFiltros_yPaginacion_alServicio() {
        UUID orgId = UUID.randomUUID();
        UUID residenteId = UUID.randomUUID();

        String q = "juan";
        TipoDocumentoIdentidad tipo = TipoDocumentoIdentidad.CC;
        String numero = "123";
        String sort = "nombre";
        String dir = "desc";
        int pageNum = 2;
        int sizeNum = 5;

        PageResponse<VisitantePreautorizadoResponse> out =
                PageResponse.of(List.of(), pageNum, sizeNum, 0);

        when(service.list(eq(orgId), eq(residenteId), eq(q), eq(tipo), eq(numero), eq(sort),
                eq(dir), eq(pageNum), eq(sizeNum))).thenReturn(out);

        given().queryParam("residenteId", residenteId).queryParam("q", q)
                .queryParam("tipoDocumento", "CC").queryParam("numeroDocumento", numero)
                .queryParam("sort", sort).queryParam("dir", dir).queryParam("page", pageNum)
                .queryParam("size", sizeNum).when().get(String.format(BASE, orgId)).then()
                .statusCode(200).body("items", hasSize(0)).body("total", equalTo(0))
                .body("page", equalTo(pageNum)).body("size", equalTo(sizeNum));
    }

    // -------------------------
    // get
    // -------------------------

    @Test
    void get_deberiaRetornar200() {
        UUID orgId = UUID.randomUUID();
        UUID visitanteId = UUID.randomUUID();
        UUID residenteId = UUID.randomUUID();

        var out = resp(visitanteId, orgId, residenteId, "Juan", TipoDocumentoIdentidad.CC, "123");

        when(service.get(orgId, visitanteId)).thenReturn(out);

        given().when().get(String.format(BASE, orgId) + "/" + visitanteId).then().statusCode(200)
                .body("idVisitante", equalTo(visitanteId.toString()))
                .body("idOrganizacion", equalTo(orgId.toString()))
                .body("idResidente", equalTo(residenteId.toString()))
                .body("nombre", equalTo("Juan")).body("tipoDocumento", equalTo("CC"))
                .body("numeroDocumento", equalTo("123"));
    }

    @Test
    void get_deberiaRetornar200_conResidenteNull() {
        UUID orgId = UUID.randomUUID();
        UUID visitanteId = UUID.randomUUID();

        var out = resp(visitanteId, orgId, null, "Juan", TipoDocumentoIdentidad.CE, "999");

        when(service.get(orgId, visitanteId)).thenReturn(out);

        given().when().get(String.format(BASE, orgId) + "/" + visitanteId).then().statusCode(200)
                .body("idVisitante", equalTo(visitanteId.toString()))
                .body("idOrganizacion", equalTo(orgId.toString())).body("idResidente", nullValue())
                .body("tipoDocumento", equalTo("CE")).body("numeroDocumento", equalTo("999"));
    }

    // -------------------------
    // create
    // -------------------------

    @Test
    void create_deberiaRetornar201_yLocation() {
        UUID orgId = UUID.randomUUID();
        UUID visitanteId = UUID.randomUUID();

        var created = resp(visitanteId, orgId, null, "Juan", TipoDocumentoIdentidad.CC, "123");

        when(service.create(any(UUID.class), any(VisitantePreautorizadoUpsertRequest.class)))
                .thenReturn(created);

        given().contentType("application/json").body("""
                    {
                      "idResidente": null,
                      "nombre":"Juan",
                      "tipoDocumento":"CC",
                      "numeroDocumento":"123",
                      "correo": null,
                      "telefono": null
                    }
                """).when().post(String.format(BASE, orgId)).then().statusCode(201).header(
                "Location",
                allOf(containsString("/organizaciones/" + orgId + "/visitantes-preautorizados/"),
                        endsWith("/visitantes-preautorizados/" + visitanteId)))
                .body("idVisitante", equalTo(visitanteId.toString()))
                .body("idOrganizacion", equalTo(orgId.toString())).body("idResidente", nullValue());
    }

    @Test
    void create_deberiaRetornar400_siFallaValidacion_yTraerDetails() {
        UUID orgId = UUID.randomUUID();

        // nombre blank y numeroDocumento blank => Bean Validation debe disparar 400
        given().contentType("application/json").body("""
                    {
                      "idResidente": null,
                      "nombre":"   ",
                      "tipoDocumento":"CC",
                      "numeroDocumento":""
                    }
                """).when().post(String.format(BASE, orgId)).then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", equalTo("Error de validación en el request"))
                .body("status", equalTo(400))
                .body("path", equalTo("/organizaciones/" + orgId + "/visitantes-preautorizados"))
                .body("timestamp", not(emptyOrNullString())).body("details", notNullValue())
                .body("details.size()", greaterThanOrEqualTo(1))
                .body("details.field", hasSize(greaterThanOrEqualTo(1)));
    }

    // -------------------------
    // update
    // -------------------------

    @Test
    void update_deberiaRetornar200() {
        UUID orgId = UUID.randomUUID();
        UUID visitanteId = UUID.randomUUID();
        UUID residenteId = UUID.randomUUID();

        var updated =
                resp(visitanteId, orgId, residenteId, "Nuevo", TipoDocumentoIdentidad.PA, "999");

        when(service.update(any(UUID.class), any(UUID.class),
                any(VisitantePreautorizadoUpsertRequest.class))).thenReturn(updated);

        given().contentType("application/json").body("""
                    {
                      "idResidente":"%s",
                      "nombre":"Nuevo",
                      "tipoDocumento":"PA",
                      "numeroDocumento":"999",
                      "correo": null,
                      "telefono": null
                    }
                """.formatted(residenteId)).when()
                .put(String.format(BASE, orgId) + "/" + visitanteId).then().statusCode(200)
                .body("nombre", equalTo("Nuevo")).body("tipoDocumento", equalTo("PA"))
                .body("numeroDocumento", equalTo("999"))
                .body("idResidente", equalTo(residenteId.toString()));
    }

    // -------------------------
    // delete
    // -------------------------

    @Test
    void delete_deberiaRetornar204() {
        UUID orgId = UUID.randomUUID();
        UUID visitanteId = UUID.randomUUID();

        doNothing().when(service).delete(any(UUID.class), any(UUID.class));

        given().when().delete(String.format(BASE, orgId) + "/" + visitanteId).then().statusCode(204)
                .body(emptyOrNullString());
    }

    // -------------------------
    // error mapping
    // -------------------------

    @Test
    void get_deberiaMapear404_aErrorResponse() {
        UUID orgId = UUID.randomUUID();
        UUID visitanteId = UUID.randomUUID();

        when(service.get(any(UUID.class), any(UUID.class)))
                .thenThrow(new NotFoundException("Visitante preautorizado no encontrado"));

        given().when().get(String.format(BASE, orgId) + "/" + visitanteId).then().statusCode(404)
                .body("code", equalTo("NOT_FOUND"))
                .body("message", containsString("Visitante preautorizado no encontrado"))
                .body("status", equalTo(404))
                .body("path",
                        equalTo("/organizaciones/" + orgId + "/visitantes-preautorizados/"
                                + visitanteId))
                .body("timestamp", not(emptyOrNullString())).body("details", nullValue());
    }

    @Test
    void create_deberiaMapear409_aErrorResponse() {
        UUID orgId = UUID.randomUUID();

        when(service.create(any(UUID.class), any(VisitantePreautorizadoUpsertRequest.class)))
                .thenThrow(new WebApplicationException("Documento duplicado",
                        Response.Status.CONFLICT));

        given().contentType("application/json").body("""
                    {
                      "idResidente": null,
                      "nombre":"Juan",
                      "tipoDocumento":"CC",
                      "numeroDocumento":"123"
                    }
                """).when().post(String.format(BASE, orgId)).then().statusCode(409)
                .body("code", equalTo("CONFLICT"))
                .body("message", containsString("Documento duplicado")).body("status", equalTo(409))
                .body("path", equalTo("/organizaciones/" + orgId + "/visitantes-preautorizados"))
                .body("timestamp", not(emptyOrNullString())).body("details", nullValue());
    }

    @Test
    void list_deberiaRetornar400_siPageEsNegativa() {
        UUID orgId = UUID.randomUUID();

        given().queryParam("page", -1).when().get(String.format(BASE, orgId)).then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void list_deberiaRetornar400_siSizeFueraDeRango() {
        UUID orgId = UUID.randomUUID();

        given().queryParam("size", 999).when().get(String.format(BASE, orgId)).then()
                .statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void list_deberiaRetornar400_siTipoDocumentoInvalido() {
        UUID orgId = UUID.randomUUID();

        // JAX-RS no puede convertir "XX" a enum -> típicamente 400
        given().queryParam("tipoDocumento", "XX").when().get(String.format(BASE, orgId)).then()
                .statusCode(anyOf(equalTo(400), equalTo(404))); // depende de tu mapper/config
    }
}
