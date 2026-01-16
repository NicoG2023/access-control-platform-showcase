package com.haedcom.access.api.reglaacceso;

import java.util.UUID;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.reglaacceso.dto.ReglaAccesoEstadoRequest;
import com.haedcom.access.api.reglaacceso.dto.ReglaAccesoResponse;
import com.haedcom.access.api.reglaacceso.dto.ReglaAccesoSearchRequest;
import com.haedcom.access.api.reglaacceso.dto.ReglaAccesoUpsertRequest;
import com.haedcom.access.application.reglaacceso.ReglaAccesoService;
import com.haedcom.access.domain.enums.EstadoReglaAcceso;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Recurso REST para la gestión de {@code ReglaAcceso}.
 *
 * <p>
 * Exposición HTTP de los casos de uso de reglas de acceso bajo un esquema multi-tenant. Todas las
 * operaciones se ejecutan en el contexto de una organización ({@code orgId}) que se recibe
 * típicamente desde la ruta (path param).
 * </p>
 *
 * <h2>Responsabilidades</h2>
 * <ul>
 * <li>Parsear y validar inputs HTTP (path/query/body).</li>
 * <li>Mapear query params a {@link ReglaAccesoSearchRequest}.</li>
 * <li>Delegar la lógica al {@link ReglaAccesoService} (no contiene lógica de negocio).</li>
 * <li>Devolver respuestas HTTP consistentes.</li>
 * </ul>
 *
 * <h2>Notas</h2>
 * <ul>
 * <li>Los errores (400/404/409/500) se estandarizan mediante tus {@code ExceptionMapper}.</li>
 * <li>Este recurso asume que el tenant llega como {@code orgId} en la URL.</li>
 * </ul>
 */
@Path("/api/v1/organizaciones/{orgId}/reglas-acceso")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ReglaAccesoResource {

    private final ReglaAccesoService service;

    /**
     * Constructor del recurso.
     *
     * @param service servicio de aplicación de reglas de acceso
     */
    public ReglaAccesoResource(ReglaAccesoService service) {
        this.service = service;
    }

    /**
     * Lista reglas de acceso del tenant con filtros y paginación.
     *
     * <p>
     * Filtros soportados (opcionales):
     * </p>
     * <ul>
     * <li>{@code idArea}: limita al área específica.</li>
     * <li>{@code idDispositivo}: limita al dispositivo específico.</li>
     * <li>{@code tipoSujeto}: limita por tipo de sujeto (RESIDENTE/VISITANTE/etc.).</li>
     * <li>{@code estado}: limita por estado (ACTIVA/INACTIVA).</li>
     * </ul>
     *
     * <p>
     * Paginación:
     * </p>
     * <ul>
     * <li>{@code page}: índice base 0.</li>
     * <li>{@code size}: tamaño de página (1..200).</li>
     * </ul>
     *
     * @param orgId tenant (obligatorio)
     * @param idArea filtro opcional por área
     * @param idDispositivo filtro opcional por dispositivo
     * @param tipoSujeto filtro opcional por tipo de sujeto
     * @param estado filtro opcional por estado
     * @param page página (base 0)
     * @param size tamaño página
     * @return respuesta paginada con reglas y metadatos
     */
    @GET
    public PageResponse<ReglaAccesoResponse> list(@PathParam("orgId") @NotNull UUID orgId,
            @QueryParam("idArea") UUID idArea, @QueryParam("idDispositivo") UUID idDispositivo,
            @QueryParam("tipoSujeto") TipoSujetoAcceso tipoSujeto,
            @QueryParam("estado") EstadoReglaAcceso estado,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("25") @Min(1) @Max(200) int size) {
        ReglaAccesoSearchRequest filters =
                new ReglaAccesoSearchRequest(idArea, idDispositivo, tipoSujeto, estado);

        return service.list(orgId, filters, page, size);
    }

    /**
     * Obtiene una regla de acceso por id dentro del tenant.
     *
     * @param orgId tenant (obligatorio)
     * @param reglaId id de la regla (obligatorio)
     * @return regla encontrada
     */
    @GET
    @Path("/{reglaId}")
    public ReglaAccesoResponse get(@PathParam("orgId") @NotNull UUID orgId,
            @PathParam("reglaId") @NotNull UUID reglaId) {
        return service.get(orgId, reglaId);
    }

    /**
     * Crea una nueva regla de acceso en el tenant.
     *
     * <p>
     * El request soporta reglas con:
     * </p>
     * <ul>
     * <li>Scope por área (obligatorio) y opcionalmente por dispositivo.</li>
     * <li>Matching por tipo de sujeto (obligatorio).</li>
     * <li>Matching opcional por dirección y método de autenticación.</li>
     * <li>Acción (obligatoria).</li>
     * <li>Vigencia UTC opcional ({@code validoDesdeUtc/validoHastaUtc}).</li>
     * <li>Ventana diaria opcional ({@code desdeHoraLocal/hastaHoraLocal}) en HH:mm.</li>
     * </ul>
     *
     * @param orgId tenant
     * @param req request de creación
     * @return regla creada
     */
    @POST
    public Response create(@PathParam("orgId") @NotNull UUID orgId,
            @Valid ReglaAccesoUpsertRequest req) {
        ReglaAccesoResponse created = service.create(orgId, req);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /**
     * Actualiza una regla existente dentro del tenant.
     *
     * @param orgId tenant
     * @param reglaId id de la regla
     * @param req request de actualización
     * @return regla actualizada
     */
    @PUT
    @Path("/{reglaId}")
    public ReglaAccesoResponse update(@PathParam("orgId") @NotNull UUID orgId,
            @PathParam("reglaId") @NotNull UUID reglaId, @Valid ReglaAccesoUpsertRequest req) {
        return service.update(orgId, reglaId, req);
    }

    /**
     * Cambia el estado de una regla (soft delete / activar).
     *
     * <p>
     * Recomendado en lugar de borrar físicamente para preservar trazabilidad y auditoría.
     * </p>
     *
     * @param orgId tenant
     * @param reglaId id de la regla
     * @param req request con estado nuevo
     * @return regla con estado actualizado
     */
    @PATCH
    @Path("/{reglaId}/estado")
    public ReglaAccesoResponse changeEstado(@PathParam("orgId") @NotNull UUID orgId,
            @PathParam("reglaId") @NotNull UUID reglaId, @Valid ReglaAccesoEstadoRequest req) {
        return service.changeEstado(orgId, reglaId, req);
    }

    /**
     * Elimina una regla.
     *
     * <p>
     * En tu implementación de servicio, esto se resuelve como "soft delete" (INACTIVA) para no
     * perder trazabilidad.
     * </p>
     *
     * @param orgId tenant
     * @param reglaId id de la regla
     * @return 204 No Content
     */
    @DELETE
    @Path("/{reglaId}")
    public Response delete(@PathParam("orgId") @NotNull UUID orgId,
            @PathParam("reglaId") @NotNull UUID reglaId) {
        service.delete(orgId, reglaId);
        return Response.noContent().build();
    }
}
