package com.haedcom.access.health;

import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Readiness
@ApplicationScoped
public class DatabaseReadinessCheck implements HealthCheck {

    @Inject
    DataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT 1")) {

            ps.execute();

            return HealthCheckResponse.up("db-ready");

        } catch (Exception e) {
            return HealthCheckResponse.named("db-ready").down()
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
