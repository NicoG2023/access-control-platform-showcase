package com.haedcom.access.api.organizacion;

import java.net.URI;
import java.util.UUID;
import com.haedcom.access.api.organizacion.dto.OrganizacionCreateRequest;
import com.haedcom.access.api.organizacion.dto.OrganizacionListResponse;
import com.haedcom.access.api.organizacion.dto.OrganizacionResponse;
import com.haedcom.access.api.organizacion.dto.OrganizacionUpdateRequest;
import com.haedcom.access.application.organizacion.OrganizacionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
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
 * Resource REST para administrar {@link com.haedcom.access.domain.model.Organizacion}.
 *
 * <p>
 * Esta API expone operaciones CRUD básicas para gestionar organizaciones (tenants).
 * </p>
 *
 * <p>
 * Convenciones:
 * </p>
 * <ul>
 * <li>Respuestas en JSON.</li>
 * <li>En creación, retorna {@code 201 Created} y header {@code Location} apuntando al recurso
 * creado.</li>
 * <li>En consulta por id, retorna {@code 404 Not Found} si no existe (propagado por el
 * service).</li>
 * <li>Listado paginado vía {@code offset} y {@code limit}.</li>
 * </ul>
 *
 * <p>
 * Seguridad/multi-tenant:
 * </p>
 * <ul>
 * <li>Este resource no implementa autenticación ni autorización.</li>
 * <li>Si más adelante restringes estos endpoints a un rol "platform-admin", se puede agregar con
 * annotations.</li>
 * </ul>
 */
@Path("/api/organizaciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrganizacionResource {

    @Inject
    OrganizacionService service;

    /**
     * Crea una organización.
     *
     * <p>
     * Retorna {@code 201 Created} y el body con la entidad creada. Además, incluye el header
     * {@code Location} con la URL del recurso creado.
     * </p>
     *
     * @param request DTO con datos de creación
     * @param uriInfo contexto de URL para construir el Location header
     * @return response HTTP con el recurso creado
     */
    @POST
    public Response create(OrganizacionCreateRequest request, @Context UriInfo uriInfo) {
        OrganizacionResponse created = service.create(request);

        URI location =
                uriInfo.getAbsolutePathBuilder().path(created.idOrganizacion.toString()).build();

        return Response.created(location).entity(created).build();
    }

    /**
     * Obtiene una organización por id.
     *
     * @param id id de la organización
     * @return DTO de la organización
     */
    @GET
    @Path("/{id}")
    public OrganizacionResponse get(@PathParam("id") UUID id) {
        return service.get(id);
    }

    /**
     * Lista organizaciones con paginación.
     *
     * <p>
     * Parámetros:
     * </p>
     * <ul>
     * <li>{@code offset}: cantidad de registros a omitir (default 0)</li>
     * <li>{@code limit}: máximo de registros a retornar (default 50)</li>
     * </ul>
     *
     * @param offset offset (>= 0)
     * @param limit límite (> 0)
     * @return respuesta paginada con items y total
     */
    @GET
    public OrganizacionListResponse list(@QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return service.list(offset, limit);
    }

    /**
     * Actualiza una organización existente (patch-like).
     *
     * <p>
     * Aunque el verbo es {@code PUT}, el comportamiento es "patch-like": si un campo viene
     * {@code null}, no se actualiza.
     * </p>
     *
     * @param id id de la organización
     * @param request DTO con campos a actualizar
     * @return DTO actualizado
     */
    @PUT
    @Path("/{id}")
    public OrganizacionResponse update(@PathParam("id") UUID id,
            OrganizacionUpdateRequest request) {
        return service.update(id, request);
    }

    /**
     * Elimina una organización por id.
     *
     * <p>
     * Operación idempotente:
     * </p>
     * <ul>
     * <li>Si existe: retorna {@code 204 No Content}.</li>
     * <li>Si no existe: también retorna {@code 204 No Content}.</li>
     * </ul>
     *
     * @param id id de la organización
     * @return {@code 204 No Content}
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id) {
        service.delete(id);
        return Response.noContent().build();
    }

    /**
     * Elimina una organización por id o falla si no existe.
     *
     * <p>
     * Útil para escenarios administrativos donde quieres distinguir el "no existe".
     * </p>
     *
     * @param id id de la organización
     * @return {@code 204 No Content} si eliminó
     */
    @DELETE
    @Path("/{id}/strict")
    public Response deleteOrThrow(@PathParam("id") UUID id) {
        service.deleteOrThrow(id);
        return Response.noContent().build();
    }
}
