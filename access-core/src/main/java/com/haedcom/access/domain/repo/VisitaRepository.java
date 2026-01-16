package com.haedcom.access.domain.repo;

import java.util.Optional;
import java.util.UUID;
import com.haedcom.access.domain.model.Visita;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio para {@link Visita}.
 *
 * <p>
 * Para el flujo de acceso, normalmente se requiere verificar si una visita está activa y pertenece
 * al tenant.
 * </p>
 */
@ApplicationScoped
public class VisitaRepository extends BaseRepository<Visita, UUID> {

    public VisitaRepository() {
        super(Visita.class);
    }

    /**
     * Busca una visita por id dentro del tenant.
     *
     * @param idVisita id de la visita
     * @param orgId id del tenant
     * @return visita si existe y pertenece al tenant
     */
    public Optional<Visita> findByIdAndOrganizacion(UUID idVisita, UUID orgId) {
        return em.createQuery("""
                select v
                from Visita v
                where v.idVisita = :id
                  and v.idOrganizacion = :orgId
                """, Visita.class).setParameter("id", idVisita).setParameter("orgId", orgId)
                .getResultStream().findFirst();
    }
}
