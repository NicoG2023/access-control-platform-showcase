package com.haedcom.access.application.organizacion;

import java.util.List;
import java.util.UUID;
import com.haedcom.access.api.organizacion.dto.OrganizacionCreateRequest;
import com.haedcom.access.api.organizacion.dto.OrganizacionListResponse;
import com.haedcom.access.api.organizacion.dto.OrganizacionResponse;
import com.haedcom.access.api.organizacion.dto.OrganizacionUpdateRequest;
import com.haedcom.access.domain.model.Organizacion;
import com.haedcom.access.domain.repo.OrganizacionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

/**
 * Servicio de aplicación para administrar {@link Organizacion}.
 *
 * <p>
 * Responsabilidades:
 * </p>
 * <ul>
 * <li>Orquestar operaciones CRUD con control transaccional.</li>
 * <li>Aplicar validaciones de entrada (forma y límites) antes de tocar la capa de
 * persistencia.</li>
 * <li>Mapear entre entidades de dominio y DTOs del API.</li>
 * </ul>
 *
 * <p>
 * Este servicio NO implementa autenticación/autorización; eso debe manejarse en la capa API
 * (resource, filters/interceptors) o en un servicio superior.
 * </p>
 */
@ApplicationScoped
public class OrganizacionService {

    @Inject
    OrganizacionRepository repo;

    /**
     * Crea una nueva organización.
     *
     * <p>
     * Si {@code timezoneId} viene vacío o null, se aplica un default de negocio:
     * </p>
     * <ul>
     * <li>Se usa {@code "UTC"} como valor por defecto.</li>
     * </ul>
     *
     * @param request datos de creación (obligatorio)
     * @return DTO de la organización creada
     * @throws IllegalArgumentException si el request es null o inválido
     */
    @Transactional
    public OrganizacionResponse create(OrganizacionCreateRequest request) {
        requireNonNull(request, "request");

        String nombre = normalizeRequired(request.nombre, 80, "nombre");
        String estado = normalizeRequired(request.estado, 20, "estado");
        String tz = normalizeOptional(request.timezoneId, 50);
        if (tz == null || tz.isBlank()) {
            tz = "UTC";
        }

        // Usa el factory existente (setea timezoneId a UTC) y luego aplicamos el timezone deseado.
        Organizacion o = Organizacion.crear(null, nombre, estado);
        o.setTimezoneId(tz);

        repo.create(o);
        // opcional: repo.flush() si quieres detectar violaciones UNIQUE tempranas.
        return toResponse(o);
    }

    /**
     * Obtiene una organización por id.
     *
     * @param orgId id de organización (obligatorio)
     * @return DTO de la organización
     * @throws IllegalArgumentException si orgId es null
     * @throws NotFoundException si no existe
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public OrganizacionResponse get(UUID orgId) {
        requireNonNull(orgId, "orgId");
        Organizacion o = repo.findByIdOrThrow(orgId);
        return toResponse(o);
    }

    /**
     * Lista organizaciones.
     *
     * <p>
     * Retorna respuesta paginada con orden estable (por nombre asc, luego id asc), según
     * implementación del repositorio.
     * </p>
     *
     * @param offset cantidad a omitir (>= 0)
     * @param limit máximo a retornar (> 0)
     * @return respuesta paginada
     * @throws IllegalArgumentException si offset/limit son inválidos
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public OrganizacionListResponse list(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset debe ser >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit debe ser > 0");
        }

        long total = repo.countAll();
        List<OrganizacionResponse> items =
                repo.findAll(offset, limit).stream().map(this::toResponse).toList();

        OrganizacionListResponse resp = new OrganizacionListResponse();
        resp.total = total;
        resp.offset = offset;
        resp.limit = limit;
        resp.items = items;
        return resp;
    }

    /**
     * Actualiza una organización existente.
     *
     * <p>
     * Modelo "patch-like": si un campo viene null, no se actualiza.
     * </p>
     *
     * @param orgId id de organización (obligatorio)
     * @param request datos a actualizar (obligatorio)
     * @return DTO de la organización actualizada
     * @throws IllegalArgumentException si parámetros son null o inválidos
     * @throws NotFoundException si no existe
     */
    @Transactional
    public OrganizacionResponse update(UUID orgId, OrganizacionUpdateRequest request) {
        requireNonNull(orgId, "orgId");
        requireNonNull(request, "request");

        Organizacion o = repo.findByIdOrThrow(orgId);

        if (request.nombre != null) {
            o.setNombre(normalizeRequired(request.nombre, 80, "nombre"));
        }
        if (request.estado != null) {
            o.setEstado(normalizeRequired(request.estado, 20, "estado"));
        }
        if (request.timezoneId != null) {
            String tz = normalizeRequired(request.timezoneId, 50, "timezoneId");
            o.setTimezoneId(tz);
        }

        // Como la entidad está managed dentro de la transacción, no es obligatorio llamar merge().
        // Aun así, mantenerlo explícito no hace daño si te gusta el estilo.
        repo.update(o);

        return toResponse(o);
    }

    /**
     * Elimina una organización por id.
     *
     * <p>
     * Operación idempotente:
     * </p>
     * <ul>
     * <li>Si existe, se elimina y retorna {@code true}.</li>
     * <li>Si no existe, retorna {@code false}.</li>
     * </ul>
     *
     * @param orgId id de organización (obligatorio)
     * @return {@code true} si eliminó, {@code false} si no existía
     * @throws IllegalArgumentException si orgId es null
     */
    @Transactional
    public boolean delete(UUID orgId) {
        requireNonNull(orgId, "orgId");
        return repo.deleteById(orgId);
    }

    /**
     * Elimina una organización por id o falla si no existe.
     *
     * @param orgId id de organización (obligatorio)
     * @throws IllegalArgumentException si orgId es null
     * @throws NotFoundException si no existe
     */
    @Transactional
    public void deleteOrThrow(UUID orgId) {
        requireNonNull(orgId, "orgId");
        repo.deleteByIdOrThrow(orgId);
    }

    // ---------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------

    /**
     * Mapea una entidad {@link Organizacion} a {@link OrganizacionResponse}.
     *
     * @param o entidad (no null)
     * @return DTO de salida
     */
    protected OrganizacionResponse toResponse(Organizacion o) {
        OrganizacionResponse r = new OrganizacionResponse();
        r.idOrganizacion = o.getIdOrganizacion();
        r.nombre = o.getNombre();
        r.estado = o.getEstado();
        r.timezoneId = o.getTimezoneId();
        r.creadoEnUtc = o.getCreadoEnUtc();
        r.actualizadoEnUtc = o.getActualizadoEnUtc();
        return r;
    }

    // ---------------------------------------------------------------------
    // Validación/normalización (simple y consistente con tu entidad)
    // ---------------------------------------------------------------------

    private static void requireNonNull(Object v, String field) {
        if (v == null)
            throw new IllegalArgumentException(field + " no puede ser null");
    }

    private static String normalizeRequired(String v, int max, String field) {
        String s = normalizeOptional(v, max);
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
        return s;
    }

    private static String normalizeOptional(String v, int max) {
        if (v == null)
            return null;
        String s = v.trim();
        if (s.length() > max) {
            throw new IllegalArgumentException("valor excede máximo " + max + " caracteres");
        }
        return s;
    }
}
