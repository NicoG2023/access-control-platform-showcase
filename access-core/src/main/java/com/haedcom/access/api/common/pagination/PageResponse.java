package com.haedcom.access.api.common.pagination;

import java.util.List;

/**
 * Respuesta estándar para endpoints paginados.
 *
 * <p>
 * Incluye los elementos de la página actual y metadatos útiles para frontend:
 * <ul>
 * <li>{@code page}: índice base 0</li>
 * <li>{@code size}: tamaño de página</li>
 * <li>{@code total}: total de elementos disponibles para el filtro actual</li>
 * <li>{@code totalPages}: páginas totales (derivado)</li>
 * <li>{@code hasNext}/{@code hasPrev}: navegación (derivado)</li>
 * </ul>
 * </p>
 *
 * @param <T> tipo de los elementos retornados
 */
public record PageResponse<T>(List<T> items, int page, int size, long total, long totalPages,
        boolean hasNext, boolean hasPrev) {

    /**
     * Construye una respuesta paginada calculando metadatos derivados.
     *
     * @param items elementos de la página
     * @param page índice base 0
     * @param size tamaño de página (debe ser > 0)
     * @param total total de elementos para el criterio
     * @param <T> tipo de elemento
     * @return respuesta paginada
     */
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        if (size <= 0) {
            throw new IllegalArgumentException("size debe ser > 0");
        }

        long totalPages = (total + size - 1) / size; // ceil(total/size) sin doubles
        boolean hasPrev = page > 0;
        boolean hasNext = (page + 1) < totalPages;

        return new PageResponse<>(items, page, size, total, totalPages, hasNext, hasPrev);
    }
}
