package com.haedcom.access.api.common.error;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapper para {@link NotFoundException}.
 *
 * <p>
 * Convierte 404 en un JSON estándar.
 * </p>
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(NotFoundException exception) {
        String path = uriInfo.getRequestUri().getPath();

        ErrorResponse body = ErrorResponse.simple("NOT_FOUND", exception.getMessage(), 404, path);

        return Response.status(Response.Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                .entity(body).build();
    }
}
