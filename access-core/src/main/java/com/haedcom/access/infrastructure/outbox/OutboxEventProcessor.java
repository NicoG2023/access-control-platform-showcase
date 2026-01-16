package com.haedcom.access.infrastructure.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import com.haedcom.access.domain.enums.OutboxStatus;
import com.haedcom.access.domain.events.OutboxEventSender;
import com.haedcom.access.domain.events.OutboxSendException;
import com.haedcom.access.domain.model.OutboxEvent;
import com.haedcom.access.domain.repo.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Procesador de eventos de Outbox, ejecutado <b>por evento</b> en transacción {@code REQUIRES_NEW}.
 *
 * <h2>Responsabilidad</h2>
 * <ul>
 * <li>Recargar el evento por ID</li>
 * <li>Enviar el evento usando {@link OutboxEventSender}</li>
 * <li>Actualizar estado (PUBLISHED/FAILED) o programar reintento</li>
 * <li>Registrar metadata de error y aplicar retry inteligente</li>
 * </ul>
 *
 * <h2>Política de reintentos</h2>
 * <p>
 * - Se reintenta solo si {@link OutboxSendException#isRetryable()} es true.<br>
 * - Se respeta {@code retryAfter} del transporte si existe (con cap).<br>
 * - Si no hay {@code retryAfter}, se usa backoff propio con jitter.
 * </p>
 *
 * <h2>Bug-fix importante</h2>
 * <p>
 * Este procesador incrementa {@code attempts} <b>en cualquier fallo</b>, incluso si el fallo es
 * no-retryable. Esto es clave para trazabilidad real en producción.
 * </p>
 */
@ApplicationScoped
public class OutboxEventProcessor {

    private static final Logger LOG = Logger.getLogger(OutboxEventProcessor.class);

    /** Máximo de intentos permitidos antes de marcar FAILED. */
    private static final int MAX_ATTEMPTS = 5;

    /** Cap de retry-after para no “congelar” eventos por ventanas excesivas. */
    private static final Duration MAX_RETRY_AFTER = Duration.ofMinutes(10);

    private final OutboxEventRepository outboxRepo;
    private final OutboxEventSender sender;
    private final Clock clock;

    private final String instanceId;
    private final long lockTtlSeconds;

    // Métricas
    private final Counter published;
    private final Counter failed;
    private final Counter retried;

    @Inject
    public OutboxEventProcessor(OutboxEventRepository outboxRepo, OutboxEventSender sender,
            Clock clock, MeterRegistry registry, InstanceIdProvider instanceIdProvider,
            @ConfigProperty(name = "haedcom.outbox.lock-ttl-seconds",
                    defaultValue = "300") long lockTtlSeconds) {

        this.outboxRepo = Objects.requireNonNull(outboxRepo, "outboxRepo es obligatorio");
        this.sender = Objects.requireNonNull(sender, "sender es obligatorio");
        this.clock = (clock != null) ? clock : Clock.systemUTC();
        this.instanceId = Objects
                .requireNonNull(instanceIdProvider, "instanceIdProvider es obligatorio").get();
        this.lockTtlSeconds = lockTtlSeconds;

        this.published = Counter.builder("outbox_events_published_total")
                .description("Número de eventos de outbox publicados exitosamente")
                .register(registry);

        this.failed = Counter.builder("outbox_events_failed_total")
                .description("Número de eventos de outbox marcados como FAILED").register(registry);

        this.retried = Counter.builder("outbox_events_retried_total")
                .description("Número de eventos de outbox para los cuales se programó reintento")
                .register(registry);
    }

    /**
     * Procesa un evento del outbox en una nueva transacción.
     *
     * <p>
     * Mejora clave: reclama ownership del evento (lock lógico) en DB de forma atómica antes de
     * invocar el envío. Si no logra reclamar, asume que otro nodo lo está procesando.
     * </p>
     *
     * @param idEvento identificador del evento outbox
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void processInNewTx(UUID idEvento) {
        OutboxEvent event = outboxRepo.findById(idEvento).orElse(null);
        if (event == null) {
            return;
        }

        if (event.getStatus() != OutboxStatus.PENDING) {
            return;
        }

        // Claim atómico por id (evita doble envío)
        OffsetDateTime now = OffsetDateTime.now(clock);

        // Si TTL <= 0, solo permitimos claim si lock está libre o ya es nuestro.
        long ttl = Math.max(0, lockTtlSeconds);
        OffsetDateTime expiredBefore = (ttl > 0) ? now.minusSeconds(ttl) : now;

        int claimed = outboxRepo.claimForProcessing(idEvento, instanceId, now, expiredBefore);
        if (claimed == 0) {
            return;
        }

        try {
            sender.send(event);

            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAtUtc(now);
            event.setNextAttemptAtUtc(null);

            clearLastError(event);

            published.increment();
            LOG.debugf("OutboxEventProcessor - publicado idEvento=%s type=%s aggregateId=%s",
                    event.getIdEvento(), event.getEventType(), event.getAggregateId());

        } catch (OutboxSendException ex) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);

            stampError(event, ex);

            if (!ex.isRetryable() || attempts >= MAX_ATTEMPTS) {
                event.setStatus(OutboxStatus.FAILED);
                event.setNextAttemptAtUtc(null);

                failed.increment();
                LOG.errorf(ex,
                        "OutboxEventProcessor - FAILED idEvento=%s type=%s aggregateId=%s attempts=%d failure=%s code=%s http=%s",
                        event.getIdEvento(), event.getEventType(), event.getAggregateId(), attempts,
                        safeFailureType(ex), ex.getErrorCode(), ex.getHttpStatus());
                return;
            }

            event.setNextAttemptAtUtc(computeNextAttempt(attempts, ex.getRetryAfter()));
            retried.increment();

            LOG.warnf(ex,
                    "OutboxEventProcessor - reintento %d programado para %s idEvento=%s type=%s aggregateId=%s failure=%s code=%s http=%s",
                    attempts, event.getNextAttemptAtUtc(), event.getIdEvento(),
                    event.getEventType(), event.getAggregateId(), safeFailureType(ex),
                    ex.getErrorCode(), ex.getHttpStatus());


        } finally {
            // Limpieza safe por ownership
            outboxRepo.clearLockIfOwned(idEvento, instanceId);
        }
    }

    /**
     * Calcula el siguiente intento en UTC.
     *
     * <p>
     * Si {@code retryAfter} viene del transporte, se respeta con cap. Si no viene, se usa backoff
     * propio con jitter.
     * </p>
     *
     * @param attempts número de intentos ya ejecutados (incluyendo el que falló)
     * @param retryAfter ventana sugerida de reintento (opcional)
     * @return timestamp UTC del próximo intento
     */
    private OffsetDateTime computeNextAttempt(int attempts, Duration retryAfter) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero()) {
            Duration capped =
                    retryAfter.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : retryAfter;
            return now.plus(capped);
        }

        return computeNextAttemptWithJitter(attempts);
    }

    /**
     * Backoff propio (cap) con jitter simple.
     *
     * <p>
     * Política base (cap):
     * <ul>
     * <li>1 → 2s</li>
     * <li>2 → 10s</li>
     * <li>3 → 30s</li>
     * <li>4 → 2m</li>
     * <li>5+ → 5m</li>
     * </ul>
     * </p>
     *
     * <p>
     * Jitter: multiplica el delay por factor aleatorio [0.7, 1.3).
     * </p>
     */
    private OffsetDateTime computeNextAttemptWithJitter(int attempts) {
        int baseSeconds = switch (attempts) {
            case 1 -> 2;
            case 2 -> 10;
            case 3 -> 30;
            case 4 -> 120;
            default -> 300;
        };

        double factor = 0.7 + (Math.random() * 0.6); // [0.7, 1.3)
        long seconds = Math.max(1, Math.round(baseSeconds * factor));
        return OffsetDateTime.now(clock).plusSeconds(seconds);
    }

    private void stampError(OutboxEvent event, OutboxSendException ex) {
        event.setLastErrorAtUtc(OffsetDateTime.now(clock));
        event.setLastErrorCode(ex.getErrorCode());
        event.setLastHttpStatus(ex.getHttpStatus());

        String msg = ex.getMessage();
        if (msg != null && msg.length() > 600) {
            msg = msg.substring(0, 600);
        }
        event.setLastErrorMessage(msg);
    }

    private void clearLastError(OutboxEvent event) {
        event.setLastErrorCode(null);
        event.setLastErrorMessage(null);
        event.setLastErrorAtUtc(null);
        event.setLastHttpStatus(null);
    }

    private static String safeFailureType(OutboxSendException ex) {
        try {
            Object v = ex.getClass().getMethod("getFailureType").invoke(ex);
            return v != null ? v.toString() : "N/A";
        } catch (Exception ignored) {
            return "N/A";
        }
    }
}
