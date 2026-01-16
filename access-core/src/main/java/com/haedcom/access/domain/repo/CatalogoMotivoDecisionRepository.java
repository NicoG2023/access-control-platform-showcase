package com.haedcom.access.domain.repo;

import java.util.List;
import java.util.Optional;
import com.haedcom.access.domain.model.CatalogoMotivoDecision;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio para el catálogo de motivos de decisión de acceso.
 *
 * <p>
 * Este catálogo define los motivos normalizados utilizados al registrar una
 * {@link com.haedcom.access.domain.model.DecisionAcceso}.
 * </p>
 *
 * <p>
 * Características:
 * <ul>
 * <li>No es multi-tenant.</li>
 * <li>Usa una clave natural ({@code codigoMotivo}) como identificador.</li>
 * <li>Normalmente se gestiona por migraciones o carga inicial de datos.</li>
 * </ul>
 * </p>
 */
@ApplicationScoped
public class CatalogoMotivoDecisionRepository
        extends BaseRepository<CatalogoMotivoDecision, String> {

    public CatalogoMotivoDecisionRepository() {
        super(CatalogoMotivoDecision.class);
    }

    /**
     * Busca un motivo de decisión por su código.
     *
     * @param codigoMotivo código del motivo
     * @return motivo si existe
     */
    public Optional<CatalogoMotivoDecision> findByCodigo(String codigoMotivo) {
        return findById(codigoMotivo);
    }

    /**
     * Lista todos los motivos disponibles.
     *
     * @return lista completa del catálogo
     */
    public List<CatalogoMotivoDecision> listAll() {
        return em.createQuery("from CatalogoMotivoDecision order by codigoMotivo",
                CatalogoMotivoDecision.class).getResultList();
    }
}
