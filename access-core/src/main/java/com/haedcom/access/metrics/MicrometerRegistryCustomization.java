package com.haedcom.access.metrics;

import java.util.Arrays;
import org.jboss.logging.Logger;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.quarkus.micrometer.runtime.MeterRegistryCustomizer;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MicrometerRegistryCustomization implements MeterRegistryCustomizer {

    private static final Logger LOG = Logger.getLogger(MicrometerRegistryCustomization.class);

    @Override
    public void customize(MeterRegistry registry) {
        LOG.warnf("[MICROMETER][CUSTOMIZER] customizing registry=%s",
                registry.getClass().getName());

        // 1) Sanitizer GLOBAL: elimina SLOs inválidos (<= 0) para cualquier meter
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(io.micrometer.core.instrument.Meter.Id id,
                    DistributionStatisticConfig config) {
                double[] slos = config.getServiceLevelObjectiveBoundaries();
                if (slos == null || slos.length == 0)
                    return config;

                boolean hasInvalid = Arrays.stream(slos).anyMatch(v -> v <= 0.0);
                if (!hasInvalid)
                    return config;

                double[] sanitized =
                        Arrays.stream(slos).filter(v -> v > 0.0).distinct().sorted().toArray();

                LOG.warnf("[MICROMETER][SANITIZER] Meter=%s invalid SLOs=%s -> sanitized=%s",
                        id.getName(), Arrays.toString(slos), Arrays.toString(sanitized));

                if (sanitized.length == 0) {
                    // elimina SLOs por completo
                    return DistributionStatisticConfig.builder().build().merge(config);
                }

                return DistributionStatisticConfig.builder().serviceLevelObjectives(sanitized)
                        .build().merge(config);
            }
        });

        // 2) Config específica de http.server.requests (RED)
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(io.micrometer.core.instrument.Meter.Id id,
                    DistributionStatisticConfig config) {
                if (!"http.server.requests".equals(id.getName()))
                    return config;

                LOG.warn(
                        "[MICROMETER][http.server.requests] enabling percentilesHistogram=true (NO SLOs)");

                DistributionStatisticConfig ours =
                        DistributionStatisticConfig.builder().percentilesHistogram(true).build();

                return ours.merge(config);
            }
        });

    }
}
