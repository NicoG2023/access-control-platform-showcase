package com.haedcom.access.health;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import com.haedcom.access.domain.enums.OutboxStatus;
import com.haedcom.access.domain.repo.OutboxEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Readiness
@ApplicationScoped
public class OutboxReadinessCheck implements HealthCheck {

    /**
     * Si hay READY, con tick=2s y batch=50, un READY debería salir rápido. 60-120s ya indica atasco
     * (Kafka caído, credenciales mal, sender roto, etc.).
     */
    private static final long MAX_OLDEST_READY_AGE_SEC = 120;

    /**
     * Con tu processor limpiando locks en finally, inflight viejo es anormal. TTL + gracia para
     * evitar falsos positivos.
     */
    private static final long INFLIGHT_GRACE_SEC = 30;

    private static final long MAX_FAILED = 50;

    @Inject
    OutboxEventRepository outboxRepo;

    @ConfigProperty(name = "haedcom.outbox.lock-ttl-seconds", defaultValue = "300")
    long lockTtlSeconds;

    @Override
    public HealthCheckResponse call() {
        try {
            long pending = outboxRepo.countByStatus(OutboxStatus.PENDING);
            long published = outboxRepo.countByStatus(OutboxStatus.PUBLISHED);
            long failed = outboxRepo.countByStatus(OutboxStatus.FAILED);

            long ready = outboxRepo.countReadyPending();
            long inflight = outboxRepo.countInflightPending();

            Long oldestReadyAgeSec = outboxRepo.oldestReadyPendingAgeSeconds(); // 👈 nuevo
            Long oldestInflightAgeSec = outboxRepo.oldestInflightAgeSeconds();

            boolean tooManyFailed = failed >= MAX_FAILED;

            boolean readyBacklogStuck = (ready > 0) && (oldestReadyAgeSec != null)
                    && (oldestReadyAgeSec > MAX_OLDEST_READY_AGE_SEC);

            boolean inflightStuck = (inflight > 0) && (oldestInflightAgeSec != null)
                    && (oldestInflightAgeSec > (lockTtlSeconds + INFLIGHT_GRACE_SEC));

            HealthCheckResponseBuilder b =
                    HealthCheckResponse.named("outbox-ready").withData("pending", pending)
                            .withData("ready", ready).withData("inflight", inflight)
                            .withData("failed", failed).withData("published", published)
                            .withData("oldestReadyAgeSec", oldestReadyAgeSec)
                            .withData("oldestInflightAgeSec", oldestInflightAgeSec)
                            .withData("thresholdOldestReadyAgeSec", MAX_OLDEST_READY_AGE_SEC)
                            .withData("lockTtlSeconds", lockTtlSeconds)
                            .withData("inflightGraceSec", INFLIGHT_GRACE_SEC)
                            .withData("thresholdFailed", MAX_FAILED);

            if (tooManyFailed || readyBacklogStuck || inflightStuck) {
                return b.down().build();
            }
            return b.up().build();

        } catch (Exception e) {
            return HealthCheckResponse.named("outbox-ready").down()
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
