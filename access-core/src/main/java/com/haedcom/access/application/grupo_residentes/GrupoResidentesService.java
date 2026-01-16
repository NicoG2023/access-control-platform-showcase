package com.haedcom.access.application.grupo_residentes;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesAddResidentesRequest;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesEstadoRequest;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesRemoveResidentesRequest;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesReplaceResidentesRequest;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesResponse;
import com.haedcom.access.api.grupo_residentes.dto.GrupoResidentesUpsertRequest;
import com.haedcom.access.api.grupo_residentes.dto.ResidenteResumenResponse;
import com.haedcom.access.domain.enums.EstadoGrupo;
import com.haedcom.access.domain.model.GrupoResidentes;
import com.haedcom.access.domain.model.Residente;
import com.haedcom.access.domain.repo.GrupoResidentesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Servicio de aplicación que orquesta los casos de uso para la gestión de {@link GrupoResidentes}
 * dentro de una organización (multi-tenant por {@code idOrganizacion}).
 *
 * <p>
 * Responsabilidades principales:
 * <ul>
 * <li>Aislamiento por tenant: todas las operaciones aplican {@code orgId}.</li>
 * <li>Validaciones que requieren base de datos (p.ej. unicidad por nombre).</li>
 * <li>Gestión de membresía (agregar/eliminar/reemplazar residentes) asegurando que los residentes
 * pertenezcan al tenant.</li>
 * <li>Traducción a errores HTTP consistentes mediante excepciones estándar (404/409/400).</li>
 * <li>Mapeo a DTOs de salida.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Nota: validaciones intrínsecas del modelo (campos obligatorios, longitudes) deben estar en
 * dominio y/o Bean Validation en DTOs.
 * </p>
 */
@ApplicationScoped
public class GrupoResidentesService {

    /**
     * Nombre sugerido de constraint UNIQUE para (id_organizacion, nombre).
     * <p>
     * Ajusta este valor al nombre real en DB si lo creas.
     * </p>
     */
    private static final String UK_GRUPO_RESIDENTES_NOMBRE = "ux_grupo_residentes_nombre";

    private final GrupoResidentesRepository grupoRepo;

    public GrupoResidentesService(GrupoResidentesRepository grupoRepo) {
        this.grupoRepo = Objects.requireNonNull(grupoRepo, "grupoRepo es obligatorio");
    }

    /**
     * Lista grupos de una organización de forma paginada, aplicando filtros opcionales.
     *
     * @param orgId identificador de la organización (tenant)
     * @param q búsqueda libre por nombre (LIKE, case-insensitive); opcional
     * @param estado filtro por estado; opcional
     * @param page número de página (base 0)
     * @param size tamaño de página
     * @return respuesta paginada con grupos (sin cargar residentes para lista)
     */
    @Transactional
    public PageResponse<GrupoResidentesResponse> list(UUID orgId, String q, EstadoGrupo estado,
            int page, int size) {

        List<GrupoResidentesResponse> items = grupoRepo.listByTenant(orgId, page, size, q, estado)
                .stream().map(this::toResponseLite).toList();

        long total = grupoRepo.countByTenant(orgId, q, estado);

        return PageResponse.of(items, page, size, total);
    }

    /**
     * Obtiene un grupo dentro del tenant.
     *
     * @param orgId identificador de la organización (tenant)
     * @param grupoId identificador del grupo
     * @param includeResidentes si true, incluye los residentes del grupo (JOIN FETCH)
     * @return grupo encontrado
     * @throws NotFoundException si no existe en el tenant
     */
    @Transactional
    public GrupoResidentesResponse get(UUID orgId, UUID grupoId, boolean includeResidentes) {
        GrupoResidentes g = includeResidentes ? getGrupoWithResidentesOrThrow(orgId, grupoId)
                : getGrupoOrThrow(orgId, grupoId);

        return includeResidentes ? toResponseFull(g) : toResponseLite(g);
    }

    /**
     * Crea un grupo dentro del tenant.
     *
     * <p>
     * Reglas aplicadas:
     * <ul>
     * <li>Nombre obligatorio (Bean Validation y/o dominio).</li>
     * <li>Validación previa de unicidad de nombre dentro del tenant.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Concurrencia: se fuerza {@code flush()} para capturar violaciones UNIQUE en el alcance del
     * método y traducirlas a 409.
     * </p>
     */
    @Transactional
    public GrupoResidentesResponse create(UUID orgId, GrupoResidentesUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        ensureNombreUnicoOnCreate(orgId, req.nombre());

        GrupoResidentes g = GrupoResidentes.crear(req.nombre());
        g.assignTenant(orgId);

        try {
            grupoRepo.persist(g);
            grupoRepo.flush();
            return toResponseLite(g);
        } catch (RuntimeException e) {
            if (isUniqueNombreViolation(e)) {
                throw conflictNombre();
            }
            throw e;
        }
    }

    /**
     * Actualiza los datos de un grupo dentro del tenant (actualmente solo nombre).
     *
     * @throws NotFoundException si no existe en el tenant
     * @throws WebApplicationException 409 si el nombre entra en conflicto con otro grupo
     */
    @Transactional
    public GrupoResidentesResponse update(UUID orgId, UUID grupoId,
            GrupoResidentesUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        GrupoResidentes g = getGrupoOrThrow(orgId, grupoId);

        ensureNombreUnicoOnUpdate(orgId, req.nombre(), grupoId);

        // No tienes método de dominio para actualizar; lo hacemos directo como en tu entidad
        // actual.
        g.setNombre(req.nombre());

        try {
            grupoRepo.flush();
            return toResponseLite(g);
        } catch (RuntimeException e) {
            if (isUniqueNombreViolation(e)) {
                throw conflictNombre();
            }
            throw e;
        }
    }

    /**
     * Elimina un grupo dentro del tenant.
     *
     * @throws NotFoundException si no existe en el tenant
     */
    @Transactional
    public void delete(UUID orgId, UUID grupoId) {
        boolean deleted = grupoRepo.deleteByIdAndTenant(orgId, grupoId);
        if (!deleted) {
            throw new NotFoundException("Grupo de residentes no encontrado");
        }
    }

    /**
     * Actualiza únicamente el estado del grupo dentro del tenant.
     *
     * @throws NotFoundException si no existe en el tenant
     */
    @Transactional
    public GrupoResidentesResponse updateEstado(UUID orgId, UUID grupoId,
            GrupoResidentesEstadoRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        GrupoResidentes g = getGrupoOrThrow(orgId, grupoId);
        g.setEstado(req.estado());

        grupoRepo.flush();
        return toResponseLite(g);
    }

    // ---------------------------------------------------------------------------
    // Membresía del grupo (residentes)
    // ---------------------------------------------------------------------------

    /**
     * Agrega residentes al grupo.
     *
     * <p>
     * Valida que todos los IDs enviados existan dentro del tenant. Si falta alguno, retorna 400
     * indicando inconsistencia en el request.
     * </p>
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @param req request con IDs a agregar
     * @return grupo actualizado incluyendo residentes
     */
    @Transactional
    public GrupoResidentesResponse addResidentes(UUID orgId, UUID grupoId,
            GrupoResidentesAddResidentesRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        // Validación fuerte: todos los IDs deben existir en el tenant
        ensureAllResidentesExistInTenant(orgId, req.residentesId());

        GrupoResidentes g = grupoRepo.addResidentes(orgId, grupoId, req.residentesId())
                .orElseThrow(() -> new NotFoundException("Grupo de residentes no encontrado"));

        grupoRepo.flush();
        return toResponseFull(g);
    }

    /**
     * Elimina residentes del grupo.
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @param req request con IDs a eliminar
     * @return grupo actualizado incluyendo residentes
     */
    @Transactional
    public GrupoResidentesResponse removeResidentes(UUID orgId, UUID grupoId,
            GrupoResidentesRemoveResidentesRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        GrupoResidentes g = grupoRepo.removeResidentes(orgId, grupoId, req.residentesId())
                .orElseThrow(() -> new NotFoundException("Grupo de residentes no encontrado"));

        grupoRepo.flush();
        return toResponseFull(g);
    }

    /**
     * Reemplaza completamente los residentes del grupo por los IDs enviados.
     *
     * <p>
     * Si se envían IDs inexistentes o de otro tenant, retorna 400.
     * </p>
     */
    @Transactional
    public GrupoResidentesResponse replaceResidentes(UUID orgId, UUID grupoId,
            GrupoResidentesReplaceResidentesRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Set<UUID> ids = (req.residentesId() == null) ? Set.of() : req.residentesId();
        ensureAllResidentesExistInTenant(orgId, ids);

        GrupoResidentes g = grupoRepo.replaceResidentes(orgId, grupoId, ids)
                .orElseThrow(() -> new NotFoundException("Grupo de residentes no encontrado"));

        grupoRepo.flush();
        return toResponseFull(g);
    }

    // ---------------------------------------------------------------------------
    // Helpers privados
    // ---------------------------------------------------------------------------

    private GrupoResidentes getGrupoOrThrow(UUID orgId, UUID grupoId) {
        return grupoRepo.findByIdAndTenant(orgId, grupoId)
                .orElseThrow(() -> new NotFoundException("Grupo de residentes no encontrado"));
    }

    private GrupoResidentes getGrupoWithResidentesOrThrow(UUID orgId, UUID grupoId) {
        return grupoRepo.findByIdWithResidentes(orgId, grupoId)
                .orElseThrow(() -> new NotFoundException("Grupo de residentes no encontrado"));
    }

    private void ensureNombreUnicoOnCreate(UUID orgId, String nombre) {
        if (grupoRepo.existsNombre(orgId, nombre, null)) {
            throw conflictNombre();
        }
    }

    private void ensureNombreUnicoOnUpdate(UUID orgId, String nombre, UUID grupoId) {
        if (grupoRepo.existsNombre(orgId, nombre, grupoId)) {
            throw conflictNombre();
        }
    }

    private WebApplicationException conflictNombre() {
        return new WebApplicationException("Ya existe un grupo con ese nombre en la organización",
                Response.Status.CONFLICT);
    }

    /**
     * Valida que TODOS los residentes enviados existan en el tenant.
     *
     * <p>
     * Decisión: si el cliente envía IDs inválidos (no existen o son de otro tenant), se responde
     * 400 en lugar de "ignorar silenciosamente", para evitar inconsistencias.
     * </p>
     */
    private void ensureAllResidentesExistInTenant(UUID orgId, Set<UUID> ids) {
        if (ids == null || ids.isEmpty())
            return;

        List<Residente> found = grupoRepo.fetchResidentesByIds(orgId, ids);
        if (found.size() != ids.size()) {
            // Identificamos cuáles faltan para devolver un mensaje útil.
            Set<UUID> foundIds =
                    found.stream().map(Residente::getIdResidente).collect(Collectors.toSet());
            Set<UUID> missing =
                    ids.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toSet());

            throw new WebApplicationException(
                    "Residentes inválidos (no existen o no pertenecen a la organización): "
                            + missing,
                    Response.Status.BAD_REQUEST);
        }
    }

    /**
     * Heurística para detectar violaciones de unicidad (Postgres) y/o por nombre de constraint.
     *
     * <p>
     * Debes alinear {@link #UK_GRUPO_RESIDENTES_NOMBRE} con el nombre real del constraint si lo
     * defines en base de datos.
     * </p>
     */
    private boolean isUniqueNombreViolation(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains(UK_GRUPO_RESIDENTES_NOMBRE)) {
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

    /**
     * Mapea a DTO "lite" (sin residentes).
     */
    private GrupoResidentesResponse toResponseLite(GrupoResidentes g) {
        return new GrupoResidentesResponse(g.getIdGrupoResidente(), g.getIdOrganizacion(),
                g.getNombre(), g.getEstado(), null, g.getCreadoEnUtc(), g.getActualizadoEnUtc());
    }

    /**
     * Mapea a DTO "full" (incluye residentes).
     */
    private GrupoResidentesResponse toResponseFull(GrupoResidentes g) {
        List<ResidenteResumenResponse> residentes = (g.getResidentes() == null) ? List.of()
                : g.getResidentes().stream().map(this::toResidenteResumen)
                        .sorted((a, b) -> a.nombre().compareToIgnoreCase(b.nombre())).toList();

        return new GrupoResidentesResponse(g.getIdGrupoResidente(), g.getIdOrganizacion(),
                g.getNombre(), g.getEstado(), residentes, g.getCreadoEnUtc(),
                g.getActualizadoEnUtc());
    }

    private ResidenteResumenResponse toResidenteResumen(Residente r) {
        return new ResidenteResumenResponse(r.getIdResidente(), r.getNombre(), r.getTipoDocumento(),
                r.getNumeroDocumento(), r.getEstado());
    }
}
