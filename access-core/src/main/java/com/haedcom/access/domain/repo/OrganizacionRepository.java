package com.haedcom.access.domain.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.haedcom.access.domain.model.Organizacion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import jakarta.ws.rs.NotFoundException;

/**
 * Repositorio JPA para {@link Organizacion}.
 *
 * <p>
 * Nota: {@link Organizacion#timezoneId} almacena una zona horaria IANA (ej. "America/Bogota"),
 * usada como default del tenant para evaluar ventanas diarias y conversiones a "hora local".
 * </p>
 */
@ApplicationScoped
public class OrganizacionRepository extends BaseRepository<Organizacion, UUID> {

    public OrganizacionRepository() {
        super(Organizacion.class);
    }

    /**
     * Obtiene el {@code timezoneId} (IANA) del tenant.
     *
     * @param orgId id de organización (obligatorio)
     * @return {@link Optional} con timezoneId si la organización existe; vacío si no existe
     */
    public Optional<String> findTimezoneId(UUID orgId) {
        List<String> res = em.createQuery("""
                select o.timezoneId
                from Organizacion o
                where o.idOrganizacion = :orgId
                """, String.class).setParameter("orgId", orgId).getResultList();

        if (res.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(res.get(0)); // debería ser NOT NULL, pero lo dejamos defensivo
    }

    /**
     * Igual que {@link #findTimezoneId(UUID)} pero falla si el tenant no existe.
     *
     * <p>
     * Útil si tu arquitectura asume que todo request trae un orgId válido.
     * </p>
     *
     * @param orgId id de organización (obligatorio)
     * @return timezoneId del tenant (no blank)
     * @throws jakarta.ws.rs.NotFoundException si la organización no existe
     */
    public String findTimezoneIdOrThrow(UUID orgId) {
        return findTimezoneId(orgId).orElseThrow(
                () -> new jakarta.ws.rs.NotFoundException("Organización no encontrada"));
    }

    // ---------------------------------------------------------------------
    // CRUD (apoyándose en BaseRepository)
    // ---------------------------------------------------------------------

    /**
     * Persiste una nueva {@link Organizacion}.
     *
     * <p>
     * Delegado a {@link BaseRepository#persist(Object)}. No realiza {@code flush()}
     * automáticamente.
     * </p>
     *
     * @param organizacion entidad a persistir (obligatoria)
     * @return la misma instancia (ya gestionada por JPA)
     * @throws IllegalArgumentException si {@code organizacion} es {@code null}
     */
    public Organizacion create(Organizacion organizacion) {
        if (organizacion == null) {
            throw new IllegalArgumentException("organizacion no puede ser null");
        }
        return persist(organizacion);
    }

    /**
     * Actualiza una {@link Organizacion}.
     *
     * <p>
     * Delegado a {@link BaseRepository#merge(Object)}. Devuelve la instancia administrada
     * (managed). No valida existencia previa: si el id no existe, el comportamiento depende de
     * JPA/DB.
     * </p>
     *
     * @param organizacion entidad con cambios (obligatoria)
     * @return instancia gestionada por JPA tras el merge
     * @throws IllegalArgumentException si {@code organizacion} es {@code null}
     */
    public Organizacion update(Organizacion organizacion) {
        if (organizacion == null) {
            throw new IllegalArgumentException("organizacion no puede ser null");
        }
        return merge(organizacion);
    }

    /**
     * Obtiene una {@link Organizacion} por id o falla si no existe.
     *
     * <p>
     * Usa {@link BaseRepository#findById(Object)} como fuente y normaliza el error.
     * </p>
     *
     * @param orgId id de organización (obligatorio)
     * @return entidad existente
     * @throws IllegalArgumentException si {@code orgId} es {@code null}
     * @throws NotFoundException si la organización no existe
     */
    public Organizacion findByIdOrThrow(UUID orgId) {
        if (orgId == null) {
            throw new IllegalArgumentException("orgId no puede ser null");
        }
        return findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organización no encontrada"));
    }

    /**
     * Lista todas las organizaciones ordenadas por nombre (asc) y luego por id (asc).
     *
     * @return lista (posiblemente vacía)
     */
    public List<Organizacion> findAll() {
        return em.createQuery("""
                select o
                from Organizacion o
                order by o.nombre asc, o.idOrganizacion asc
                """, Organizacion.class).getResultList();
    }

    /**
     * Lista organizaciones con paginación (offset/limit), ordenadas por nombre (asc) y luego por id
     * (asc).
     *
     * <p>
     * Se define aquí (en lugar de {@link BaseRepository#listAll(int, int)}) para garantizar un
     * orden estable.
     * </p>
     *
     * @param offset cantidad de registros a omitir (>= 0)
     * @param limit máximo de registros a retornar (> 0)
     * @return lista (posiblemente vacía)
     * @throws IllegalArgumentException si {@code offset < 0} o {@code limit <= 0}
     */
    public List<Organizacion> findAll(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset debe ser >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit debe ser > 0");
        }

        TypedQuery<Organizacion> q = em.createQuery("""
                select o
                from Organizacion o
                order by o.nombre asc, o.idOrganizacion asc
                """, Organizacion.class);

        q.setFirstResult(offset);
        q.setMaxResults(limit);
        return q.getResultList();
    }

    /**
     * Cuenta el total de organizaciones.
     *
     * @return total de registros
     */
    public long countAll() {
        return em.createQuery("""
                select count(o)
                from Organizacion o
                """, Long.class).getSingleResult();
    }

    /**
     * Verifica si existe una organización con el id dado.
     *
     * @param orgId id de organización (obligatorio)
     * @return {@code true} si existe; {@code false} si no existe
     * @throws IllegalArgumentException si {@code orgId} es {@code null}
     */
    public boolean existsById(UUID orgId) {
        if (orgId == null) {
            throw new IllegalArgumentException("orgId no puede ser null");
        }
        Long count = em.createQuery("""
                select count(o)
                from Organizacion o
                where o.idOrganizacion = :orgId
                """, Long.class).setParameter("orgId", orgId).getSingleResult();
        return count != null && count > 0;
    }

    /**
     * Elimina una organización por id, si existe.
     *
     * <p>
     * No falla si no existe (idempotente).
     * </p>
     *
     * @param orgId id de organización (obligatorio)
     * @return {@code true} si se eliminó; {@code false} si no existía
     * @throws IllegalArgumentException si {@code orgId} es {@code null}
     */
    public boolean deleteById(UUID orgId) {
        if (orgId == null) {
            throw new IllegalArgumentException("orgId no puede ser null");
        }
        Organizacion existing = em.find(Organizacion.class, orgId);
        if (existing == null) {
            return false;
        }
        delete(existing); // BaseRepository#delete
        return true;
    }

    /**
     * Elimina una organización por id o falla si no existe.
     *
     * @param orgId id de organización (obligatorio)
     * @throws IllegalArgumentException si {@code orgId} es {@code null}
     * @throws NotFoundException si la organización no existe
     */
    public void deleteByIdOrThrow(UUID orgId) {
        boolean deleted = deleteById(orgId);
        if (!deleted) {
            throw new NotFoundException("Organización no encontrada");
        }
    }
}
