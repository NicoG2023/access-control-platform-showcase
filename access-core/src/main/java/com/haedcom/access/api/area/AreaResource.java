package com.haedcom.access.api.area;

import java.net.URI;
import java.util.UUID;
import com.haedcom.access.api.area.dto.AreaResponse;
import com.haedcom.access.api.area.dto.AreaUpsertRequest;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.application.area.AreaService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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
 * Recurso REST para la gestión de {@code Area} dentro del contexto de una organización (tenant).
 *
 * <p>
 * Base path: {@code /organizaciones/{orgId}/areas}
 * </p>
 *
 * <p>
 * Un {@code Area} representa una unidad lógica dentro de una organización (por ejemplo: Torre A,
 * Recepción, Parqueadero, Piso 3, etc.). Su nombre es único dentro del tenant.
 * </p>
 *
 * <h2>Rutas</h2>
 * <ul>
 * <li>GET {@code /organizaciones/{orgId}/areas}</li>
 * <li>GET {@code /organizaciones/{orgId}/areas/{areaId}}</li>
 * <li>POST {@code /organizaciones/{orgId}/areas}</li>
 * <li>PUT {@code /organizaciones/{orgId}/areas/{areaId}}</li>
 * <li>DELETE {@code /organizaciones/{orgId}/areas/{areaId}}</li>
 * </ul>
 *
 * <h2>Paginación (GET list)</h2>
 * <ul>
 * <li>{@code page}: número de página (base 0). Default: 0</li>
 * <li>{@code size}: tamaño de página. Default: 20. Rango: [1..200]</li>
 * </ul>
 *
 * <h2>Convenciones de error</h2>
 * <p>
 * Los errores se serializan como JSON estándar mediante {@code ExceptionMapper}s globales:
 * </p>
 * <ul>
 * <li><b>400</b>: request inválido (Bean Validation, parámetros fuera de rango, etc.)</li>
 * <li><b>404</b>: organización/área no encontrada, o área no pertenece al tenant</li>
 * <li><b>409</b>: conflicto de unicidad (nombre duplicado dentro del tenant)</li>
 * </ul>
 */
@ApplicationScoped
@Path("/organizaciones/{orgId}/areas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AreaResource {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final AreaService service;

    /**
     * Inyección por constructor (preferida) para mejorar testabilidad y asegurar inmutabilidad.
     *
     * @param service servicio de aplicación para áreas
     */
    @Inject
    public AreaResource(AreaService service) {
        this.service = service;
    }

    /**
     * Lista áreas de una organización de forma paginada.
     *
     * <p>
     * Retorna un wrapper con:
     * </p>
     * <ul>
     * <li>{@code items}: elementos de la página</li>
     * <li>{@code total}: total de elementos disponibles para el tenant</li>
     * <li>{@code totalPages}, {@code hasNext}, {@code hasPrev}: navegación</li>
     * </ul>
     *
     * @param orgId identificador de la organización (tenant)
     * @param page número de página (base 0). Si no se envía, se usa 0.
     * @param size tamaño de página. Si no se envía, se usa 20.
     * @return respuesta paginada con áreas y metadatos
     */
    @GET
    public PageResponse<AreaResponse> list(@PathParam("orgId") UUID orgId,
            @QueryParam("page") @Min(0) Integer page,
            @QueryParam("size") @Min(1) @Max(200) Integer size) {

        int p = (page == null) ? DEFAULT_PAGE : page;
        int s = (size == null) ? DEFAULT_SIZE : size;

        return service.list(orgId, p, s);
    }

    /**
     * Obtiene el detalle de un área específica dentro de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param areaId identificador del área
     * @return DTO del área
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece a la organización
     */
    @GET
    @Path("/{areaId}")
    public AreaResponse get(@PathParam("orgId") UUID orgId, @PathParam("areaId") UUID areaId) {
        return service.get(orgId, areaId);
    }

    /**
     * Crea un área dentro de una organización.
     *
     * <p>
     * Devuelve:
     * </p>
     * <ul>
     * <li>201 Created</li>
     * <li>Header {@code Location} apuntando al recurso creado</li>
     * <li>Body con el área creada</li>
     * </ul>
     *
     * @param orgId identificador de la organización (tenant)
     * @param req payload de creación (validado con Bean Validation)
     * @param uriInfo contexto para construir el header {@code Location}
     * @return respuesta 201 con {@code Location} y entidad creada
     */
    @POST
    public Response create(@PathParam("orgId") UUID orgId, @Valid AreaUpsertRequest req,
            @Context UriInfo uriInfo) {

        AreaResponse created = service.create(orgId, req);

        URI location = uriInfo.getAbsolutePathBuilder().path(created.idArea().toString()).build();

        return Response.created(location).entity(created).build();
    }

    /**
     * Actualiza la información de un área existente.
     *
     * @param orgId identificador de la organización (tenant)
     * @param areaId identificador del área
     * @param req payload con los nuevos datos (validado con Bean Validation)
     * @return área actualizada
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece a la organización
     * @throws jakarta.ws.rs.WebApplicationException 409 si el nombre entra en conflicto
     */
    @PUT
    @Path("/{areaId}")
    public AreaResponse update(@PathParam("orgId") UUID orgId, @PathParam("areaId") UUID areaId,
            @Valid AreaUpsertRequest req) {

        return service.update(orgId, areaId, req);
    }

    /**
     * Elimina un área de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param areaId identificador del área
     * @return 204 No Content si se eliminó correctamente
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece a la organización
     */
    @DELETE
    @Path("/{areaId}")
    public Response delete(@PathParam("orgId") UUID orgId, @PathParam("areaId") UUID areaId) {
        service.delete(orgId, areaId);
        return Response.noContent().build();
    }
}
