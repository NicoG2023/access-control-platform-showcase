package com.haedcom.access.domain.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.haedcom.access.domain.model.ComandoDispositivo;
import jakarta.enterprise.context.ApplicationScoped;


/**
 * Repositorio para {@link ComandoDispositivo}.
 *
 * <p>
 * Soporta idempotencia por tenant al emitir comandos hacia dispositivos.
 * </p>
 */
@ApplicationScoped
public class ComandoDispositivoRepository extends BaseRepository<ComandoDispositivo, UUID> {

  public ComandoDispositivoRepository() {
    super(ComandoDispositivo.class);
  }

  /**
   * Busca un comando por clave de idempotencia dentro del tenant.
   *
   * @param orgId id del tenant
   * @param claveIdempotencia clave idempotente
   * @return comando existente si ya fue creado
   */
  public Optional<ComandoDispositivo> findByIdempotencia(UUID orgId, String claveIdempotencia) {
    return em.createQuery("""
        select c
        from ComandoDispositivo c
        where c.idOrganizacion = :orgId
          and c.claveIdempotencia = :key
        """, ComandoDispositivo.class).setParameter("orgId", orgId)
        .setParameter("key", claveIdempotencia).getResultStream().findFirst();
  }

  /**
   * Lista comandos por intento dentro del tenant.
   *
   * @param orgId id del tenant
   * @param idIntento id del intento
   * @return comandos emitidos para ese intento
   */
  public List<ComandoDispositivo> listByIntento(UUID orgId, UUID idIntento) {
    return em.createQuery("""
        select c
        from ComandoDispositivo c
        where c.idOrganizacion = :orgId
          and c.intento.idIntento = :idIntento
        order by c.enviadoEnUtc asc
        """, ComandoDispositivo.class).setParameter("orgId", orgId)
        .setParameter("idIntento", idIntento).getResultList();
  }

  /**
   * Busca un comando por id dentro del tenant.
   *
   * @param orgId id del tenant
   * @param idComando id del comando
   * @return comando si existe en el tenant
   */
  public Optional<ComandoDispositivo> findByIdAndOrg(UUID orgId, UUID idComando) {
    return em.createQuery("""
        select c
        from ComandoDispositivo c
        where c.idOrganizacion = :orgId
          and c.idComando = :idComando
        """, ComandoDispositivo.class).setParameter("orgId", orgId)
        .setParameter("idComando", idComando).getResultStream().findFirst();
  }

  public Optional<ComandoDispositivo> findByIdAndOrgWithIntento(UUID orgId, UUID idComando) {
    return em.createQuery("""
        select c
        from ComandoDispositivo c
        join fetch c.intento i
        where c.idOrganizacion = :orgId
          and c.idComando = :idComando
        """, ComandoDispositivo.class).setParameter("orgId", orgId)
        .setParameter("idComando", idComando).getResultStream().findFirst();
  }

}
