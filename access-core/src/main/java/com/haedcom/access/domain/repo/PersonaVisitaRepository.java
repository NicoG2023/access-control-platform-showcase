package com.haedcom.access.domain.repo;

import java.util.Optional;
import java.util.UUID;
import com.haedcom.access.domain.model.PersonaVisita;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio para {@link PersonaVisita}.
 *
 * <p>
 * Para control de acceso, permite traer la persona asociada a una visita y garantizar
 * tenant-isolation.
 * </p>
 */
@ApplicationScoped
public class PersonaVisitaRepository extends BaseRepository<PersonaVisita, UUID> {

    public PersonaVisitaRepository() {
        super(PersonaVisita.class);
    }

    /**
     * Busca una persona de visita por id dentro del tenant.
     *
     * @param idPersonaVisita id de persona
     * @param orgId id del tenant
     * @return persona si existe y pertenece al tenant
     */
    public Optional<PersonaVisita> findByIdAndOrganizacion(UUID idPersonaVisita, UUID orgId) {
        return em.createQuery("""
                select p
                from PersonaVisita p
                where p.idPersonaVisita = :id
                  and p.idOrganizacion = :orgId
                """, PersonaVisita.class).setParameter("id", idPersonaVisita)
                .setParameter("orgId", orgId).getResultStream().findFirst();
    }
}
