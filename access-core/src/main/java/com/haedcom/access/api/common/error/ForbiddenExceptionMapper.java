package com.haedcom.access.api.common.error;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapper para errores de autorización (403 - Forbidden).
 *
 * <p>
 * Se dispara cuando el usuario está autenticado, pero no tiene permisos/rol para acceder al
 * recurso.
 * </p>
 *
 * <p>
 * Por seguridad, el mensaje devuelto al cliente es genérico.
 * </p>
 */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ForbiddenException exception) {
        String path = uriInfo != null && uriInfo.getRequestUri() != null
                ? uriInfo.getRequestUri().getPath()
                : null;

        ErrorResponse body = ErrorResponse.simple("FORBIDDEN",
                "Acceso denegado: no tienes permisos para esta operación", 403, path);

        return Response.status(Response.Status.FORBIDDEN).type(MediaType.APPLICATION_JSON)
                .entity(body).build();
    }
}
