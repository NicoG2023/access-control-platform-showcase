package com.haedcom.access.api.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.net.URI;
import org.junit.jupiter.api.Test;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Pruebas unitarias para {@link WebApplicationExceptionMapper}.
 */
class WebApplicationExceptionMapperTest {

    @Test
    void deberiaMapear409_aConflict() {
        WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("organizaciones/1/residentes");
        when(uriInfo.getRequestUri())
                .thenReturn(URI.create("http://localhost/organizaciones/1/residentes"));
        mapper.uriInfo = uriInfo;

        WebApplicationException ex =
                new WebApplicationException("Documento duplicado", Response.Status.CONFLICT);

        Response res = mapper.toResponse(ex);

        assertThat(res.getStatus()).isEqualTo(409);

        ErrorResponse body = (ErrorResponse) res.getEntity();
        assertThat(body.code()).isEqualTo("CONFLICT");
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.message()).contains("Documento duplicado");
        assertThat(body.path()).contains("/organizaciones/1/residentes");
        assertThat(body.details()).isNull();
    }
}
