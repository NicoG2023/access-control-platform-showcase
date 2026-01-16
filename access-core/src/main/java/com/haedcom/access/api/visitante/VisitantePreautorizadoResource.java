package com.haedcom.access.api.visitante;

import java.net.URI;
import java.util.UUID;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.visitante.dto.VisitantePreautorizadoResponse;
import com.haedcom.access.api.visitante.dto.VisitantePreautorizadoUpsertRequest;
import com.haedcom.access.application.visitantePreautorizado.VisitantePreautorizadoService;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
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
 * Recurso REST para la gestión de visitantes preautorizados dentro del contexto de una organización
 * (tenant).
 *
 * <p>
 * Base path: {@code /organizaciones/{orgId}/visitantes-preautorizados}
 * </p>
 *
 * <p>
 * Un {@code VisitantePreautorizado} representa un visitante permitido por un residente (anfitrión)
 * dentro de una organización. La asociación al residente es opcional.
 * </p>
 *
 * <h2>Rutas</h2>
 * <ul>
 * <li>GET {@code /organizaciones/{orgId}/visitantes-preautorizados}</li>
 * <li>GET {@code /organizaciones/{orgId}/visitantes-preautorizados/{visitanteId}}</li>
 * <li>POST {@code /organizaciones/{orgId}/visitantes-preautorizados}</li>
 * <li>PUT {@code /organizaciones/{orgId}/visitantes-preautorizados/{visitanteId}}</li>
 * <li>DELETE {@code /organizaciones/{orgId}/visitantes-preautorizados/{visitanteId}}</li>
 * </ul>
 *
 * <h2>Convenciones de error</h2>
 * <p>
 * Los errores se serializan como JSON estándar mediante {@code ExceptionMapper}s globales:
 * </p>
 * <ul>
 * <li><b>400</b>: request inválido (Bean Validation, parámetros fuera de rango, etc.)</li>
 * <li><b>404</b>: organización/visitante no encontrado, o visitante no pertenece al tenant</li>
 * <li><b>409</b>: conflicto de unicidad (documento duplicado dentro del tenant)</li>
 * </ul>
 *
 * <h2>Filtros y paginación (GET list)</h2>
 * <ul>
 * <li>{@code residenteId} (opcional): filtra por anfitrión/residente asociado</li>
 * <li>{@code q} (opcional): búsqueda libre (parcial) sobre campos soportados por el
 * repositorio</li>
 * <li>{@code tipoDocumento} (opcional)</li>
 * <li>{@code numeroDocumento} (opcional, exacto)</li>
 * <li>{@code sort}/{@code dir}: ordenamiento controlado por whitelist en el repositorio</li>
 * <li>{@code page}/{@code size}: paginación (base 0)</li>
 * </ul>
 */
@ApplicationScoped
@Path("/organizaciones/{orgId}/visitantes-preautorizados")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VisitantePreautorizadoResource {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final VisitantePreautorizadoService service;

    /**
     * Inyección por constructor (preferida) para mejorar testabilidad y asegurar inmutabilidad.
     *
     * @param service servicio de aplicación para visitantes preautorizados
     */
    @Inject
    public VisitantePreautorizadoResource(VisitantePreautorizadoService service) {
        this.service = service;
    }

    /**
     * Lista visitantes preautorizados de una organización de forma paginada, devolviendo metadatos
     * útiles para frontend.
     *
     * <p>
     * Retorna un wrapper con:
     * </p>
     * <ul>
     * <li>{@code items}: elementos de la página</li>
     * <li>{@code total}: total de elementos disponibles para el tenant y filtros actuales</li>
     * <li>{@code totalPages}, {@code hasNext}, {@code hasPrev}: navegación</li>
     * </ul>
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId filtra por residente asociado (opcional)
     * @param q término de búsqueda libre (opcional)
     * @param tipoDocumento filtro por tipo de documento (opcional)
     * @param numeroDocumento filtro por documento exacto (opcional)
     * @param sort campo de ordenamiento (opcional; whitelist en repositorio)
     * @param dir dirección del ordenamiento (opcional; {@code asc} o {@code desc})
     * @param page número de página (base 0). Si no se envía, se usa 0.
     * @param size tamaño de página. Si no se envía, se usa 20.
     * @return respuesta paginada con visitantes y metadatos
     */
    @GET
    public PageResponse<VisitantePreautorizadoResponse> list(@PathParam("orgId") UUID orgId,
            @QueryParam("residenteId") UUID residenteId, @QueryParam("q") String q,
            @QueryParam("tipoDocumento") TipoDocumentoIdentidad tipoDocumento,
            @QueryParam("numeroDocumento") String numeroDocumento, @QueryParam("sort") String sort,
            @QueryParam("dir") String dir, @QueryParam("page") @Min(0) Integer page,
            @QueryParam("size") @Min(1) @Max(200) Integer size) {

        int p = (page == null) ? DEFAULT_PAGE : page;
        int s = (size == null) ? DEFAULT_SIZE : size;

        return service.list(orgId, residenteId, q, tipoDocumento, numeroDocumento, sort, dir, p, s);
    }

    /**
     * Obtiene el detalle de un visitante preautorizado específico dentro de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param visitanteId identificador del visitante
     * @return DTO del visitante preautorizado
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece a la organización
     */
    @GET
    @Path("/{visitanteId}")
    public VisitantePreautorizadoResponse get(@PathParam("orgId") UUID orgId,
            @PathParam("visitanteId") UUID visitanteId) {

        return service.get(orgId, visitanteId);
    }

    /**
     * Crea un visitante preautorizado dentro de una organización.
     *
     * <p>
     * Devuelve:
     * </p>
     * <ul>
     * <li>201 Created</li>
     * <li>Header {@code Location} apuntando al recurso creado</li>
     * <li>Body con el visitante creado</li>
     * </ul>
     *
     * @param orgId identificador de la organización (tenant)
     * @param req payload de creación (validado con Bean Validation)
     * @param uriInfo contexto para construir el header {@code Location}
     * @return respuesta 201 con {@code Location} y entidad creada
     */
    @POST
    public Response create(@PathParam("orgId") UUID orgId,
            @Valid VisitantePreautorizadoUpsertRequest req, @Context UriInfo uriInfo) {

        VisitantePreautorizadoResponse created = service.create(orgId, req);

        URI location =
                uriInfo.getAbsolutePathBuilder().path(created.idVisitante().toString()).build();

        return Response.created(location).entity(created).build();
    }

    /**
     * Actualiza la información de un visitante preautorizado existente.
     *
     * @param orgId identificador de la organización (tenant)
     * @param visitanteId identificador del visitante
     * @param req payload con los nuevos datos (validado con Bean Validation)
     * @return visitante actualizado
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece a la organización
     * @throws jakarta.ws.rs.WebApplicationException 409 si el documento entra en conflicto
     */
    @PUT
    @Path("/{visitanteId}")
    public VisitantePreautorizadoResponse update(@PathParam("orgId") UUID orgId,
            @PathParam("visitanteId") UUID visitanteId,
            @Valid VisitantePreautorizadoUpsertRequest req) {

        return service.update(orgId, visitanteId, req);
    }

    /**
     * Elimina un visitante preautorizado de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param visitanteId identificador del visitante
     * @return 204 No Content si se eliminó correctamente
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece a la organización
     */
    @DELETE
    @Path("/{visitanteId}")
    public Response delete(@PathParam("orgId") UUID orgId,
            @PathParam("visitanteId") UUID visitanteId) {

        service.delete(orgId, visitanteId);
        return Response.noContent().build();
    }
}
