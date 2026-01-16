package com.haedcom.access.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import com.haedcom.access.domain.repo.CatalogoMotivoDecisionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Readiness
@ApplicationScoped
public class CatalogReadinessCheck implements HealthCheck {

    @Inject
    CatalogoMotivoDecisionRepository motivoRepo;

    @Override
    public HealthCheckResponse call() {
        try {
            boolean ok = motivoRepo.findByCodigo("POLICY_ERROR").isPresent();

            if (ok) {
                return HealthCheckResponse.up("catalog-motivos-ready");
            }

            return HealthCheckResponse.named("catalog-motivos-ready").down()
                    .withData("missing", "POLICY_ERROR").build();

        } catch (Exception e) {
            return HealthCheckResponse.named("catalog-motivos-ready").down()
                    .withData("error", e.getClass().getSimpleName())
                    .withData("message", safe(e.getMessage())).build();
        }
    }

    private String safe(String s) {
        if (s == null)
            return null;
        return s.length() <= 160 ? s : s.substring(0, 160);
    }
}
