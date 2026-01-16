package com.haedcom.access.api.grupo_residentes;

import java.net.URI;
import java.util.UUID;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesAddResidentesRequest;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesEstadoRequest;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesRemoveResidentesRequest;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesReplaceResidentesRequest;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesResponse;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesUpsertRequest;
import com.haedcom.access.application.grupo_residentes.GrupoResidentesService;
import com.haedcom.access.domain.enums.EstadoGrupo;
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
 * Recurso REST para la gestión de grupos de residentes dentro del contexto de una organización
 * (tenant).
 *
 * <p>
 * Base path: {@code /organizaciones/{orgId}/grupos-residentes}
 * </p>
 *
 * <h3>Rutas principales</h3>
 * <ul>
 * <li>GET /organizaciones/{orgId}/grupos-residentes</li>
 * <li>GET /organizaciones/{orgId}/grupos-residentes/{grupoId}</li>
 * <li>POST /organizaciones/{orgId}/grupos-residentes</li>
 * <li>PUT /organizaciones/{orgId}/grupos-residentes/{grupoId}</li>
 * <li>DELETE /organizaciones/{orgId}/grupos-residentes/{grupoId}</li>
 * <li>PATCH /organizaciones/{orgId}/grupos-residentes/{grupoId}/estado</li>
 * </ul>
 *
 * <h3>Gestión de membresía (residentes del grupo)</h3>
 * <ul>
 * <li>POST /organizaciones/{orgId}/grupos-residentes/{grupoId}/residentes (agregar)</li>
 * <li>DELETE /organizaciones/{orgId}/grupos-residentes/{grupoId}/residentes (eliminar)</li>
 * <li>PUT /organizaciones/{orgId}/grupos-residentes/{grupoId}/residentes (reemplazar)</li>
 * </ul>
 *
 * <h3>Convenciones de error</h3>
 * <p>
 * Los errores son manejados por {@code ExceptionMapper}s globales. Convenciones sugeridas:
 * </p>
 * <ul>
 * <li>400: request inválido (Bean Validation, parámetros fuera de rango, residentes inválidos)</li>
 * <li>404: grupo no encontrado o no pertenece al tenant</li>
 * <li>409: conflicto de unicidad (nombre duplicado)</li>
 * </ul>
 */
@ApplicationScoped
@Path("/organizaciones/{orgId}/grupos-residentes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GrupoResidentesResource {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final GrupoResidentesService service;

    /**
     * Inyección por constructor (preferida) para testabilidad e inmutabilidad.
     */
    @Inject
    public GrupoResidentesResource(GrupoResidentesService service) {
        this.service = service;
    }

    /**
     * Lista grupos de residentes de una organización de forma paginada.
     *
     * <p>
     * Filtros soportados:
     * <ul>
     * <li>{@code q}: búsqueda parcial por nombre (case-insensitive)</li>
     * <li>{@code estado}: estado del grupo</li>
     * </ul>
     * </p>
     *
     * @param orgId identificador del tenant
     * @param q búsqueda por nombre (opcional)
     * @param estado filtro por estado (opcional)
     * @param page número de página (base 0)
     * @param size tamaño de página (1..200)
     */
    @GET
    public PageResponse<GrupoResidentesResponse> list(@PathParam("orgId") UUID orgId,
            @QueryParam("q") String q, @QueryParam("estado") EstadoGrupo estado,
            @QueryParam("page") @Min(0) Integer page,
            @QueryParam("size") @Min(1) @Max(200) Integer size) {
        int p = (page == null) ? DEFAULT_PAGE : page;
        int s = (size == null) ? DEFAULT_SIZE : size;

        return service.list(orgId, q, estado, p, s);
    }

    /**
     * Obtiene el detalle de un grupo específico.
     *
     * <p>
     * Query param:
     * <ul>
     * <li>{@code includeResidentes}: si {@code true}, incluye la lista de residentes del grupo</li>
     * </ul>
     * </p>
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @param includeResidentes si true, retorna detalle con residentes
     * @return grupo (lite o full según {@code includeResidentes})
     */
    @GET
    @Path("/{grupoId}")
    public GrupoResidentesResponse get(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId,
            @QueryParam("includeResidentes") Boolean includeResidentes) {
        boolean include = (includeResidentes != null && includeResidentes);
        return service.get(orgId, grupoId, include);
    }

    /**
     * Crea un grupo de residentes dentro de una organización.
     *
     * <p>
     * Devuelve:
     * <ul>
     * <li>201 Created</li>
     * <li>Header {@code Location} apuntando al recurso creado</li>
     * <li>Body con el grupo creado</li>
     * </ul>
     * </p>
     */
    @POST
    public Response create(@PathParam("orgId") UUID orgId, @Valid GrupoResidentesUpsertRequest req,
            @Context UriInfo uriInfo) {
        GrupoResidentesResponse created = service.create(orgId, req);

        URI location = uriInfo.getAbsolutePathBuilder().path(created.idGrupoResidente().toString())
                .build();

        return Response.created(location).entity(created).build();
    }

    /**
     * Actualiza la información del grupo (por ahora: nombre).
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe en el tenant
     * @throws jakarta.ws.rs.WebApplicationException 409 si el nombre entra en conflicto
     */
    @PUT
    @Path("/{grupoId}")
    public GrupoResidentesResponse update(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId, @Valid GrupoResidentesUpsertRequest req) {
        return service.update(orgId, grupoId, req);
    }

    /**
     * Elimina un grupo de residentes.
     *
     * @return 204 No Content si se eliminó correctamente
     *
     * @throws jakarta.ws.rs.NotFoundException si no existe en el tenant
     */
    @DELETE
    @Path("/{grupoId}")
    public Response delete(@PathParam("orgId") UUID orgId, @PathParam("grupoId") UUID grupoId) {
        service.delete(orgId, grupoId);
        return Response.noContent().build();
    }

    /**
     * Actualiza el estado de un grupo (caso de uso explícito, separado del upsert).
     *
     * <p>
     * Ruta: {@code PATCH /organizaciones/{orgId}/grupos-residentes/{grupoId}/estado}
     * </p>
     */
    @PATCH
    @Path("/{grupoId}/estado")
    public GrupoResidentesResponse updateEstado(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId, @Valid GrupoResidentesEstadoRequest req) {
        return service.updateEstado(orgId, grupoId, req);
    }

    // ---------------------------------------------------------------------------
    // Membresía: residentes dentro del grupo
    // ---------------------------------------------------------------------------

    /**
     * Agrega uno o varios residentes al grupo.
     *
     * <p>
     * Ruta: {@code POST /organizaciones/{orgId}/grupos-residentes/{grupoId}/residentes}
     * </p>
     *
     * @return grupo actualizado (incluye residentes)
     */
    @POST
    @Path("/{grupoId}/residentes")
    public GrupoResidentesResponse addResidentes(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId, @Valid GrupoResidentesAddResidentesRequest req) {
        return service.addResidentes(orgId, grupoId, req);
    }

    /**
     * Elimina uno o varios residentes del grupo.
     *
     * <p>
     * Ruta: {@code DELETE /organizaciones/{orgId}/grupos-residentes/{grupoId}/residentes}
     * </p>
     *
     * @return grupo actualizado (incluye residentes)
     */
    @DELETE
    @Path("/{grupoId}/residentes")
    public GrupoResidentesResponse removeResidentes(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId, @Valid GrupoResidentesRemoveResidentesRequest req) {
        return service.removeResidentes(orgId, grupoId, req);
    }

    /**
     * Reemplaza completamente los residentes del grupo.
     *
     * <p>
     * Ruta: {@code PUT /organizaciones/{orgId}/grupos-residentes/{grupoId}/residentes}
     * </p>
     *
     * @return grupo actualizado (incluye residentes)
     */
    @PUT
    @Path("/{grupoId}/residentes")
    public GrupoResidentesResponse replaceResidentes(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId,
            @Valid GrupoResidentesReplaceResidentesRequest req) {
        return service.replaceResidentes(orgId, grupoId, req);
    }
}
