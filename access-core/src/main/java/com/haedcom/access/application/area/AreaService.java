package com.haedcom.access.application.area;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.haedcom.access.api.area.dto.AreaResponse;
import com.haedcom.access.api.area.dto.AreaUpsertRequest;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.domain.model.Area;
import com.haedcom.access.domain.model.Organizacion;
import com.haedcom.access.domain.repo.AreaRepository;
import com.haedcom.access.domain.repo.OrganizacionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Servicio de aplicación que orquesta los casos de uso para la gestión de {@link Area} dentro del
 * contexto de una organización (multi-tenant).
 *
 * <p>
 * Responsabilidades principales:
 * <ul>
 * <li>Garantizar aislamiento por tenant ({@code idOrganizacion}).</li>
 * <li>Validar unicidad del nombre del área dentro de la organización.</li>
 * <li>Coordinar repositorios y entidades de dominio.</li>
 * <li>Traducir violaciones de integridad a errores HTTP consistentes.</li>
 * <li>Mapear entidades de dominio a DTOs de salida.</li>
 * </ul>
 * </p>
 */
@ApplicationScoped
public class AreaService {

    /**
     * Nombre del constraint UNIQUE en base de datos para (id_organizacion, nombre).
     */
    private static final String UK_AREA_NOMBRE = "ux_area_org_nombre";

    private final AreaRepository areaRepo;
    private final OrganizacionRepository orgRepo;

    public AreaService(AreaRepository areaRepo, OrganizacionRepository orgRepo) {
        this.areaRepo = Objects.requireNonNull(areaRepo, "areaRepo es obligatorio");
        this.orgRepo = Objects.requireNonNull(orgRepo, "orgRepo es obligatorio");
    }

    /**
     * Lista las áreas de una organización de forma paginada.
     *
     * @param orgId identificador de la organización (tenant)
     * @param page número de página (base 0)
     * @param size tamaño de página
     * @return respuesta paginada con áreas y metadatos
     */
    @Transactional
    public PageResponse<AreaResponse> list(UUID orgId, int page, int size) {
        List<AreaResponse> items = areaRepo.listByOrganizacion(orgId, page, size).stream()
                .map(this::toResponse).toList();

        long total = areaRepo.countByOrganizacion(orgId);

        return PageResponse.of(items, page, size, total);
    }

    /**
     * Obtiene una área específica dentro de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param areaId identificador del área
     * @return DTO del área
     * @throws NotFoundException si el área no existe o no pertenece a la organización
     */
    @Transactional
    public AreaResponse get(UUID orgId, UUID areaId) {
        return toResponse(getAreaOrThrow(orgId, areaId));
    }

    /**
     * Crea una nueva área dentro de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param req datos de creación del área
     * @return área creada
     * @throws NotFoundException si la organización no existe
     * @throws WebApplicationException 409 si el nombre del área ya existe
     */
    @Transactional
    public AreaResponse create(UUID orgId, AreaUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Organizacion org = getOrganizacionOrThrow(orgId);

        ensureNombreUnicoOnCreate(orgId, req.nombre());

        Area area = Area.crear(org, req.nombre(), req.rutaImagenArea());

        try {
            areaRepo.persist(area);
            areaRepo.flush();
            return toResponse(area);
        } catch (RuntimeException e) {
            if (isUniqueNombreViolation(e)) {
                throw conflictNombre();
            }
            throw e;
        }
    }

    /**
     * Actualiza una área existente dentro de la organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param areaId identificador del área
     * @param req nuevos datos del área
     * @return área actualizada
     * @throws NotFoundException si el área no existe o no pertenece a la organización
     * @throws WebApplicationException 409 si el nombre entra en conflicto
     */
    @Transactional
    public AreaResponse update(UUID orgId, UUID areaId, AreaUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Area area = getAreaOrThrow(orgId, areaId);

        ensureNombreUnicoOnUpdate(orgId, req.nombre(), areaId);

        area.setNombre(req.nombre());
        area.setRutaImagenArea(req.rutaImagenArea());

        try {
            areaRepo.flush();
            return toResponse(area);
        } catch (RuntimeException e) {
            if (isUniqueNombreViolation(e)) {
                throw conflictNombre();
            }
            throw e;
        }
    }

    /**
     * Elimina un área de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param areaId identificador del área
     * @throws NotFoundException si el área no existe o no pertenece a la organización
     */
    @Transactional
    public void delete(UUID orgId, UUID areaId) {
        Area area = getAreaOrThrow(orgId, areaId);
        areaRepo.delete(area);
    }

    // -------------------------
    // Helpers privados
    // -------------------------

    private Organizacion getOrganizacionOrThrow(UUID orgId) {
        return orgRepo.findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organización no encontrada"));
    }

    private Area getAreaOrThrow(UUID orgId, UUID areaId) {
        return areaRepo.findByIdAndOrganizacion(areaId, orgId)
                .orElseThrow(() -> new NotFoundException("Área no encontrada"));
    }

    private void ensureNombreUnicoOnCreate(UUID orgId, String nombre) {
        if (areaRepo.existsByNombre(orgId, nombre.trim())) {
            throw conflictNombre();
        }
    }

    private void ensureNombreUnicoOnUpdate(UUID orgId, String nombre, UUID areaId) {
        if (areaRepo.existsByNombreExcludingId(orgId, nombre.trim(), areaId)) {
            throw conflictNombre();
        }
    }

    private WebApplicationException conflictNombre() {
        return new WebApplicationException("Ya existe un área con ese nombre en la organización",
                Response.Status.CONFLICT);
    }

    private boolean isUniqueNombreViolation(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains(UK_AREA_NOMBRE)) {
                return true;
            }
            if (cur instanceof java.sql.SQLException sqlEx) {
                if ("23505".equals(sqlEx.getSQLState())) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private AreaResponse toResponse(Area a) {
        return new AreaResponse(a.getIdArea(), a.getIdOrganizacion(), a.getNombre(),
                a.getRutaImagenArea(), a.getCreadoEnUtc(), a.getActualizadoEnUtc());
    }
}
