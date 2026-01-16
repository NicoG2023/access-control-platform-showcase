package com.haedcom.access.application.acceso.decision;

import java.util.List;
import java.util.UUID;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import com.haedcom.access.domain.model.ReglaAcceso;
import com.haedcom.access.domain.repo.ReglaAccesoRepository;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReglaAccesoCandidatesProvider {

    private final ReglaAccesoRepository repo;

    public ReglaAccesoCandidatesProvider(ReglaAccesoRepository repo) {
        this.repo = repo;
    }

    /**
     * Cachea reglas activas base por (orgId, areaId, tipoSujeto). OJO: aquí NO entra nowUtc, ni
     * device/direction/method.
     */
    @CacheResult(cacheName = "regla-candidates")
    public List<ReglaAcceso> activeRulesBase(UUID orgId, UUID areaId, TipoSujetoAcceso tipoSujeto) {
        return repo.findActiveRulesBase(orgId, areaId, tipoSujeto);
    }
}
