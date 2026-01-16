package com.haedcom.access.application.grupo_visitantes;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.grupo_visitantes.dto.GrupoVisitantesEstadoRequest;
import com.haedcom.access.api.grupo_visitantes.dto.GrupoVisitantesMiembrosRequest;
import com.haedcom.access.api.grupo_visitantes.dto.GrupoVisitantesResponse;
import com.haedcom.access.api.grupo_visitantes.dto.GrupoVisitantesUpsertRequest;
import com.haedcom.access.api.grupo_visitantes.dto.VisitanteLiteResponse;
import com.haedcom.access.domain.enums.EstadoGrupo;
import com.haedcom.access.domain.model.GrupoVisitantes;
import com.haedcom.access.domain.model.Organizacion;
import com.haedcom.access.domain.model.VisitantePreautorizado;
import com.haedcom.access.domain.repo.GrupoVisitantesRepository;
import com.haedcom.access.domain.repo.OrganizacionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Servicio de aplicación que orquesta los casos de uso para la gestión de {@link GrupoVisitantes}
 * dentro de una organización (multi-tenant por {@code idOrganizacion}).
 *
 * <p>
 * Responsabilidades principales:
 * <ul>
 * <li>Aislamiento por tenant: todas las operaciones se ejecutan en el contexto de
 * {@code orgId}.</li>
 * <li>Coordinar repositorio y dominio.</li>
 * <li>Validaciones que requieren DB (p.ej. unicidad por nombre).</li>
 * <li>Gestión de membresía (add/remove/replace de visitantes).</li>
 * <li>Traducir violaciones de integridad a errores HTTP consistentes (p.ej. 409 por
 * duplicado).</li>
 * </ul>
 * </p>
 *
 * <p>
 * Nota: validaciones intrínsecas (nombre obligatorio, longitudes, estado no null) se delegan al
 * dominio ({@link GrupoVisitantes}) y/o a Bean Validation en los DTOs.
 * </p>
 */
@ApplicationScoped
public class GrupoVisitantesService {

    /**
     * Nombre del constraint UNIQUE en base de datos para (id_organizacion, nombre).
     *
     * <p>
     * Usado para detectar y traducir a {@code 409 Conflict} conflictos de nombre.
     * </p>
     */
    private static final String UK_GRUPO_VISITANTES_NOMBRE = "ux_grupo_visitantes_nombre";

    private final GrupoVisitantesRepository repo;
    private final OrganizacionRepository orgRepo;

    public GrupoVisitantesService(GrupoVisitantesRepository repo, OrganizacionRepository orgRepo) {
        this.repo = Objects.requireNonNull(repo, "repo es obligatorio");
        this.orgRepo = Objects.requireNonNull(orgRepo, "orgRepo es obligatorio");
    }

    /**
     * Lista grupos de visitantes de una organización de forma paginada, con filtros opcionales.
     *
     * <p>
     * Por defecto, este listado NO incluye la colección de visitantes (evita N+1).
     * </p>
     *
     * @param orgId tenant
     * @param q búsqueda parcial por nombre (opcional)
     * @param estado filtro por estado (opcional)
     * @param page página base 0
     * @param size tamaño de página
     * @return respuesta paginada con metadatos
     */
    @Transactional
    public PageResponse<GrupoVisitantesResponse> list(UUID orgId, String q, EstadoGrupo estado,
            int page, int size) {

        List<GrupoVisitantesResponse> items = repo.listByTenant(orgId, page, size, q, estado)
                .stream().map(g -> toResponse(g, false)).toList();

        long total = repo.countByTenant(orgId, q, estado);

        return PageResponse.of(items, page, size, total);
    }

    /**
     * Obtiene un grupo por id dentro del tenant.
     *
     * @param orgId tenant
     * @param grupoId id del grupo
     * @param includeVisitantes si true, retorna también los visitantes
     * @return DTO del grupo
     * @throws NotFoundException si no existe o no pertenece al tenant
     */
    @Transactional
    public GrupoVisitantesResponse get(UUID orgId, UUID grupoId, boolean includeVisitantes) {
        GrupoVisitantes g = includeVisitantes ? getGrupoWithVisitantesOrThrow(orgId, grupoId)
                : getGrupoOrThrow(orgId, grupoId);

        return toResponse(g, includeVisitantes);
    }

    /**
     * Crea un grupo de visitantes dentro de una organización.
     *
     * <p>
     * Reglas aplicadas:
     * <ul>
     * <li>La organización debe existir.</li>
     * <li>El nombre debe ser único dentro del tenant (validación previa + constraint UNIQUE).</li>
     * <li>Opcionalmente, se puede setear membresía inicial mediante {@code visitantesId}.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Concurrencia: se fuerza {@code flush()} para detectar violaciones del UNIQUE dentro del
     * método y traducirlas consistentemente a {@code 409 Conflict}.
     * </p>
     */
    @Transactional
    public GrupoVisitantesResponse create(UUID orgId, GrupoVisitantesUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Organizacion org = getOrganizacionOrThrow(orgId);

        ensureNombreUnicoOnCreate(orgId, req.nombre());

        GrupoVisitantes g = GrupoVisitantes.crear(req.nombre());
        g.setOrganizacionTenant(org);

        // membresía inicial (opcional)
        if (req.visitantesId() != null && !req.visitantesId().isEmpty()) {
            List<VisitantePreautorizado> visitantes =
                    repo.fetchVisitantesByIds(orgId, req.visitantesId());
            // si quieres estricto: todos deben existir / ser del tenant
            ensureAllVisitantesResolved(req.visitantesId(), visitantes);
            g.setVisitantes(new HashSet<>(visitantes));
        }

        try {
            repo.persist(g);
            repo.flush();
            // por defecto devolvemos sin detalle; si quieres con detalle, cambia a true
            return toResponse(g, false);
        } catch (RuntimeException e) {
            if (isUniqueNombreViolation(e)) {
                throw conflictNombre();
            }
            throw e;
        }
    }

    /**
     * Actualiza el nombre (y opcionalmente reemplaza membresía si se envía {@code visitantesId}).
     *
     * <p>
     * Reglas:
     * <ul>
     * <li>El grupo debe existir y pertenecer al tenant.</li>
     * <li>Unicidad de nombre excluyendo el grupo actual.</li>
     * <li>Si {@code visitantesId} viene null: NO toca la membresía.</li>
     * <li>Si {@code visitantesId} viene no-null: reemplaza completamente la membresía.</li>
     * </ul>
     * </p>
     */
    @Transactional
    public GrupoVisitantesResponse update(UUID orgId, UUID grupoId,
            GrupoVisitantesUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        // si vamos a tocar membresía, traemos con visitantes para evitar problemas LAZY
        boolean replaceMembers = req.visitantesId() != null;

        GrupoVisitantes g = replaceMembers ? getGrupoWithVisitantesOrThrow(orgId, grupoId)
                : getGrupoOrThrow(orgId, grupoId);

        ensureNombreUnicoOnUpdate(orgId, req.nombre(), grupoId);

        g.setNombre(req.nombre());

        if (replaceMembers) {
            Set<UUID> ids = req.visitantesId();
            if (ids == null)
                ids = Set.of(); // por contrato, null ya se trató, pero defensivo

            List<VisitantePreautorizado> visitantes =
                    ids.isEmpty() ? List.of() : repo.fetchVisitantesByIds(orgId, ids);

            ensureAllVisitantesResolved(ids, visitantes);

            g.getVisitantes().clear();
            g.getVisitantes().addAll(visitantes);
        }

        try {
            repo.flush();
            return toResponse(g, replaceMembers); // si se tocó membresía, devolvemos detalle
        } catch (RuntimeException e) {
            if (isUniqueNombreViolation(e)) {
                throw conflictNombre();
            }
            throw e;
        }
    }

    /**
     * Elimina un grupo de visitantes dentro del tenant.
     *
     * @throws NotFoundException si no existe o no pertenece al tenant
     */
    @Transactional
    public void delete(UUID orgId, UUID grupoId) {
        GrupoVisitantes g = getGrupoOrThrow(orgId, grupoId);
        repo.delete(g);
    }

    /**
     * Actualiza únicamente el estado del grupo.
     *
     * @param orgId tenant
     * @param grupoId id grupo
     * @param req nuevo estado
     * @return grupo actualizado
     */
    @Transactional
    public GrupoVisitantesResponse updateEstado(UUID orgId, UUID grupoId,
            GrupoVisitantesEstadoRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        GrupoVisitantes g = getGrupoOrThrow(orgId, grupoId);
        g.setEstado(req.estado());

        repo.flush();

        return toResponse(g, false);
    }

    // -------------------------------------------------------------------------
    // Casos de uso de membresía (visitantes)
    // -------------------------------------------------------------------------

    /**
     * Agrega visitantes a un grupo.
     *
     * <p>
     * Recomendación: este caso de uso es "incremental" (no reemplaza miembros existentes).
     * </p>
     */
    @Transactional
    public GrupoVisitantesResponse addVisitantes(UUID orgId, UUID grupoId,
            GrupoVisitantesMiembrosRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Set<UUID> ids = req.visitantesId();
        if (ids.isEmpty()) {
            return toResponse(getGrupoWithVisitantesOrThrow(orgId, grupoId), true);
        }

        // validación estricta (todos existen y son del tenant)
        List<VisitantePreautorizado> visitantes = repo.fetchVisitantesByIds(orgId, ids);
        ensureAllVisitantesResolved(ids, visitantes);

        GrupoVisitantes g = repo.addVisitantes(orgId, grupoId, ids)
                .orElseThrow(() -> new NotFoundException("Grupo de visitantes no encontrado"));

        repo.flush();

        return toResponse(g, true);
    }

    /**
     * Elimina visitantes de un grupo.
     *
     * <p>
     * Si el set contiene IDs que no están en el grupo, simplemente no tiene efecto para esos IDs.
     * </p>
     */
    @Transactional
    public GrupoVisitantesResponse removeVisitantes(UUID orgId, UUID grupoId,
            GrupoVisitantesMiembrosRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Set<UUID> ids = req.visitantesId();
        if (ids.isEmpty()) {
            return toResponse(getGrupoWithVisitantesOrThrow(orgId, grupoId), true);
        }

        GrupoVisitantes g = repo.removeVisitantes(orgId, grupoId, ids)
                .orElseThrow(() -> new NotFoundException("Grupo de visitantes no encontrado"));

        repo.flush();

        return toResponse(g, true);
    }

    /**
     * Reemplaza completamente la membresía del grupo por el set indicado.
     */
    @Transactional
    public GrupoVisitantesResponse replaceVisitantes(UUID orgId, UUID grupoId,
            GrupoVisitantesMiembrosRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Set<UUID> ids = req.visitantesId();
        // validación estricta: si manda N ids, deben existir N
        if (!ids.isEmpty()) {
            List<VisitantePreautorizado> visitantes = repo.fetchVisitantesByIds(orgId, ids);
            ensureAllVisitantesResolved(ids, visitantes);
        }

        GrupoVisitantes g = repo.replaceVisitantes(orgId, grupoId, ids)
                .orElseThrow(() -> new NotFoundException("Grupo de visitantes no encontrado"));

        repo.flush();

        return toResponse(g, true);
    }

    // -------------------------------------------------------------------------
    // Helpers privados (tenant / unicidad / mapeo)
    // -------------------------------------------------------------------------

    private Organizacion getOrganizacionOrThrow(UUID orgId) {
        return orgRepo.findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organización no encontrada"));
    }

    private GrupoVisitantes getGrupoOrThrow(UUID orgId, UUID grupoId) {
        return repo.findByIdAndTenant(orgId, grupoId)
                .orElseThrow(() -> new NotFoundException("Grupo de visitantes no encontrado"));
    }

    private GrupoVisitantes getGrupoWithVisitantesOrThrow(UUID orgId, UUID grupoId) {
        return repo.findByIdWithVisitantes(orgId, grupoId)
                .orElseThrow(() -> new NotFoundException("Grupo de visitantes no encontrado"));
    }

    private void ensureNombreUnicoOnCreate(UUID orgId, String nombre) {
        if (repo.existsNombre(orgId, nombre, null)) {
            throw conflictNombre();
        }
    }

    private void ensureNombreUnicoOnUpdate(UUID orgId, String nombre, UUID grupoId) {
        if (repo.existsNombre(orgId, nombre, grupoId)) {
            throw conflictNombre();
        }
    }

    private WebApplicationException conflictNombre() {
        return new WebApplicationException(
                "Ya existe un grupo de visitantes con ese nombre en la organización",
                Response.Status.CONFLICT);
    }

    /**
     * Determina si la excepción (o alguna de sus causas) corresponde a una violación de unicidad de
     * nombre.
     *
     * <p>
     * Estrategia:
     * <ul>
     * <li>Detectar el nombre del constraint {@code ux_grupo_visitantes_nombre} en el mensaje.</li>
     * <li>O detectar {@code SQLState 23505} (unique_violation) si aparece una SQLException.</li>
     * </ul>
     * </p>
     */
    private boolean isUniqueNombreViolation(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains(UK_GRUPO_VISITANTES_NOMBRE)) {
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
     * Valida que todos los IDs solicitados fueron resueltos (existen y pertenecen al tenant).
     *
     * <p>
     * Esta validación es importante para evitar:
     * <ul>
     * <li>Agregar visitantes de otro tenant.</li>
     * <li>Quedar en un estado parcial silencioso.</li>
     * </ul>
     * </p>
     *
     * @param requestedIds ids solicitados por el cliente
     * @param resolved entidades retornadas por el repo
     * @throws WebApplicationException 400 si hay IDs inválidos
     */
    private void ensureAllVisitantesResolved(Set<UUID> requestedIds,
            List<VisitantePreautorizado> resolved) {

        if (requestedIds == null || requestedIds.isEmpty())
            return;

        Set<UUID> found = resolved.stream().map(VisitantePreautorizado::getIdVisitante)
                .collect(java.util.stream.Collectors.toSet());

        if (found.size() != requestedIds.size()) {
            Set<UUID> missing = new HashSet<>(requestedIds);
            missing.removeAll(found);

            throw new WebApplicationException(
                    "Uno o más visitantes no existen o no pertenecen a la organización: " + missing,
                    Response.Status.BAD_REQUEST);
        }
    }

    private GrupoVisitantesResponse toResponse(GrupoVisitantes g, boolean includeVisitantes) {
        List<VisitanteLiteResponse> visitantes = null;

        if (includeVisitantes) {
            visitantes = (g.getVisitantes() == null) ? List.of()
                    : g.getVisitantes().stream().map(this::toVisitanteLite)
                            .sorted((a, b) -> a.nombre().compareToIgnoreCase(b.nombre())).toList();
        }

        return new GrupoVisitantesResponse(g.getIdGrupoVisitante(), g.getIdOrganizacion(),
                g.getNombre(), g.getEstado(), visitantes, g.getCreadoEnUtc(),
                g.getActualizadoEnUtc());
    }

    private VisitanteLiteResponse toVisitanteLite(VisitantePreautorizado v) {
        return new VisitanteLiteResponse(v.getIdVisitante(), v.getNombre(), v.getTipoDocumento(),
                v.getNumeroDocumento());
    }
}
