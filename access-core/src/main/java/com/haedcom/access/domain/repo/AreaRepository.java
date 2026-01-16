package com.haedcom.access.domain.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.haedcom.access.domain.model.Area;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;

/**
 * Repositorio JPA para {@link Area}.
 *
 * <p>
 * Reglas multi-tenant:
 * <ul>
 * <li>Toda consulta debe filtrar por {@code idOrganizacion} (tenant).</li>
 * <li>Si el registro no pertenece al tenant, se considera como no encontrado.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Notas sobre zona horaria:
 * <ul>
 * <li>{@link Area} puede tener {@code timezoneId} como override opcional.</li>
 * <li>Si {@code timezoneId} es {@code null} (o blank), el área hereda la zona del tenant
 * (organización).</li>
 * </ul>
 * </p>
 */
@ApplicationScoped
public class AreaRepository extends BaseRepository<Area, UUID> {

  public AreaRepository() {
    super(Area.class);
  }

  /**
   * Busca un área por su identificador, asegurando que pertenezca a la organización indicada.
   *
   * @param areaId identificador del área (obligatorio)
   * @param orgId identificador de la organización/tenant (obligatorio)
   * @return {@link Optional} con el área si existe y pertenece al tenant; vacío en caso contrario
   */
  public Optional<Area> findByIdAndOrganizacion(UUID areaId, UUID orgId) {
    return em.createQuery("""
        select a
        from Area a
        where a.idArea = :areaId
          and a.idOrganizacion = :orgId
        """, Area.class).setParameter("areaId", areaId).setParameter("orgId", orgId)
        .getResultStream().findFirst();
  }

  /**
   * Lista las áreas de una organización con paginación.
   *
   * <p>
   * Orden determinístico por nombre ASC para evitar inconsistencias entre páginas.
   * </p>
   *
   * @param orgId tenant (obligatorio)
   * @param page página base 0
   * @param size tamaño de página
   * @return lista de áreas del tenant
   */
  public List<Area> listByOrganizacion(UUID orgId, int page, int size) {
    TypedQuery<Area> q = em.createQuery("""
        select a
        from Area a
        where a.idOrganizacion = :orgId
        order by a.nombre asc
        """, Area.class);

    return q.setParameter("orgId", orgId).setFirstResult(page * size).setMaxResults(size)
        .getResultList();
  }

  /**
   * Cuenta el total de áreas registradas para una organización.
   *
   * @param orgId tenant (obligatorio)
   * @return total de áreas del tenant
   */
  public long countByOrganizacion(UUID orgId) {
    return em.createQuery("""
        select count(a)
        from Area a
        where a.idOrganizacion = :orgId
        """, Long.class).setParameter("orgId", orgId).getSingleResult();
  }

  /**
   * Verifica si ya existe un área con el mismo nombre dentro de una organización.
   *
   * @param orgId tenant (obligatorio)
   * @param nombre nombre normalizado (obligatorio)
   * @return {@code true} si ya existe un área con ese nombre en el tenant
   */
  public boolean existsByNombre(UUID orgId, String nombre) {
    Long count = em.createQuery("""
        select count(a)
        from Area a
        where a.idOrganizacion = :orgId
          and a.nombre = :nombre
        """, Long.class).setParameter("orgId", orgId).setParameter("nombre", nombre)
        .getSingleResult();

    return count > 0;
  }

  /**
   * Verifica unicidad de nombre excluyendo un área específica (útil en updates).
   *
   * @param orgId tenant (obligatorio)
   * @param nombre nombre normalizado (obligatorio)
   * @param areaId id de área a excluir (obligatorio)
   * @return {@code true} si existe otra área con el mismo nombre en el tenant
   */
  public boolean existsByNombreExcludingId(UUID orgId, String nombre, UUID areaId) {
    Long count = em.createQuery("""
        select count(a)
        from Area a
        where a.idOrganizacion = :orgId
          and a.nombre = :nombre
          and a.idArea <> :areaId
        """, Long.class).setParameter("orgId", orgId).setParameter("nombre", nombre)
        .setParameter("areaId", areaId).getSingleResult();

    return count > 0;
  }

  /**
   * Obtiene el {@code timezoneId} (IANA) configurado en el área, si existe.
   *
   * <p>
   * Este valor es un override opcional. Si el área no tiene override, el valor será {@code null}.
   * </p>
   *
   * @param orgId tenant (obligatorio)
   * @param areaId id de área (obligatorio)
   * @return {@link Optional} con el timezone del área (puede ser {@code null} si no hay override);
   *         vacío si el área no existe o no pertenece al tenant
   */
  public Optional<String> findTimezoneId(UUID orgId, UUID areaId) {
    List<String> res = em.createQuery("""
        select a.timezoneId
        from Area a
        where a.idOrganizacion = :orgId
          and a.idArea = :areaId
        """, String.class).setParameter("orgId", orgId).setParameter("areaId", areaId)
        .getResultList();

    if (res.isEmpty()) {
      return Optional.empty();
    }
    // Puede venir null si el área existe pero no tiene override.
    return Optional.ofNullable(res.get(0));
  }

  /**
   * Variante conveniente para providers: retorna {@code null} si:
   * <ul>
   * <li>el área no existe / no pertenece al tenant</li>
   * <li>o el área existe pero no tiene override</li>
   * </ul>
   *
   * @param orgId tenant (obligatorio)
   * @param areaId id de área (obligatorio)
   * @return timezoneId del área o {@code null}
   */
  public String findTimezoneIdOrNull(UUID orgId, UUID areaId) {
    return findTimezoneId(orgId, areaId).orElse(null);
  }
}
