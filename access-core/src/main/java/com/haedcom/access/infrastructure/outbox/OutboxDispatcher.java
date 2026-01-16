package com.haedcom.access.infrastructure.outbox;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import com.haedcom.access.domain.model.OutboxEvent;
import com.haedcom.access.domain.repo.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Dispatcher configurable del patrón <b>Transactional Outbox</b>.
 *
 * <h2>Configuración</h2>
 * <ul>
 * <li>{@code haedcom.outbox.dispatch.every}</li>
 * <li>{@code haedcom.outbox.dispatch.delayed}</li>
 * <li>{@code haedcom.outbox.maintenance.every}</li>
 * <li>{@code haedcom.outbox.maintenance.delayed}</li>
 * <li>{@code haedcom.outbox.lock-ttl-seconds}</li>
 * </ul>
 *
 * <h2>Jobs</h2>
 * <ol>
 * <li><b>Dispatcher</b>: reclama y procesa eventos READY.</li>
 * <li><b>Mantenimiento</b>: limpia locks lógicos vencidos (inflight fantasma).</li>
 * </ol>
 */
@ApplicationScoped
public class OutboxDispatcher {

    private static final Logger LOG = Logger.getLogger(OutboxDispatcher.class);

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final OutboxEventRepository outboxRepo;
    private final OutboxEventProcessor processor;
    private final Clock clock;

    private final String instanceId;
    private final long lockTtlSeconds;

    // Métricas
    private final Counter runs;
    private final Counter emptyRuns;
    private final Counter claimed;
    private final Counter expiredLocksReleased;
    private final Timer dispatchTimer;

    @Inject
    public OutboxDispatcher(OutboxEventRepository outboxRepo, OutboxEventProcessor processor,
            Clock clock, MeterRegistry registry, InstanceIdProvider instanceIdProvider,
            @ConfigProperty(name = "haedcom.outbox.lock-ttl-seconds",
                    defaultValue = "300") long lockTtlSeconds) {

        this.outboxRepo = Objects.requireNonNull(outboxRepo);
        this.processor = Objects.requireNonNull(processor);
        this.clock = clock != null ? clock : Clock.systemUTC();

        this.instanceId = Objects
                .requireNonNull(instanceIdProvider, "instanceIdProvider es obligatorio").get();

        this.lockTtlSeconds = lockTtlSeconds;

        this.runs = Counter.builder("outbox_dispatch_runs_total").register(registry);
        this.emptyRuns = Counter.builder("outbox_dispatch_empty_runs_total").register(registry);
        this.claimed = Counter.builder("outbox_events_claimed_total").register(registry);
        this.expiredLocksReleased =
                Counter.builder("outbox_expired_locks_released_total").register(registry);

        this.dispatchTimer = Timer.builder("outbox_dispatch_duration_seconds").register(registry);

        // ===== Gauges PRO =====
        Gauge.builder("outbox_pending_ready", outboxRepo, r -> (double) r.countReadyPending())
                .register(registry);

        Gauge.builder("outbox_pending_inflight", outboxRepo, r -> (double) r.countInflightPending())
                .register(registry);

        Gauge.builder("outbox_oldest_pending_age_seconds", outboxRepo,
                r -> r.oldestPendingAgeSeconds() == null ? 0 : r.oldestPendingAgeSeconds())
                .register(registry);

        Gauge.builder("outbox_oldest_inflight_age_seconds", outboxRepo,
                r -> r.oldestInflightAgeSeconds() == null ? 0 : r.oldestInflightAgeSeconds())
                .register(registry);
    }

    /**
     * Job principal del dispatcher (configurable).
     */
    @Scheduled(every = "{haedcom.outbox.dispatch.every:2s}",
            delayed = "{haedcom.outbox.dispatch.delayed:3s}",
            concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        dispatchPending();
    }

    /**
     * Job de mantenimiento configurable para liberar locks lógicos vencidos.
     */
    @Scheduled(every = "{haedcom.outbox.maintenance.every:5m}",
            delayed = "{haedcom.outbox.maintenance.delayed:30s}",
            concurrentExecution = ConcurrentExecution.SKIP)
    void maintenanceReleaseExpiredLocks() {
        if (lockTtlSeconds <= 0) {
            return;
        }

        try {
            int released = releaseExpiredLocksTx(lockTtlSeconds);
            if (released > 0) {
                expiredLocksReleased.increment(released);

                if (released >= 100) {
                    LOG.warnf("Outbox maintenance released MANY expired locks=%d ttl=%ds", released,
                            lockTtlSeconds);
                } else {
                    LOG.debugf("Outbox maintenance released expired locks=%d ttl=%ds", released,
                            lockTtlSeconds);
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Outbox maintenance failed ttl=%ds", lockTtlSeconds);
        }
    }

    /**
     * Ejecuta una corrida completa del dispatcher.
     */
    public void dispatchPending() {
        runs.increment();

        dispatchTimer.record(() -> {
            List<UUID> ids = claimBatchIds(DEFAULT_BATCH_SIZE);

            if (ids.isEmpty()) {
                emptyRuns.increment();
                return;
            }

            claimed.increment(ids.size());

            if (ids.size() >= DEFAULT_BATCH_SIZE) {
                LOG.debugf("OutboxDispatcher - batch lleno claimed=%d instanceId=%s ttl=%d",
                        Integer.valueOf(ids.size()), instanceId, Long.valueOf(lockTtlSeconds));
            } else {
                LOG.debugf("OutboxDispatcher - claimed=%d instanceId=%s ttl=%d",
                        Integer.valueOf(ids.size()), instanceId, Long.valueOf(lockTtlSeconds));
            }

            for (UUID id : ids) {
                processor.processInNewTx(id);
            }
        });
    }

    /**
     * Claim transaccional corto que retorna solo IDs.
     */
    @Transactional
    protected List<UUID> claimBatchIds(int limit) {
        List<OutboxEvent> batch =
                outboxRepo.findPendingReadyForUpdateSkipLocked(limit, lockTtlSeconds);

        if (batch.isEmpty()) {
            return List.of();
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<UUID> ids = new ArrayList<>(batch.size());

        boolean markDiagnosticLock = lockTtlSeconds > 0;

        for (OutboxEvent e : batch) {
            if (markDiagnosticLock) {
                e.setLockedAtUtc(now);
                e.setLockedBy(instanceId);
            }
            ids.add(e.getIdEvento());
        }

        outboxRepo.flush();
        return ids;
    }


    /**
     * Libera locks lógicos vencidos (TX corta).
     */
    @Transactional
    protected int releaseExpiredLocksTx(long ttlSeconds) {
        return outboxRepo.releaseExpiredLocks(ttlSeconds);
    }
}
