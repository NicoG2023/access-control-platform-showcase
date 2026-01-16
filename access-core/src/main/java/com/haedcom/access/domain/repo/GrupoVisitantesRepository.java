package com.haedcom.access.domain.repo;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoGrupo;
import com.haedcom.access.domain.model.GrupoVisitantes;
import com.haedcom.access.domain.model.VisitantePreautorizado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;

/**
 * Repositorio JPA para {@link GrupoVisitantes}.
 *
 * <h3>Responsabilidad</h3>
 * <ul>
 * <li>Acceso a datos (CRUD) y consultas específicas.</li>
 * <li>Operaciones tenant-aware: SIEMPRE filtra por {@code idOrganizacion}.</li>
 * <li>Lecturas con {@code JOIN FETCH} cuando se requiere materializar la relación
 * {@code visitantes} y evitar N+1 o LazyInitializationException.</li>
 * </ul>
 *
 * <h3>No es responsabilidad</h3>
 * <ul>
 * <li>Validaciones de negocio (permisos, reglas por estado, límites, etc.).</li>
 * <li>Control transaccional (se realiza en la capa de servicio).</li>
 * </ul>
 */
@ApplicationScoped
public class GrupoVisitantesRepository extends BaseRepository<GrupoVisitantes, UUID> {

    public GrupoVisitantesRepository() {
        super(GrupoVisitantes.class);
    }

    /**
     * Busca un grupo por su ID dentro del tenant.
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @return grupo si existe en el tenant; vacío si no existe o pertenece a otro tenant
     */
    public Optional<GrupoVisitantes> findByIdAndTenant(UUID orgId, UUID grupoId) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        return em
                .createQuery(
                        "select g from GrupoVisitantes g "
                                + "where g.idOrganizacion = :orgId and g.idGrupoVisitante = :id",
                        GrupoVisitantes.class)
                .setParameter("orgId", orgId).setParameter("id", grupoId).getResultStream()
                .findFirst();
    }

    /**
     * Busca un grupo por su ID dentro del tenant, trayendo también la colección de visitantes.
     *
     * <p>
     * Útil para "lecturas detalle" o para actualizar la membresía del grupo sin incurrir en
     * problemas de carga perezosa (LAZY).
     * </p>
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @return grupo (con visitantes materializados) si existe en el tenant; vacío en caso contrario
     */
    public Optional<GrupoVisitantes> findByIdWithVisitantes(UUID orgId, UUID grupoId) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        // DISTINCT evita duplicados por la relación ManyToMany.
        return em
                .createQuery(
                        "select distinct g from GrupoVisitantes g "
                                + "left join fetch g.visitantes v "
                                + "where g.idOrganizacion = :orgId and g.idGrupoVisitante = :id",
                        GrupoVisitantes.class)
                .setParameter("orgId", orgId).setParameter("id", grupoId).getResultStream()
                .findFirst();
    }

    /**
     * Lista grupos del tenant de forma paginada, con filtros opcionales por nombre y estado.
     *
     * @param orgId identificador del tenant
     * @param page número de página (base 0)
     * @param size tamaño de página (> 0)
     * @param nombreLike búsqueda parcial por nombre (case-insensitive). Si es null/blank, no
     *        filtra.
     * @param estado estado a filtrar. Si es null, no filtra por estado.
     * @return lista de grupos
     */
    public List<GrupoVisitantes> listByTenant(UUID orgId, int page, int size, String nombreLike,
            EstadoGrupo estado) {

        require(orgId, "orgId");
        if (page < 0)
            throw new IllegalArgumentException("page debe ser >= 0");
        if (size <= 0)
            throw new IllegalArgumentException("size debe ser > 0");

        StringBuilder jpql = new StringBuilder().append("select g from GrupoVisitantes g ")
                .append("where g.idOrganizacion = :orgId ");

        boolean hasNombre = nombreLike != null && !nombreLike.trim().isBlank();
        if (hasNombre) {
            jpql.append("and lower(g.nombre) like :q ");
        }
        if (estado != null) {
            jpql.append("and g.estado = :estado ");
        }

        jpql.append("order by g.nombre asc");

        TypedQuery<GrupoVisitantes> query = em.createQuery(jpql.toString(), GrupoVisitantes.class)
                .setParameter("orgId", orgId).setFirstResult(page * size).setMaxResults(size);

        if (hasNombre) {
            query.setParameter("q", "%" + nombreLike.trim().toLowerCase() + "%");
        }
        if (estado != null) {
            query.setParameter("estado", estado);
        }

        return query.getResultList();
    }

    /**
     * Busca un grupo por nombre exacto dentro del tenant (case-insensitive).
     *
     * <p>
     * Útil como soporte para validaciones de unicidad en create/update.
     * </p>
     *
     * @param orgId identificador del tenant
     * @param nombre nombre exacto a buscar
     * @return grupo si existe; vacío en caso contrario
     */
    public Optional<GrupoVisitantes> findByNombre(UUID orgId, String nombre) {
        require(orgId, "orgId");
        String v = normalizeRequired(nombre, "nombre");

        return em
                .createQuery(
                        "select g from GrupoVisitantes g "
                                + "where g.idOrganizacion = :orgId and lower(g.nombre) = :nombre",
                        GrupoVisitantes.class)
                .setParameter("orgId", orgId).setParameter("nombre", v.toLowerCase())
                .getResultStream().findFirst();
    }

    /**
     * Indica si ya existe un grupo con el mismo nombre dentro del tenant (case-insensitive),
     * opcionalmente excluyendo un ID (útil para updates).
     *
     * @param orgId identificador del tenant
     * @param nombre nombre a validar
     * @param excludeGrupoId ID a excluir; null si no aplica
     * @return true si existe un grupo con ese nombre (distinto al excluido); false si no existe
     */
    public boolean existsNombre(UUID orgId, String nombre, UUID excludeGrupoId) {
        require(orgId, "orgId");
        String v = normalizeRequired(nombre, "nombre");

        StringBuilder jpql = new StringBuilder().append("select count(g) from GrupoVisitantes g ")
                .append("where g.idOrganizacion = :orgId ")
                .append("and lower(g.nombre) = :nombre ");

        if (excludeGrupoId != null) {
            jpql.append("and g.idGrupoVisitante <> :excludeId ");
        }

        var query = em.createQuery(jpql.toString(), Long.class).setParameter("orgId", orgId)
                .setParameter("nombre", v.toLowerCase());

        if (excludeGrupoId != null) {
            query.setParameter("excludeId", excludeGrupoId);
        }

        Long count = query.getSingleResult();
        return count != null && count > 0;
    }

    /**
     * Elimina un grupo por ID dentro del tenant sin necesidad de cargar la entidad.
     *
     * <p>
     * Retorna {@code true} si se eliminó un registro; {@code false} si no existía en ese tenant.
     * </p>
     */
    public boolean deleteByIdAndTenant(UUID orgId, UUID grupoId) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        int deleted = em
                .createQuery("delete from GrupoVisitantes g "
                        + "where g.idOrganizacion = :orgId and g.idGrupoVisitante = :id")
                .setParameter("orgId", orgId).setParameter("id", grupoId).executeUpdate();

        return deleted > 0;
    }

    /**
     * Cuenta grupos dentro del tenant con filtros opcionales (para paginación).
     *
     * @param orgId identificador del tenant
     * @param nombreLike búsqueda parcial (case-insensitive). Si es null/blank, no filtra.
     * @param estado estado a filtrar. Si es null, no filtra.
     * @return total de grupos que cumplen los filtros
     */
    public long countByTenant(UUID orgId, String nombreLike, EstadoGrupo estado) {
        require(orgId, "orgId");

        StringBuilder jpql = new StringBuilder().append("select count(g) from GrupoVisitantes g ")
                .append("where g.idOrganizacion = :orgId ");

        boolean hasNombre = nombreLike != null && !nombreLike.trim().isBlank();
        if (hasNombre) {
            jpql.append("and lower(g.nombre) like :q ");
        }
        if (estado != null) {
            jpql.append("and g.estado = :estado ");
        }

        TypedQuery<Long> query =
                em.createQuery(jpql.toString(), Long.class).setParameter("orgId", orgId);

        if (hasNombre) {
            query.setParameter("q", "%" + nombreLike.trim().toLowerCase() + "%");
        }
        if (estado != null) {
            query.setParameter("estado", estado);
        }

        Long count = query.getSingleResult();
        return (count == null) ? 0L : count;
    }

    // ---------------------------------------------------------------------------
    // Gestión de miembros (visitantes) en un grupo
    // ---------------------------------------------------------------------------

    /**
     * Agrega uno o varios visitantes a un grupo.
     *
     * <p>
     * Requisitos:
     * <ul>
     * <li>Debe ejecutarse dentro de una transacción en la capa de servicio.</li>
     * <li>El grupo debe pertenecer al tenant indicado.</li>
     * <li>Los visitantes se validan por tenant: solo se agregan visitantes cuyo
     * {@code idOrganizacion} coincide con {@code orgId}.</li>
     * </ul>
     * </p>
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @param visitantesId IDs de visitantes a agregar (si null/vacío, no hace nada)
     * @return el grupo actualizado (con visitantes materializados)
     */
    public Optional<GrupoVisitantes> addVisitantes(UUID orgId, UUID grupoId,
            Set<UUID> visitantesId) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        if (visitantesId == null || visitantesId.isEmpty()) {
            return findByIdWithVisitantes(orgId, grupoId);
        }

        Optional<GrupoVisitantes> optGrupo = findByIdWithVisitantes(orgId, grupoId);
        if (optGrupo.isEmpty())
            return Optional.empty();

        GrupoVisitantes grupo = optGrupo.get();
        ensureVisitantesCollection(grupo);

        List<VisitantePreautorizado> visitantes = fetchVisitantesByIds(orgId, visitantesId);
        grupo.getVisitantes().addAll(visitantes);

        return Optional.of(grupo);
    }

    /**
     * Elimina uno o varios visitantes de un grupo.
     *
     * <p>
     * Requisitos:
     * <ul>
     * <li>Debe ejecutarse dentro de una transacción en la capa de servicio.</li>
     * <li>El grupo debe pertenecer al tenant indicado.</li>
     * </ul>
     * </p>
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @param visitantesId IDs de visitantes a eliminar (si null/vacío, no hace nada)
     * @return el grupo actualizado (con visitantes materializados)
     */
    public Optional<GrupoVisitantes> removeVisitantes(UUID orgId, UUID grupoId,
            Set<UUID> visitantesId) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        if (visitantesId == null || visitantesId.isEmpty()) {
            return findByIdWithVisitantes(orgId, grupoId);
        }

        Optional<GrupoVisitantes> optGrupo = findByIdWithVisitantes(orgId, grupoId);
        if (optGrupo.isEmpty())
            return Optional.empty();

        GrupoVisitantes grupo = optGrupo.get();
        ensureVisitantesCollection(grupo);

        grupo.getVisitantes().removeIf(v -> visitantesId.contains(v.getIdVisitante()));

        return Optional.of(grupo);
    }

    /**
     * Reemplaza completamente la membresía del grupo por el set indicado.
     *
     * <p>
     * Muy útil para un {@code PUT /grupos/{id}} donde el cliente envía la lista final de
     * visitantes. Valida tenant al cargar visitantes.
     * </p>
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @param nuevosVisitantes IDs finales (si null, se interpreta como vacío)
     * @return el grupo actualizado
     */
    public Optional<GrupoVisitantes> replaceVisitantes(UUID orgId, UUID grupoId,
            Set<UUID> nuevosVisitantes) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        Optional<GrupoVisitantes> optGrupo = findByIdWithVisitantes(orgId, grupoId);
        if (optGrupo.isEmpty())
            return Optional.empty();

        GrupoVisitantes grupo = optGrupo.get();
        ensureVisitantesCollection(grupo);

        Set<UUID> ids = (nuevosVisitantes == null) ? Collections.emptySet() : nuevosVisitantes;
        List<VisitantePreautorizado> visitantes =
                ids.isEmpty() ? List.of() : fetchVisitantesByIds(orgId, ids);

        grupo.getVisitantes().clear();
        grupo.getVisitantes().addAll(visitantes);

        return Optional.of(grupo);
    }

    /**
     * Carga visitantes por IDs validando tenant.
     *
     * <p>
     * Si el caller envía IDs que no existen o que pertenecen a otro tenant, simplemente no
     * aparecerán en el resultado. El service decidirá si eso debe disparar error (recomendado) o si
     * es tolerable.
     * </p>
     *
     * @param orgId identificador del tenant
     * @param visitantesId IDs a buscar
     * @return lista de visitantes que existen y pertenecen al tenant
     */
    public List<VisitantePreautorizado> fetchVisitantesByIds(UUID orgId, Set<UUID> visitantesId) {
        require(orgId, "orgId");
        if (visitantesId == null || visitantesId.isEmpty())
            return List.of();

        return em
                .createQuery(
                        "select v from VisitantePreautorizado v "
                                + "where v.idOrganizacion = :orgId and v.idVisitante in :ids",
                        VisitantePreautorizado.class)
                .setParameter("orgId", orgId).setParameter("ids", new HashSet<>(visitantesId))
                .getResultList();
    }

    // ---------------------------------------------------------------------------
    // Helpers internos
    // ---------------------------------------------------------------------------

    private static void require(Object v, String name) {
        if (v == null)
            throw new IllegalArgumentException(name + " no puede ser null");
    }

    private static String normalizeRequired(String v, String name) {
        String out = (v == null) ? null : v.trim();
        if (out == null || out.isBlank())
            throw new IllegalArgumentException(name + " es obligatorio");
        return out;
    }

    /**
     * Garantiza que la colección no sea null y que esté inicializada (si aplica).
     *
     * <p>
     * En ManyToMany LAZY, acceder a {@code size()} fuerza inicialización dentro de transacción.
     * </p>
     */
    private static void ensureVisitantesCollection(GrupoVisitantes grupo) {
        if (grupo.getVisitantes() == null) {
            grupo.setVisitantes(new HashSet<>());
            return;
        }
        // fuerza inicialización si es lazy
        grupo.getVisitantes().size();
    }
}
