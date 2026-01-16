package com.haedcom.access.api.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.net.URI;
import org.junit.jupiter.api.Test;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Pruebas unitarias para {@link ForbiddenExceptionMapper}.
 */
class ForbiddenExceptionMapperTest {

    @Test
    void deberiaMapear403_aForbidden() {
        ForbiddenExceptionMapper mapper = new ForbiddenExceptionMapper();

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("organizaciones/1/residentes");
        when(uriInfo.getRequestUri())
                .thenReturn(URI.create("http://localhost/organizaciones/1/residentes"));
        mapper.uriInfo = uriInfo;

        Response res = mapper.toResponse(new ForbiddenException("Sin permisos"));

        assertThat(res.getStatus()).isEqualTo(403);

        ErrorResponse body = (ErrorResponse) res.getEntity();
        assertThat(body.code()).isEqualTo("FORBIDDEN");
        assertThat(body.status()).isEqualTo(403);
        assertThat(body.path()).contains("/organizaciones/1/residentes");
        assertThat(body.message()).contains("Acceso denegado");
    }
}
