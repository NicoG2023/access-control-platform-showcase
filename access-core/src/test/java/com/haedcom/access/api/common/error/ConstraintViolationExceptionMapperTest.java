package com.haedcom.access.api.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Pruebas unitarias para {@link ConstraintViolationExceptionMapper}.
 *
 * <p>
 * Se usan mocks (Mockito) para no depender de implementaciones concretas de {@link Path} /
 * {@link Path.Node}, que pueden variar entre proveedores/versiones.
 * </p>
 */
class ConstraintViolationExceptionMapperTest {

    @Test
    void deberiaMapear400_yConstruirDetails() {
        ConstraintViolationExceptionMapper mapper = new ConstraintViolationExceptionMapper();

        // Mock de UriInfo (solo lo necesario para construir "path")
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("organizaciones/1/residentes");
        when(uriInfo.getRequestUri())
                .thenReturn(URI.create("http://localhost/organizaciones/1/residentes"));
        mapper.uriInfo = uriInfo;

        // Violación 1
        ConstraintViolation<?> v1 = mock(ConstraintViolation.class);
        when(v1.getMessage()).thenReturn("must not be blank");
        when(v1.getPropertyPath()).thenReturn(pathConUnNodo("nombre"));

        // Violación 2
        ConstraintViolation<?> v2 = mock(ConstraintViolation.class);
        when(v2.getMessage()).thenReturn("size must be between 1 and 30");
        when(v2.getPropertyPath()).thenReturn(pathConUnNodo("numeroDocumento"));

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(v1, v2));

        Response res = mapper.toResponse(ex);

        assertThat(res.getStatus()).isEqualTo(400);

        ErrorResponse body = (ErrorResponse) res.getEntity();
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.details()).isNotNull();
        assertThat(body.details()).hasSize(2);

        // Ajusta según tu implementación:
        // - si tu mapper hace "/" + uriInfo.getPath() -> debe ser "/organizaciones/1/residentes"
        // - si usa requestUri.getPath() -> también
        assertThat(body.path()).isEqualTo("/organizaciones/1/residentes");
    }

    /**
     * Construye un {@link Path} falso con un único nodo cuyo {@code getName()} es {@code field}.
     *
     * <p>
     * Nota: NO se stubbea {@code toString()}, porque Mockito puede fallar al stubbear métodos de
     * {@link Object}.
     * </p>
     *
     * @param field nombre del campo (por ejemplo, "nombre")
     * @return un {@link Path} que expone un solo {@link Path.Node}
     */
    private static Path pathConUnNodo(String field) {
        return new Path() {
            @Override
            public java.util.Iterator<Node> iterator() {
                Node node = new Node() {
                    @Override
                    public String getName() {
                        return field;
                    }

                    @Override
                    public boolean isInIterable() {
                        return false;
                    }

                    @Override
                    public Integer getIndex() {
                        return null;
                    }

                    @Override
                    public Object getKey() {
                        return null;
                    }

                    @Override
                    public ElementKind getKind() {
                        return ElementKind.PROPERTY;
                    }

                    // Este sí existe en Path.Node en varias versiones:
                    @Override
                    public <T extends Node> T as(Class<T> nodeType) {
                        return nodeType.cast(this);
                    }
                };
                return java.util.List.of(node).iterator();
            }

            @Override
            public String toString() {
                return field;
            }
        };
    }

}
