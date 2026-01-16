package com.haedcom.access.api.common.error;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapper para errores de autenticación (401 - Unauthorized).
 *
 * <p>
 * Se dispara cuando el usuario no está autenticado o falta/expira el token, o el esquema de
 * autenticación no es válido.
 * </p>
 *
 * <p>
 * Por seguridad, el mensaje devuelto al cliente es genérico (no expone detalles del mecanismo de
 * auth).
 * </p>
 */
@Provider
public class NotAuthorizedExceptionMapper implements ExceptionMapper<NotAuthorizedException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(NotAuthorizedException exception) {
        String path = uriInfo != null && uriInfo.getRequestUri() != null
                ? uriInfo.getRequestUri().getPath()
                : null;

        ErrorResponse body = ErrorResponse.simple("UNAUTHORIZED",
                "No autenticado o credenciales inválidas", 401, path);

        return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.APPLICATION_JSON)
                .entity(body).build();
    }
}
