package com.haedcom.access.api.dispositivo;

import java.net.URI;
import java.util.UUID;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.dispositivo.dto.DispositivoResponse;
import com.haedcom.access.api.dispositivo.dto.DispositivoUpsertRequest;
import com.haedcom.access.application.dispositivo.DispositivoService;
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
 * Recurso REST para la gestión de {@code Dispositivo} dentro del contexto de una organización
 * (tenant).
 *
 * <p>
 * Base path: {@code /organizaciones/{orgId}/dispositivos}
 * </p>
 *
 * <p>
 * Un {@code Dispositivo} representa un elemento físico o lógico (ej. lector biométrico, cámara,
 * panel de acceso, etc.) asociado a un {@code Area} dentro de una organización.
 * </p>
 *
 * <h2>Rutas</h2>
 * <ul>
 * <li>GET {@code /organizaciones/{orgId}/dispositivos}</li>
 * <li>GET {@code /organizaciones/{orgId}/dispositivos/{dispositivoId}}</li>
 * <li>POST {@code /organizaciones/{orgId}/dispositivos}</li>
 * <li>PUT {@code /organizaciones/{orgId}/dispositivos/{dispositivoId}}</li>
 * <li>DELETE {@code /organizaciones/{orgId}/dispositivos/{dispositivoId}}</li>
 * </ul>
 *
 * <h2>Filtros y paginación (GET list)</h2>
 * <ul>
 * <li>{@code areaId} (opcional): filtra dispositivos por área</li>
 * <li>{@code page}/{@code size}: paginación (base 0)</li>
 * </ul>
 *
 * <h2>Convenciones de error</h2>
 * <p>
 * Los errores se serializan como JSON estándar mediante {@code ExceptionMapper}s globales:
 * </p>
 * <ul>
 * <li><b>400</b>: request inválido (Bean Validation, parámetros fuera de rango)</li>
 * <li><b>404</b>: organización/dispositivo/área no encontrada o no pertenece al tenant</li>
 * <li><b>409</b>: conflicto de unicidad (identificador externo duplicado)</li>
 * </ul>
 */
@ApplicationScoped
@Path("/organizaciones/{orgId}/dispositivos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DispositivoResource {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final DispositivoService service;

    /**
     * Inyección por constructor (preferida) para mejorar testabilidad y asegurar inmutabilidad.
     *
     * @param service servicio de aplicación para dispositivos
     */
    @Inject
    public DispositivoResource(DispositivoService service) {
        this.service = service;
    }

    /**
     * Lista dispositivos de una organización de forma paginada.
     *
     * <p>
     * Retorna un wrapper con:
     * </p>
     * <ul>
     * <li>{@code items}: dispositivos de la página</li>
     * <li>{@code total}: total de dispositivos disponibles</li>
     * <li>{@code totalPages}, {@code hasNext}, {@code hasPrev}</li>
     * </ul>
     *
     * @param orgId identificador de la organización (tenant)
     * @param areaId filtra por área (opcional)
     * @param page número de página (base 0). Default: 0
     * @param size tamaño de página. Default: 20
     * @return respuesta paginada con dispositivos
     */
    @GET
    public PageResponse<DispositivoResponse> list(@PathParam("orgId") UUID orgId,
            @QueryParam("areaId") UUID areaId, @QueryParam("page") @Min(0) Integer page,
            @QueryParam("size") @Min(1) @Max(200) Integer size) {

        int p = (page == null) ? DEFAULT_PAGE : page;
        int s = (size == null) ? DEFAULT_SIZE : size;

        return service.list(orgId, areaId, p, s);
    }

    /**
     * Obtiene el detalle de un dispositivo específico dentro de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param dispositivoId identificador del dispositivo
     * @return DTO del dispositivo
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece al tenant
     */
    @GET
    @Path("/{dispositivoId}")
    public DispositivoResponse get(@PathParam("orgId") UUID orgId,
            @PathParam("dispositivoId") UUID dispositivoId) {

        return service.get(orgId, dispositivoId);
    }

    /**
     * Crea un dispositivo dentro de una organización.
     *
     * <p>
     * Devuelve:
     * </p>
     * <ul>
     * <li>201 Created</li>
     * <li>Header {@code Location} apuntando al recurso creado</li>
     * <li>Body con el dispositivo creado</li>
     * </ul>
     *
     * @param orgId identificador de la organización (tenant)
     * @param req payload de creación (validado con Bean Validation)
     * @param uriInfo contexto para construir el header {@code Location}
     * @return respuesta 201 con {@code Location} y entidad creada
     */
    @POST
    public Response create(@PathParam("orgId") UUID orgId, @Valid DispositivoUpsertRequest req,
            @Context UriInfo uriInfo) {

        DispositivoResponse created = service.create(orgId, req);

        URI location =
                uriInfo.getAbsolutePathBuilder().path(created.idDispositivo().toString()).build();

        return Response.created(location).entity(created).build();
    }

    /**
     * Actualiza la información de un dispositivo existente.
     *
     * @param orgId identificador de la organización (tenant)
     * @param dispositivoId identificador del dispositivo
     * @param req payload con los nuevos datos (validado con Bean Validation)
     * @return dispositivo actualizado
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece al tenant
     * @throws jakarta.ws.rs.WebApplicationException 409 si el identificador externo entra en
     *         conflicto
     */
    @PUT
    @Path("/{dispositivoId}")
    public DispositivoResponse update(@PathParam("orgId") UUID orgId,
            @PathParam("dispositivoId") UUID dispositivoId, @Valid DispositivoUpsertRequest req) {

        return service.update(orgId, dispositivoId, req);
    }

    /**
     * Elimina un dispositivo de una organización (delete físico).
     *
     * @param orgId identificador de la organización (tenant)
     * @param dispositivoId identificador del dispositivo
     * @return 204 No Content si se eliminó correctamente
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe o no pertenece al tenant
     */
    @DELETE
    @Path("/{dispositivoId}")
    public Response delete(@PathParam("orgId") UUID orgId,
            @PathParam("dispositivoId") UUID dispositivoId) {

        service.delete(orgId, dispositivoId);
        return Response.noContent().build();
    }
}
