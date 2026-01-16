package com.haedcom.access.api.residente;

import java.net.URI;
import java.util.UUID;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.residente.dto.ResidenteEstadoRequest;
import com.haedcom.access.api.residente.dto.ResidenteResponse;
import com.haedcom.access.api.residente.dto.ResidenteUpsertRequest;
import com.haedcom.access.application.residente.ResidenteService;
import com.haedcom.access.domain.enums.EstadoResidente;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Recurso REST para la gestión de residentes dentro del contexto de una organización (tenant).
 *
 * <p>
 * Base path: {@code /organizaciones/{orgId}/residentes}
 * </p>
 *
 * <p>
 * Rutas:
 * <ul>
 * <li>GET /organizaciones/{orgId}/residentes</li>
 * <li>GET /organizaciones/{orgId}/residentes/{residenteId}</li>
 * <li>POST /organizaciones/{orgId}/residentes</li>
 * <li>PUT /organizaciones/{orgId}/residentes/{residenteId}</li>
 * <li>DELETE /organizaciones/{orgId}/residentes/{residenteId}</li>
 * </ul>
 * </p>
 *
 * <p>
 * Convenciones de error (manejadas por {@code ExceptionMapper}s globales):
 * <ul>
 * <li>400: request inválido (Bean Validation, parámetros fuera de rango, etc.)</li>
 * <li>404: organización o residente no encontrado, o residente no pertenece al tenant</li>
 * <li>409: conflicto de unicidad (documento duplicado)</li>
 * </ul>
 * </p>
 */
@ApplicationScoped
@Path("/organizaciones/{orgId}/residentes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ResidenteResource {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final ResidenteService service;

    /**
     * Inyección por constructor (preferida) para mejorar testabilidad y asegurar inmutabilidad.
     *
     * @param service servicio de aplicación para residentes
     */
    @Inject
    public ResidenteResource(ResidenteService service) {
        this.service = service;
    }

    /**
     * Lista residentes de una organización de forma paginada, devolviendo metadatos útiles para
     * frontend.
     *
     * <p>
     * Retorna un wrapper con:
     * <ul>
     * <li>{@code items}: elementos de la página</li>
     * <li>{@code total}: total de elementos para el tenant</li>
     * <li>{@code totalPages}, {@code hasNext}, {@code hasPrev}</li>
     * </ul>
     * </p>
     */
    @GET
    public PageResponse<ResidenteResponse> list(@PathParam("orgId") UUID orgId,
            @QueryParam("q") String q,
            @QueryParam("tipoDocumento") TipoDocumentoIdentidad tipoDocumento,
            @QueryParam("numeroDocumento") String numeroDocumento,
            @QueryParam("estado") EstadoResidente estado, @QueryParam("sort") String sort,
            @QueryParam("dir") String dir, @QueryParam("page") @Min(0) Integer page,
            @QueryParam("size") @Min(1) @Max(200) Integer size) {

        int p = (page == null) ? DEFAULT_PAGE : page;
        int s = (size == null) ? DEFAULT_SIZE : size;

        return service.list(orgId, q, tipoDocumento, numeroDocumento, estado, sort, dir, p, s);
    }



    /**
     * Obtiene el detalle de un residente específico dentro de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId identificador del residente
     * @return DTO del residente
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece a la organización
     */
    @GET
    @Path("/{residenteId}")
    public ResidenteResponse get(@PathParam("orgId") UUID orgId,
            @PathParam("residenteId") UUID residenteId) {

        return service.get(orgId, residenteId);
    }

    /**
     * Crea un residente dentro de una organización.
     *
     * <p>
     * Devuelve:
     * <ul>
     * <li>201 Created</li>
     * <li>Header {@code Location} apuntando al recurso creado</li>
     * <li>Body con el residente creado</li>
     * </ul>
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param req payload de creación (validado con Bean Validation)
     * @param uriInfo contexto para construir el header {@code Location}
     * @return respuesta 201 con {@code Location} y entidad creada
     */
    @POST
    public Response create(@PathParam("orgId") UUID orgId, @Valid ResidenteUpsertRequest req,
            @Context UriInfo uriInfo) {

        ResidenteResponse created = service.create(orgId, req);

        URI location =
                uriInfo.getAbsolutePathBuilder().path(created.idResidente().toString()).build();

        return Response.created(location).entity(created).build();
    }

    /**
     * Actualiza la información de un residente existente.
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId identificador del residente
     * @param req payload con los nuevos datos (validado con Bean Validation)
     * @return residente actualizado
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece a la organización
     * @throws jakarta.ws.rs.WebApplicationException 409 si el documento entra en conflicto
     */
    @PUT
    @Path("/{residenteId}")
    public ResidenteResponse update(@PathParam("orgId") UUID orgId,
            @PathParam("residenteId") UUID residenteId, @Valid ResidenteUpsertRequest req) {

        return service.update(orgId, residenteId, req);
    }

    /**
     * Elimina un residente de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId identificador del residente
     * @return 204 No Content si se eliminó correctamente
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece a la organización
     */
    @DELETE
    @Path("/{residenteId}")
    public Response delete(@PathParam("orgId") UUID orgId,
            @PathParam("residenteId") UUID residenteId) {

        service.delete(orgId, residenteId);
        return Response.noContent().build();
    }

    /**
     * Actualiza el estado de un residente (caso de uso explícito, separado del upsert).
     *
     * <p>
     * Ruta: {@code PATCH /organizaciones/{orgId}/residentes/{residenteId}/estado}
     * </p>
     *
     * <p>
     * Convenciones de error (manejadas por ExceptionMappers globales):
     * <ul>
     * <li>400: request inválido (Bean Validation)</li>
     * <li>404: residente no encontrado o no pertenece al tenant</li>
     * </ul>
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId identificador del residente
     * @param req payload con el nuevo estado (validado con Bean Validation)
     * @return residente con el estado actualizado
     */
    @PATCH
    @Path("/{residenteId}/estado")
    public ResidenteResponse updateEstado(@PathParam("orgId") UUID orgId,
            @PathParam("residenteId") UUID residenteId, @Valid ResidenteEstadoRequest req) {

        return service.updateEstado(orgId, residenteId, req);
    }

}
