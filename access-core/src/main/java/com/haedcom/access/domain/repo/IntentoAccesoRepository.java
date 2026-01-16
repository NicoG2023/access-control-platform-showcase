package com.haedcom.access.domain.repo;

import java.util.Optional;
import java.util.UUID;
import com.haedcom.access.domain.model.IntentoAcceso;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio para {@link IntentoAcceso}.
 *
 * <p>
 * Soporta consultas por idempotencia a nivel tenant para implementar "at-most-once" en el endpoint
 * de registro de intentos.
 * </p>
 */
@ApplicationScoped
public class IntentoAccesoRepository extends BaseRepository<IntentoAcceso, UUID> {

  public IntentoAccesoRepository() {
    super(IntentoAcceso.class);
  }

  /**
   * Busca un intento por clave de idempotencia dentro del tenant.
   *
   * @param orgId id de la organización (tenant)
   * @param claveIdempotencia clave idempotente enviada por el gateway/dispositivo
   * @return intento si existe para ese tenant y clave
   */
  public Optional<IntentoAcceso> findByIdempotencia(UUID orgId, String claveIdempotencia) {
    return em.createQuery("""
        select i
        from IntentoAcceso i
        where i.idOrganizacion = :orgId
          and i.claveIdempotencia = :key
        """, IntentoAcceso.class).setParameter("orgId", orgId)
        .setParameter("key", claveIdempotencia).getResultStream().findFirst();
  }

  /**
   * Busca un intento por id dentro del tenant.
   *
   * @param idIntento id del intento
   * @param orgId id del tenant
   * @return intento si existe y pertenece al tenant
   */
  public Optional<IntentoAcceso> findByIdAndOrganizacion(UUID idIntento, UUID orgId) {
    return em.createQuery("""
        select i
        from IntentoAcceso i
        where i.idIntento = :id
          and i.idOrganizacion = :orgId
        """, IntentoAcceso.class).setParameter("id", idIntento).setParameter("orgId", orgId)
        .getResultStream().findFirst();
  }
}
