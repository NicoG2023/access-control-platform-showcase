package com.haedcom.access.infrastructure.messaging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haedcom.access.application.audit.AuditIngestService;
import com.haedcom.access.domain.events.ComandoDispositivoEjecutado;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Consumer de auditoría para eventos emitidos por el Transactional Outbox en Kafka.
 *
 * <h2>Formato de entrada</h2>
 * <p>
 * Este consumer espera que el tópico contenga un JSON de {@link OutboxKafkaEnvelope}. En dicho
 * envelope:
 * </p>
 * <ul>
 * <li>{@code eventType}: tipo lógico del evento de dominio (por ejemplo,
 * {@code ComandoDispositivoEjecutado}).</li>
 * <li>{@code payload}: JSON (string) del evento de dominio.</li>
 * </ul>
 *
 * <h2>Qué procesa</h2>
 * <p>
 * Para reducir acoplamiento y ruido, este consumer procesa <b>únicamente</b> el evento
 * {@link ComandoDispositivoEjecutado}. Cualquier otro {@code eventType} se ignora y se hace ACK.
 * </p>
 *
 * <h2>Política de errores</h2>
 * <ul>
 * <li>Si falla el parseo del envelope o del payload, o si falla la ingestión: se publica a DLQ con
 * metadata.</li>
 * <li>El mensaje original se <b>ackea</b> igualmente para evitar reintentos infinitos desde el
 * tópico principal.</li>
 * </ul>
 *
 * <h2>ACK/commit</h2>
 * <p>
 * {@link Message#ack()} retorna {@code CompletionStage<Void>}. Para mantener una firma reactiva,
 * este consumer lo convierte a {@link Uni} usando {@link Uni#createFrom()}.
 * </p>
 *
 * <h2>Metadata Kafka</h2>
 * <p>
 * En SmallRye, {@link IncomingKafkaRecordMetadata} es genérico, pero el acceso vía
 * {@link Message#getMetadata(Class)} pierde la información genérica en runtime. Por eso se usa un
 * cast controlado a {@code IncomingKafkaRecordMetadata<?, ?>} con un único
 * {@code @SuppressWarnings("unchecked")}.
 * </p>
 */
@ApplicationScoped
public class ComandoEjecutadoAuditKafkaConsumer {

    private static final Logger LOG = Logger.getLogger(ComandoEjecutadoAuditKafkaConsumer.class);

    private final AuditIngestService ingest;
    private final ObjectMapper objectMapper;
    private final AuditDeadLetterPublisher dlq;

    public ComandoEjecutadoAuditKafkaConsumer(AuditIngestService ingest, ObjectMapper objectMapper,
            AuditDeadLetterPublisher dlq) {
        this.ingest = Objects.requireNonNull(ingest, "ingest es obligatorio");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper es obligatorio");
        this.dlq = Objects.requireNonNull(dlq, "dlq es obligatorio");
    }

    /**
     * Procesa un mensaje del tópico de auditoría.
     *
     * <p>
     * Flujo:
     * </p>
     * <ol>
     * <li>Lee el JSON (payload) del mensaje.</li>
     * <li>Parsea {@link OutboxKafkaEnvelope}.</li>
     * <li>Si el {@code eventType} no es {@code ComandoDispositivoEjecutado}: ACK y salir.</li>
     * <li>Parsea el {@code payload} al evento de dominio y ejecuta
     * {@link AuditIngestService#ingest(Object)}.</li>
     * <li>Si falla algo: publica a DLQ y hace ACK.</li>
     * </ol>
     *
     * @param msg mensaje entrante (payload = JSON del envelope)
     * @return Uni que completa cuando el ACK termina
     */
    @Incoming("audit-comando-ejecutado")
    public Uni<Void> onMessage(Message<String> msg) {
        final String json = msg.getPayload();

        final IncomingKafkaRecordMetadata<?, ?> meta = (IncomingKafkaRecordMetadata<?, ?>) msg
                .getMetadata(IncomingKafkaRecordMetadata.class).orElse(null);

        try {
            OutboxKafkaEnvelope env = objectMapper.readValue(json, OutboxKafkaEnvelope.class);

            // Consumimos solo el evento que nos interesa para auditoría
            if (!ComandoDispositivoEjecutado.class.getSimpleName().equals(env.eventType())) {
                return ack(msg);
            }

            ComandoDispositivoEjecutado ev =
                    objectMapper.readValue(env.payload(), ComandoDispositivoEjecutado.class);

            ingest.ingest(ev);
            return ack(msg);

        } catch (Exception e) {
            LOG.errorf(e, "audit_consume_failed topic=%s partition=%s offset=%s",
                    meta != null ? meta.getTopic() : null,
                    meta != null ? meta.getPartition() : null,
                    meta != null ? meta.getOffset() : null);

            dlq.toDlqFromMain(extractKey(meta), json, e, kafkaMeta(meta),
                    envelopeMetaBestEffort(json));

            return ack(msg);
        }
    }

    /**
     * Convierte {@code msg.ack()} (CompletionStage) a {@link Uni}.
     *
     * @param msg mensaje a ackear
     * @return Uni que completa cuando el ACK finaliza
     */
    private static Uni<Void> ack(Message<?> msg) {
        return Uni.createFrom().completionStage(msg.ack());
    }

    /**
     * Extrae la key del record Kafka (si está disponible). Si no, retorna un valor defensivo.
     */
    private static String extractKey(IncomingKafkaRecordMetadata<?, ?> meta) {
        Object key = (meta != null) ? meta.getKey() : null;
        return (key != null) ? key.toString() : "UNKNOWN_KEY";
    }

    /**
     * Construye un mapa con metadata Kafka útil para diagnosticar fallos.
     */
    private static Map<String, Object> kafkaMeta(IncomingKafkaRecordMetadata<?, ?> meta) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (meta == null)
            return m;

        m.put("topic", meta.getTopic());
        m.put("partition", meta.getPartition());
        m.put("offset", meta.getOffset());
        m.put("timestamp", meta.getTimestamp());
        m.put("key", meta.getKey());
        return m;
    }

    /**
     * Intenta extraer metadata del envelope de manera best-effort.
     *
     * <p>
     * Si el JSON no es un {@link OutboxKafkaEnvelope} válido, retorna un mapa vacío.
     * </p>
     */
    private Map<String, Object> envelopeMetaBestEffort(String json) {
        try {
            OutboxKafkaEnvelope env = objectMapper.readValue(json, OutboxKafkaEnvelope.class);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idEvento", safe(env.idEvento()));
            m.put("orgId", safe(env.orgId()));
            m.put("eventType", env.eventType());
            m.put("aggregateType", env.aggregateType());
            m.put("aggregateId", env.aggregateId());
            m.put("createdAtUtc", env.createdAtUtc());
            m.put("attempts", env.attempts());
            return m;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String safe(UUID id) {
        return (id != null) ? id.toString() : null;
    }
}
