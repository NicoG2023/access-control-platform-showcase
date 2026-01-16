package com.haedcom.access.infrastructure.messaging;

import java.util.Objects;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haedcom.access.application.audit.AuditIngestService;
import com.haedcom.access.domain.events.ComandoDispositivoEjecutado;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reprocesador de DLQ (Dead Letter Queue) para auditoría.
 *
 * <h2>Objetivo</h2>
 * <p>
 * Dar una segunda oportunidad a mensajes que fallaron en el consumer principal de auditoría (por
 * ejemplo, por un fallo transitorio de base de datos o por una dependencia momentáneamente
 * indisponible).
 * </p>
 *
 * <h2>Entrada</h2>
 * <p>
 * Consume mensajes desde el tópico DLQ en formato {@link AuditDeadLetterMessage} (JSON). Ese DLQ
 * message contiene (entre otros) {@code originalPayload}, que corresponde al JSON original del
 * tópico principal (envelope {@link OutboxKafkaEnvelope}).
 * </p>
 *
 * <h2>Salida</h2>
 * <ul>
 * <li>Si reprocesa OK: hace ack y el mensaje se considera resuelto.</li>
 * <li>Si vuelve a fallar: lo envía a <b>Parking Lot</b> (tópico de “estacionamiento” para análisis
 * manual) y hace ack para no ciclar.</li>
 * </ul>
 *
 * <h2>Commit/ack</h2>
 * <p>
 * Se recomienda {@code enable.auto.commit=false} y confirmar offsets vía {@code ack()} al
 * finalizar.
 * </p>
 */
@ApplicationScoped
public class AuditDlqReprocessor {

    private static final Logger LOG = Logger.getLogger(AuditDlqReprocessor.class);

    private final ObjectMapper objectMapper;
    private final AuditIngestService ingest;
    private final AuditDeadLetterPublisher dlqPublisher;

    public AuditDlqReprocessor(ObjectMapper objectMapper, AuditIngestService ingest,
            AuditDeadLetterPublisher dlqPublisher) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper es obligatorio");
        this.ingest = Objects.requireNonNull(ingest, "ingest es obligatorio");
        this.dlqPublisher = Objects.requireNonNull(dlqPublisher, "dlqPublisher es obligatorio");
    }

    /**
     * Intenta reprocesar un mensaje de DLQ.
     *
     * <p>
     * Reglas:
     * </p>
     * <ul>
     * <li>Si el evento dentro del envelope no es {@code ComandoDispositivoEjecutado}, se ignora y
     * se hace ack.</li>
     * <li>Si falla, se envía a Parking Lot y se hace ack.</li>
     * </ul>
     */
    @Incoming("audit-dlq-retry")
    public Uni<Void> onDlq(Message<String> msg) {
        final String dlqJson = msg.getPayload();

        try {
            AuditDeadLetterMessage dlqMsg =
                    objectMapper.readValue(dlqJson, AuditDeadLetterMessage.class);

            // OJO: el DLQ message generado por el consumer principal debe tener originalEnvelope
            // lleno.
            // Si todavía no lo tiene (mientras migras), cae a fallback usando originalPayload.
            String originalEnvelopeJson =
                    (dlqMsg.originalEnvelope() != null && !dlqMsg.originalEnvelope().isBlank())
                            ? dlqMsg.originalEnvelope()
                            : dlqMsg.originalPayload();

            OutboxKafkaEnvelope env =
                    objectMapper.readValue(originalEnvelopeJson, OutboxKafkaEnvelope.class);

            if (!ComandoDispositivoEjecutado.class.getSimpleName().equals(env.eventType())) {
                return ack(msg);
            }

            ComandoDispositivoEjecutado ev =
                    objectMapper.readValue(env.payload(), ComandoDispositivoEjecutado.class);

            ingest.ingest(ev);
            LOG.infof("audit_dlq_reprocessed_ok idEvento=%s aggregateId=%s", env.idEvento(),
                    env.aggregateId());

            return ack(msg);

        } catch (Exception e) {
            LOG.errorf(e, "audit_dlq_reprocess_failed -> parking_lot");

            // En vez de “anidar”, mandamos:
            // - dlqJson como dlqMessage
            // - originalEnvelopeJson (si se pudo extraer) como originalEnvelope
            String originalEnvelopeBestEffort = null;
            try {
                AuditDeadLetterMessage dlqMsg =
                        objectMapper.readValue(dlqJson, AuditDeadLetterMessage.class);
                originalEnvelopeBestEffort =
                        (dlqMsg.originalEnvelope() != null && !dlqMsg.originalEnvelope().isBlank())
                                ? dlqMsg.originalEnvelope()
                                : dlqMsg.originalPayload();
            } catch (Exception ignored) {
                // se queda null
            }

            dlqPublisher.toParkingLotFromDlq("AUDIT_PARKING", dlqJson, originalEnvelopeBestEffort,
                    e, null, null);

            return ack(msg);
        }
    }


    /**
     * Convierte {@code msg.ack()} (CompletionStage) a {@link Uni}.
     */
    private static Uni<Void> ack(Message<?> msg) {
        return Uni.createFrom().completionStage(msg.ack());
    }
}
