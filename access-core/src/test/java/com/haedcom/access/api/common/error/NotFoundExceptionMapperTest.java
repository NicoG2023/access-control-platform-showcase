package com.haedcom.access.api.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.net.URI;
import org.junit.jupiter.api.Test;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Pruebas unitarias para {@link NotFoundExceptionMapper}.
 */
class NotFoundExceptionMapperTest {

    @Test
    void deberiaMapear404_aErrorResponse() {
        NotFoundExceptionMapper mapper = new NotFoundExceptionMapper();

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("organizaciones/1/residentes/2");
        when(uriInfo.getRequestUri())
                .thenReturn(URI.create("http://localhost/organizaciones/1/residentes/2"));
        mapper.uriInfo = uriInfo;

        Response res = mapper.toResponse(new NotFoundException("Residente no encontrado"));

        assertThat(res.getStatus()).isEqualTo(404);

        ErrorResponse body = (ErrorResponse) res.getEntity();
        assertThat(body.code()).isEqualTo("NOT_FOUND");
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.message()).contains("Residente no encontrado");
        assertThat(body.path()).contains("/organizaciones/1/residentes/2");
        assertThat(body.timestamp()).isNotNull();
    }
}
