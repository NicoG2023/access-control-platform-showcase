package com.haedcom.access.api.grupo_visitantes;

import java.net.URI;
import java.util.UUID;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.grupo_visitantes.dto.GrupoVisitantesEstadoRequest;
import com.haedcom.access.api.grupo_visitantes.dto.GrupoVisitantesMiembrosRequest;
import com.haedcom.access.api.grupo_visitantes.dto.GrupoVisitantesResponse;
import com.haedcom.access.api.grupo_visitantes.dto.GrupoVisitantesUpsertRequest;
import com.haedcom.access.application.grupo_visitantes.GrupoVisitantesService;
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
 * Recurso REST para la gestión de grupos de visitantes dentro del contexto de una organización
 * (tenant).
 *
 * <p>
 * Base path: {@code /organizaciones/{orgId}/grupos-visitantes}
 * </p>
 *
 * <h3>Rutas principales</h3>
 * <ul>
 * <li>GET {@code /organizaciones/{orgId}/grupos-visitantes}</li>
 * <li>GET {@code /organizaciones/{orgId}/grupos-visitantes/{grupoId}}</li>
 * <li>POST {@code /organizaciones/{orgId}/grupos-visitantes}</li>
 * <li>PUT {@code /organizaciones/{orgId}/grupos-visitantes/{grupoId}}</li>
 * <li>DELETE {@code /organizaciones/{orgId}/grupos-visitantes/{grupoId}}</li>
 * <li>PATCH {@code /organizaciones/{orgId}/grupos-visitantes/{grupoId}/estado}</li>
 * </ul>
 *
 * <h3>Gestión de miembros</h3>
 * <ul>
 * <li>POST {@code /organizaciones/{orgId}/grupos-visitantes/{grupoId}/visitantes} (add)</li>
 * <li>DELETE {@code /organizaciones/{orgId}/grupos-visitantes/{grupoId}/visitantes} (remove)</li>
 * <li>PUT {@code /organizaciones/{orgId}/grupos-visitantes/{grupoId}/visitantes} (replace)</li>
 * </ul>
 *
 * <h3>Convenciones de error</h3>
 * <p>
 * Los errores son manejados por {@code ExceptionMapper}s globales (ya existentes en tu proyecto):
 * </p>
 * <ul>
 * <li>400: request inválido (Bean Validation, parámetros fuera de rango, IDs inválidos)</li>
 * <li>404: organización o grupo no encontrado, o grupo no pertenece al tenant</li>
 * <li>409: conflicto de unicidad (nombre duplicado dentro del tenant)</li>
 * </ul>
 */
@ApplicationScoped
@Path("/organizaciones/{orgId}/grupos-visitantes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GrupoVisitantesResource {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final GrupoVisitantesService service;

    /**
     * Inyección por constructor (preferida) para mejorar testabilidad y asegurar inmutabilidad.
     *
     * @param service servicio de aplicación para grupos de visitantes
     */
    @Inject
    public GrupoVisitantesResource(GrupoVisitantesService service) {
        this.service = service;
    }

    /**
     * Lista grupos de visitantes de una organización de forma paginada.
     *
     * <p>
     * Por defecto NO retorna la colección {@code visitantes}. Para obtener detalle, use el endpoint
     * {@code GET /{grupoId}?includeVisitantes=true}.
     * </p>
     *
     * @param orgId identificador del tenant
     * @param q filtro opcional por nombre (búsqueda parcial, case-insensitive)
     * @param estado filtro opcional por estado del grupo
     * @param page número de página (base 0)
     * @param size tamaño de página (1..200)
     * @return página con items y metadatos
     */
    @GET
    public PageResponse<GrupoVisitantesResponse> list(@PathParam("orgId") UUID orgId,
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
     * El parámetro {@code includeVisitantes} permite solicitar también la lista de visitantes
     * asociados. Esto hace que el service ejecute una lectura con {@code JOIN FETCH}.
     * </p>
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @param includeVisitantes si true, incluye lista de visitantes
     * @return DTO del grupo
     */
    @GET
    @Path("/{grupoId}")
    public GrupoVisitantesResponse get(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId,
            @QueryParam("includeVisitantes") Boolean includeVisitantes) {

        boolean include = (includeVisitantes != null) && includeVisitantes;
        return service.get(orgId, grupoId, include);
    }

    /**
     * Crea un grupo de visitantes dentro de una organización.
     *
     * <p>
     * Devuelve:
     * <ul>
     * <li>201 Created</li>
     * <li>Header {@code Location} apuntando al recurso creado</li>
     * <li>Body con el grupo creado</li>
     * </ul>
     * </p>
     *
     * @param orgId tenant
     * @param req payload de creación (validado con Bean Validation)
     * @param uriInfo contexto para construir el header {@code Location}
     * @return respuesta 201 con {@code Location} y entidad creada
     */
    @POST
    public Response create(@PathParam("orgId") UUID orgId, @Valid GrupoVisitantesUpsertRequest req,
            @Context UriInfo uriInfo) {

        GrupoVisitantesResponse created = service.create(orgId, req);

        URI location = uriInfo.getAbsolutePathBuilder().path(created.idGrupoVisitante().toString())
                .build();

        return Response.created(location).entity(created).build();
    }

    /**
     * Actualiza la información de un grupo existente.
     *
     * <p>
     * Nota: si el request incluye {@code visitantesId}, el service interpreta que se desea
     * reemplazar completamente la membresía por esa lista. Si {@code visitantesId} es null, no se
     * toca la membresía.
     * </p>
     *
     * @param orgId tenant
     * @param grupoId id del grupo
     * @param req payload con los nuevos datos (validado con Bean Validation)
     * @return grupo actualizado
     */
    @PUT
    @Path("/{grupoId}")
    public GrupoVisitantesResponse update(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId, @Valid GrupoVisitantesUpsertRequest req) {

        return service.update(orgId, grupoId, req);
    }

    /**
     * Elimina un grupo de visitantes dentro de una organización.
     *
     * @param orgId tenant
     * @param grupoId id del grupo
     * @return 204 No Content si se eliminó correctamente
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
     * Ruta: {@code PATCH /organizaciones/{orgId}/grupos-visitantes/{grupoId}/estado}
     * </p>
     *
     * @param orgId tenant
     * @param grupoId id del grupo
     * @param req payload con el nuevo estado (validado con Bean Validation)
     * @return grupo con el estado actualizado
     */
    @PATCH
    @Path("/{grupoId}/estado")
    public GrupoVisitantesResponse updateEstado(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId, @Valid GrupoVisitantesEstadoRequest req) {

        return service.updateEstado(orgId, grupoId, req);
    }

    // -------------------------------------------------------------------------
    // Gestión de miembros (visitantes) en el grupo
    // -------------------------------------------------------------------------

    /**
     * Agrega uno o varios visitantes al grupo (operación incremental).
     *
     * <p>
     * Ruta: {@code POST /organizaciones/{orgId}/grupos-visitantes/{grupoId}/visitantes}
     * </p>
     *
     * @param orgId tenant
     * @param grupoId id del grupo
     * @param req request con IDs a agregar (validado)
     * @return grupo con membresía actualizada (incluye visitantes)
     */
    @POST
    @Path("/{grupoId}/visitantes")
    public GrupoVisitantesResponse addVisitantes(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId, @Valid GrupoVisitantesMiembrosRequest req) {

        return service.addVisitantes(orgId, grupoId, req);
    }

    /**
     * Elimina uno o varios visitantes del grupo.
     *
     * <p>
     * Ruta: {@code DELETE /organizaciones/{orgId}/grupos-visitantes/{grupoId}/visitantes}
     * </p>
     *
     * <p>
     * Nota: se consume body JSON con IDs a eliminar (patrón aceptable cuando se requiere enviar un
     * payload en DELETE). Si prefieres evitar body en DELETE, alternativa:
     * {@code POST /.../visitantes/remove}.
     * </p>
     *
     * @param orgId tenant
     * @param grupoId id del grupo
     * @param req request con IDs a eliminar (validado)
     * @return grupo con membresía actualizada (incluye visitantes)
     */
    @DELETE
    @Path("/{grupoId}/visitantes")
    public GrupoVisitantesResponse removeVisitantes(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId, @Valid GrupoVisitantesMiembrosRequest req) {

        return service.removeVisitantes(orgId, grupoId, req);
    }

    /**
     * Reemplaza completamente la lista de visitantes del grupo.
     *
     * <p>
     * Ruta: {@code PUT /organizaciones/{orgId}/grupos-visitantes/{grupoId}/visitantes}
     * </p>
     *
     * @param orgId tenant
     * @param grupoId id del grupo
     * @param req request con la lista final de IDs (validado)
     * @return grupo con membresía actualizada (incluye visitantes)
     */
    @PUT
    @Path("/{grupoId}/visitantes")
    public GrupoVisitantesResponse replaceVisitantes(@PathParam("orgId") UUID orgId,
            @PathParam("grupoId") UUID grupoId, @Valid GrupoVisitantesMiembrosRequest req) {

        return service.replaceVisitantes(orgId, grupoId, req);
    }
}
