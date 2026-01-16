package com.haedcom.access.infrastructure.messaging;

import java.util.Objects;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haedcom.access.domain.events.OutboxEventSender;
import com.haedcom.access.domain.events.OutboxSendException;
import com.haedcom.access.domain.model.OutboxEvent;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;

/**
 * Implementación Kafka del puerto {@link OutboxEventSender}.
 *
 * <h2>Rol en Transactional Outbox</h2>
 * <p>
 * Este componente es el “publisher real”: toma un {@link OutboxEvent} ya persistido y lo publica en
 * Kafka. Si el envío falla, lanza {@link OutboxSendException} para que el
 * {@code OutboxEventProcessor} decida retry/backoff (retry inteligente).
 * </p>
 *
 * <h2>Qué se publica</h2>
 * <p>
 * Se publica un {@link OutboxKafkaEnvelope} (envelope completo) para maximizar trazabilidad:
 * </p>
 * <ul>
 * <li>metadata (idEvento, orgId, tipos, timestamps, attempts)</li>
 * <li>{@code payload}: JSON del evento de dominio (string)</li>
 * </ul>
 *
 * <h2>Key de Kafka</h2>
 * <p>
 * Por defecto la key es {@code orgId} (tenant) para mantener orden relativo por organización. Si
 * prefieres orden por agregado, puedes cambiar a {@code aggregateId} o una key compuesta.
 * </p>
 *
 * <h2>Sincronía</h2>
 * <p>
 * Se hace {@code await()} del envío para convertir fallos del producer en excepción, y así aplicar
 * retry inteligente desde outbox (en vez de perder el error en background).
 * </p>
 */
@ApplicationScoped
@Default
public class KafkaOutboxEventSender implements OutboxEventSender {

    private static final Logger LOG = Logger.getLogger(KafkaOutboxEventSender.class);

    /** Canal outgoing (SmallRye Reactive Messaging) configurado en application.properties. */
    private final MutinyEmitter<Record<String, String>> emitter;

    /** Serializador JSON para el envelope publicado. */
    private final ObjectMapper objectMapper;

    @Inject
    public KafkaOutboxEventSender(
            @Channel("outbox-events") MutinyEmitter<Record<String, String>> emitter,
            ObjectMapper objectMapper) {
        this.emitter = Objects.requireNonNull(emitter, "emitter es obligatorio");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper es obligatorio");
    }

    /**
     * Publica el evento del outbox en Kafka.
     *
     * <h3>Política de errores</h3>
     * <ul>
     * <li><b>Serialización/contrato</b> (JSON inválido) → <b>no retryable</b></li>
     * <li><b>Timeout/red/retriable Kafka</b> → <b>retryable</b></li>
     * <li><b>Errores definitivos Kafka</b> (config/serialization/record-too-large) → <b>no
     * retryable</b></li>
     * <li><b>Desconocidos</b> → <b>retryable</b> conservador</li>
     * </ul>
     *
     * @param event entidad outbox persistida (no null)
     * @throws OutboxSendException si falla la publicación (clasificada retryable/no retryable)
     */
    @Override
    public void send(OutboxEvent event) throws OutboxSendException {
        Objects.requireNonNull(event, "event es obligatorio");

        // Key por tenant (orden relativo por organización)
        String key = (event.getIdOrganizacion() != null) ? event.getIdOrganizacion().toString()
                : "UNKNOWN_ORG";

        final String json;
        try {
            json = objectMapper.writeValueAsString(OutboxKafkaEnvelope.from(event));
        } catch (JsonProcessingException ex) {
            // Contrato roto / JSON inválido → definitivo
            throw OutboxSendException.unknown(
                    "No se pudo serializar OutboxKafkaEnvelope (contrato/JSON inválido)", ex, false,
                    "JSON_SERIALIZATION");
        }

        try {
            Uni<Void> uni = emitter.send(Record.of(key, json));
            uni.await().indefinitely();

            LOG.debugf("KafkaOutboxEventSender - sent idEvento=%s orgId=%s type=%s aggregateId=%s",
                    event.getIdEvento(), key, event.getEventType(), event.getAggregateId());

        } catch (Exception ex) {
            // Clasifica según heurística (retryable/no retryable)
            throw KafkaOutboxFailureClassifier.classify(ex);
        }
    }
}
