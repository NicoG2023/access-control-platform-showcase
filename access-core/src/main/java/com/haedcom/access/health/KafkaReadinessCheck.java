package com.haedcom.access.health;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import jakarta.enterprise.context.ApplicationScoped;

@Readiness
@ApplicationScoped
public class KafkaReadinessCheck implements HealthCheck {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    // Pequeño: readiness no debe colgar
    private static final int TIMEOUT_MS = 1500;

    @Override
    public HealthCheckResponse call() {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            return HealthCheckResponse.named("kafka-ready").down()
                    .withData("error", "MissingConfig")
                    .withData("message", "kafka.bootstrap.servers is not set").build();
        }

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("request.timeout.ms", String.valueOf(TIMEOUT_MS));
        props.put("default.api.timeout.ms", String.valueOf(TIMEOUT_MS));

        try (AdminClient admin = AdminClient.create(props)) {
            var cluster = admin.describeCluster();

            String clusterId = cluster.clusterId().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            int nodes = cluster.nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).size();

            return HealthCheckResponse.named("kafka-ready").up().withData("clusterId", clusterId)
                    .withData("nodes", nodes).withData("bootstrapServers", bootstrapServers)
                    .build();

        } catch (Exception e) {
            return HealthCheckResponse.named("kafka-ready").down()
                    .withData("bootstrapServers", bootstrapServers)
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
