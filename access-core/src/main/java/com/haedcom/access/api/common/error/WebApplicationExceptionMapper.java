package com.haedcom.access.api.common.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapper para {@link WebApplicationException}.
 *
 * <p>
 * Respeta el status original (ej. 409) y devuelve JSON estándar.
 * </p>
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {
        int status = exception.getResponse().getStatus();
        String path = uriInfo.getRequestUri().getPath();

        String code = switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            default -> "HTTP_" + status;
        };

        ErrorResponse body = ErrorResponse.simple(code, exception.getMessage(), status, path);

        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
