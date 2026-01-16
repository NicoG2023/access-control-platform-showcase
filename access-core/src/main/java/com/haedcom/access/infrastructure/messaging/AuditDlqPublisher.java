package com.haedcom.access.infrastructure.messaging;

import java.util.Objects;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Publica errores de consumo en una Dead Letter Queue (DLQ) en Kafka.
 *
 * <p>
 * Best-effort: si DLQ falla, solo loggea (no debe tumbar el consumer).
 * </p>
 */
@ApplicationScoped
public class AuditDlqPublisher {

    private static final Logger LOG = Logger.getLogger(AuditDlqPublisher.class);

    private final MutinyEmitter<Record<String, String>> emitter;
    private final ObjectMapper objectMapper;

    public AuditDlqPublisher(@Channel("audit-dlq") MutinyEmitter<Record<String, String>> emitter,
            ObjectMapper objectMapper) {
        this.emitter = Objects.requireNonNull(emitter, "emitter es obligatorio");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper es obligatorio");
    }

    public void publish(AuditDlqMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            // key por tipo (o puedes usar orgId si lo logras inferir)
            Uni<Void> uni = emitter.send(Record.of("audit", json));
            uni.await().indefinitely();
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "audit_dlq_serialize_failed");
        } catch (Exception e) {
            LOG.errorf(e, "audit_dlq_publish_failed");
        }
    }
}
