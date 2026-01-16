package com.haedcom.access.infrastructure.outbox;

import com.haedcom.access.domain.enums.OutboxStatus;
import com.haedcom.access.domain.repo.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Registra métricas del Outbox (Micrometer).
 *
 * <p>
 * Expone gauges para monitorear:
 * </p>
 * <ul>
 * <li><b>PENDING</b>: backlog total de eventos pendientes</li>
 * <li><b>READY</b>: pendientes listos para ser despachados (sin backoff o backoff vencido)</li>
 * <li><b>INFLIGHT</b>: pendientes reclamados (claimed) por alguna instancia (lockedAtUtc !=
 * null)</li>
 * <li><b>FAILED</b>: eventos que requieren intervención</li>
 * <li><b>Lag</b>: edad del PENDING más viejo</li>
 * <li><b>Oldest inflight</b>: edad del inflight más viejo (señal de atasco)</li>
 * </ul>
 *
 * <p>
 * Nota: este bean solo registra meters al iniciar la app.
 * </p>
 */
@ApplicationScoped
public class OutboxMetrics {

    @Inject
    public OutboxMetrics(OutboxEventRepository repo, MeterRegistry registry) {

        Gauge.builder("outbox_pending", repo, r -> r.countByStatus(OutboxStatus.PENDING))
                .description("Cantidad de eventos PENDING en outbox").register(registry);

        Gauge.builder("outbox_ready", repo, r -> r.countReadyPending()).description(
                "Cantidad de eventos PENDING listos para despachar (nextAttemptAtUtc null o vencido)")
                .register(registry);

        Gauge.builder("outbox_inflight", repo, r -> r.countInflightPending())
                .description("Cantidad de eventos PENDING reclamados (lockedAtUtc != null)")
                .register(registry);

        Gauge.builder("outbox_failed", repo, r -> r.countByStatus(OutboxStatus.FAILED))
                .description("Cantidad de eventos FAILED en outbox").register(registry);

        Gauge.builder("outbox_oldest_pending_age_seconds", repo, r -> {
            Long v = r.oldestPendingAgeSeconds();
            return v != null ? v.doubleValue() : 0.0;
        }).description("Lag del outbox: edad del PENDING más viejo (segundos)").register(registry);

        Gauge.builder("outbox_oldest_inflight_age_seconds", repo, r -> {
            Long v = r.oldestInflightAgeSeconds();
            return v != null ? v.doubleValue() : 0.0;
        }).description(
                "Edad del inflight más viejo (segundos). Señal de atasco o transacciones largas.")
                .register(registry);
    }
}
