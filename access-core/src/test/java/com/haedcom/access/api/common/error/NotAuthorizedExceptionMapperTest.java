package com.haedcom.access.api.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.net.URI;
import org.junit.jupiter.api.Test;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Pruebas unitarias para {@link NotAuthorizedExceptionMapper}.
 */
class NotAuthorizedExceptionMapperTest {

    @Test
    void deberiaMapear401_aUnauthorized() {
        NotAuthorizedExceptionMapper mapper = new NotAuthorizedExceptionMapper();

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("organizaciones/1/residentes");
        when(uriInfo.getRequestUri())
                .thenReturn(URI.create("http://localhost/organizaciones/1/residentes"));
        mapper.uriInfo = uriInfo;

        Response res = mapper.toResponse(new NotAuthorizedException("Bearer"));

        assertThat(res.getStatus()).isEqualTo(401);

        ErrorResponse body = (ErrorResponse) res.getEntity();
        assertThat(body.code()).isEqualTo("UNAUTHORIZED");
        assertThat(body.status()).isEqualTo(401);
        assertThat(body.path()).contains("/organizaciones/1/residentes");
        assertThat(body.message()).contains("No autenticado");
    }
}
