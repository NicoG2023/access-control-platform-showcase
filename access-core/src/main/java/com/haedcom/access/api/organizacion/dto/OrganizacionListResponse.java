package com.haedcom.access.api.organizacion.dto;

import java.util.List;

/**
 * Respuesta paginada para listados de organizaciones.
 */
public class OrganizacionListResponse {

    /** Total de registros existentes. */
    public long total;

    /** Offset usado en la consulta. */
    public int offset;

    /** Límite usado en la consulta. */
    public int limit;

    /** Items retornados. */
    public List<OrganizacionResponse> items;

    public OrganizacionListResponse() {}
}
