package com.haedcom.access.api.common.error;

import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapper para errores de validación (Bean Validation).
 *
 * <p>
 * Ej: @NotBlank, @Size, @NotNull en los DTOs.
 * </p>
 */
@Provider
public class ConstraintViolationExceptionMapper
                implements ExceptionMapper<ConstraintViolationException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(ConstraintViolationException exception) {
                String path = uriInfo.getRequestUri().getPath();

                List<ErrorDetail> details = exception.getConstraintViolations().stream()
                                .map(v -> new ErrorDetail(v.getPropertyPath().toString(),
                                                v.getMessage()))
                                .collect(Collectors.toList());

                ErrorResponse body = ErrorResponse.withDetails("VALIDATION_ERROR",
                                "Error de validación en el request", 400, path, details);

                return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                                .entity(body).build();
        }
}
