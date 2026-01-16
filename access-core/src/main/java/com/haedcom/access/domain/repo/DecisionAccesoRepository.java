package com.haedcom.access.domain.repo;

import java.util.Optional;
import java.util.UUID;
import com.haedcom.access.domain.model.DecisionAcceso;
import com.haedcom.access.domain.model.IntentoAcceso;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio para {@link DecisionAcceso}.
 *
 * <p>
 * Maneja la unicidad 1-1 entre {@link DecisionAcceso} e {@link IntentoAcceso}.
 * </p>
 */
@ApplicationScoped
public class DecisionAccesoRepository extends BaseRepository<DecisionAcceso, UUID> {

    public DecisionAccesoRepository() {
        super(DecisionAcceso.class);
    }

    /**
     * Busca la decisión asociada a un intento.
     *
     * @param orgId id del tenant
     * @param idIntento id del intento
     * @return decisión si existe
     */
    public Optional<DecisionAcceso> findByIntento(UUID orgId, UUID idIntento) {
        return em.createQuery("""
                select d
                from DecisionAcceso d
                where d.idOrganizacion = :orgId
                  and d.intento.idIntento = :idIntento
                """, DecisionAcceso.class).setParameter("orgId", orgId)
                .setParameter("idIntento", idIntento).getResultStream().findFirst();
    }
}
