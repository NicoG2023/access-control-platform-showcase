package com.haedcom.access.domain.repo;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoGrupo;
import com.haedcom.access.domain.model.GrupoResidentes;
import com.haedcom.access.domain.model.Residente;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;

/**
 * Repositorio JPA para {@link GrupoResidentes}.
 *
 * <h3>Responsabilidad</h3>
 * <ul>
 * <li>Acceso a datos (CRUD) y consultas específicas.</li>
 * <li>Operaciones tenant-aware: SIEMPRE filtra por {@code idOrganizacion}.</li>
 * <li>Lecturas con {@code JOIN FETCH} cuando se requiere materializar la relación
 * {@code residentes} y evitar N+1 o LazyInitializationException.</li>
 * </ul>
 *
 * <h3>No es responsabilidad</h3>
 * <ul>
 * <li>Validaciones de negocio (permisos, reglas por estado, límites, etc.).</li>
 * <li>Control transaccional (se realiza en la capa de servicio).</li>
 * </ul>
 */
@ApplicationScoped
public class GrupoResidentesRepository extends BaseRepository<GrupoResidentes, UUID> {

    public GrupoResidentesRepository() {
        super(GrupoResidentes.class);
    }

    /**
     * Busca un grupo por su ID dentro del tenant.
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @return grupo si existe en el tenant; vacío si no existe o pertenece a otro tenant
     */
    public Optional<GrupoResidentes> findByIdAndTenant(UUID orgId, UUID grupoId) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        return em
                .createQuery(
                        "select g from GrupoResidentes g "
                                + "where g.idOrganizacion = :orgId and g.idGrupoResidente = :id",
                        GrupoResidentes.class)
                .setParameter("orgId", orgId).setParameter("id", grupoId).getResultStream()
                .findFirst();
    }

    /**
     * Busca un grupo por su ID dentro del tenant, trayendo también la colección de residentes.
     *
     * <p>
     * Útil para "lecturas detalle" o para actualizar la membresía del grupo sin incurrir en
     * problemas de carga perezosa (LAZY).
     * </p>
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @return grupo (con residentes materializados) si existe en el tenant; vacío en caso contrario
     */
    public Optional<GrupoResidentes> findByIdWithResidentes(UUID orgId, UUID grupoId) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        // DISTINCT evita duplicados por la relación ManyToMany.
        return em
                .createQuery(
                        "select distinct g from GrupoResidentes g "
                                + "left join fetch g.residentes r "
                                + "where g.idOrganizacion = :orgId and g.idGrupoResidente = :id",
                        GrupoResidentes.class)
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
    public List<GrupoResidentes> listByTenant(UUID orgId, int page, int size, String nombreLike,
            EstadoGrupo estado) {

        require(orgId, "orgId");
        if (page < 0)
            throw new IllegalArgumentException("page debe ser >= 0");
        if (size <= 0)
            throw new IllegalArgumentException("size debe ser > 0");

        StringBuilder jpql = new StringBuilder().append("select g from GrupoResidentes g ")
                .append("where g.idOrganizacion = :orgId ");

        boolean hasNombre = nombreLike != null && !nombreLike.trim().isBlank();
        if (hasNombre) {
            jpql.append("and lower(g.nombre) like :q ");
        }
        if (estado != null) {
            jpql.append("and g.estado = :estado ");
        }

        jpql.append("order by g.nombre asc");

        TypedQuery<GrupoResidentes> query = em.createQuery(jpql.toString(), GrupoResidentes.class)
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
    public Optional<GrupoResidentes> findByNombre(UUID orgId, String nombre) {
        require(orgId, "orgId");
        String v = normalizeRequired(nombre, "nombre");

        return em
                .createQuery(
                        "select g from GrupoResidentes g "
                                + "where g.idOrganizacion = :orgId and lower(g.nombre) = :nombre",
                        GrupoResidentes.class)
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

        StringBuilder jpql = new StringBuilder().append("select count(g) from GrupoResidentes g ")
                .append("where g.idOrganizacion = :orgId ")
                .append("and lower(g.nombre) = :nombre ");

        if (excludeGrupoId != null) {
            jpql.append("and g.idGrupoResidente <> :excludeId ");
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
                .createQuery("delete from GrupoResidentes g "
                        + "where g.idOrganizacion = :orgId and g.idGrupoResidente = :id")
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

        StringBuilder jpql = new StringBuilder().append("select count(g) from GrupoResidentes g ")
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
    // Gestión de miembros (residentes) en un grupo
    // ---------------------------------------------------------------------------

    /**
     * Agrega uno o varios residentes a un grupo.
     *
     * <p>
     * Requisitos:
     * <ul>
     * <li>Debe ejecutarse dentro de una transacción en la capa de servicio.</li>
     * <li>El grupo debe pertenecer al tenant indicado.</li>
     * <li>Los residentes se validan por tenant: solo se agregan residentes cuyo
     * {@code idOrganizacion} coincide con {@code orgId}.</li>
     * </ul>
     * </p>
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @param residentesId IDs de residentes a agregar (si null/vacío, no hace nada)
     * @return el grupo actualizado (con residentes materializados)
     */
    public Optional<GrupoResidentes> addResidentes(UUID orgId, UUID grupoId,
            Set<UUID> residentesId) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        if (residentesId == null || residentesId.isEmpty()) {
            return findByIdWithResidentes(orgId, grupoId);
        }

        Optional<GrupoResidentes> optGrupo = findByIdWithResidentes(orgId, grupoId);
        if (optGrupo.isEmpty())
            return Optional.empty();

        GrupoResidentes grupo = optGrupo.get();
        ensureResidentesCollection(grupo);

        List<Residente> residentes = fetchResidentesByIds(orgId, residentesId);
        // Set evita duplicados naturalmente (si equals/hashCode está por identidad JPA, aun así no
        // duplica en join)
        grupo.getResidentes().addAll(residentes);

        return Optional.of(grupo);
    }

    /**
     * Elimina uno o varios residentes de un grupo.
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
     * @param residentesId IDs de residentes a eliminar (si null/vacío, no hace nada)
     * @return el grupo actualizado (con residentes materializados)
     */
    public Optional<GrupoResidentes> removeResidentes(UUID orgId, UUID grupoId,
            Set<UUID> residentesId) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        if (residentesId == null || residentesId.isEmpty()) {
            return findByIdWithResidentes(orgId, grupoId);
        }

        Optional<GrupoResidentes> optGrupo = findByIdWithResidentes(orgId, grupoId);
        if (optGrupo.isEmpty())
            return Optional.empty();

        GrupoResidentes grupo = optGrupo.get();
        ensureResidentesCollection(grupo);

        // Remueve por ID (sin necesidad de cargar residentes a borrar, aunque ya está
        // materializado).
        grupo.getResidentes().removeIf(r -> residentesId.contains(r.getIdResidente()));

        return Optional.of(grupo);
    }

    /**
     * Reemplaza completamente la membresía del grupo por el set indicado.
     *
     * <p>
     * Muy útil para un {@code PUT /grupos/{id}} donde el cliente envía la lista final de
     * residentes. Valida tenant al cargar residentes.
     * </p>
     *
     * @param orgId identificador del tenant
     * @param grupoId identificador del grupo
     * @param nuevosResidentes IDs finales (si null, se interpreta como vacío)
     * @return el grupo actualizado
     */
    public Optional<GrupoResidentes> replaceResidentes(UUID orgId, UUID grupoId,
            Set<UUID> nuevosResidentes) {
        require(orgId, "orgId");
        require(grupoId, "grupoId");

        Optional<GrupoResidentes> optGrupo = findByIdWithResidentes(orgId, grupoId);
        if (optGrupo.isEmpty())
            return Optional.empty();

        GrupoResidentes grupo = optGrupo.get();
        ensureResidentesCollection(grupo);

        Set<UUID> ids = (nuevosResidentes == null) ? Collections.emptySet() : nuevosResidentes;
        List<Residente> residentes = ids.isEmpty() ? List.of() : fetchResidentesByIds(orgId, ids);

        grupo.getResidentes().clear();
        grupo.getResidentes().addAll(residentes);

        return Optional.of(grupo);
    }

    /**
     * Carga residentes por IDs validando tenant.
     *
     * <p>
     * Si el caller envía IDs que no existen o que pertenecen a otro tenant, simplemente no
     * aparecerán en el resultado. El service decidirá si eso debe disparar error (recomendado) o si
     * es tolerable.
     * </p>
     */
    public List<Residente> fetchResidentesByIds(UUID orgId, Set<UUID> residentesId) {
        require(orgId, "orgId");
        if (residentesId == null || residentesId.isEmpty())
            return List.of();

        return em
                .createQuery(
                        "select r from Residente r "
                                + "where r.idOrganizacion = :orgId and r.idResidente in :ids",
                        Residente.class)
                .setParameter("orgId", orgId).setParameter("ids", new HashSet<>(residentesId))
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
     * Garantiza que la colección no sea null y que esté inicializada (si aplica). En ManyToMany
     * LAZY, acceder a size() fuerza inicialización dentro de transacción.
     */
    private static void ensureResidentesCollection(GrupoResidentes grupo) {
        if (grupo.getResidentes() == null) {
            grupo.setResidentes(new HashSet<>());
            return;
        }
        // fuerza inicialización si es lazy
        grupo.getResidentes().size();
    }
}
