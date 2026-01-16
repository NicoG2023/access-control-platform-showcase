package com.haedcom.access.infrastructure.messaging;

import java.util.Objects;
import java.util.UUID;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haedcom.access.application.acceso.decision.ReglaCandidatesCacheInvalidator;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import com.haedcom.access.domain.events.ReglaAccesoPolicyChanged;
import com.haedcom.access.domain.events.ReglaAccesoPolicyInvalidateAllRequested;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Consumer de eventos de política (reglas de acceso) para invalidar caches locales (Caffeine) en
 * todos los nodos, propagado vía Kafka (Transactional Outbox).
 *
 * <p>
 * Espera mensajes con JSON de {@link OutboxKafkaEnvelope}. El {@code payload} contiene el evento de
 * dominio serializado.
 * </p>
 *
 * <h2>Política de ACK</h2>
 * <ul>
 * <li>Si no es un eventType relevante: ACK y salir.</li>
 * <li>Si falla parseo o invalidación: log + ACK (para evitar loops infinitos).</li>
 * </ul>
 *
 * <p>
 * Idempotente: invalidar una key inexistente no causa problemas.
 * </p>
 */
@ApplicationScoped
public class ReglaAccesoCacheInvalidationKafkaConsumer {

    private static final Logger LOG =
            Logger.getLogger(ReglaAccesoCacheInvalidationKafkaConsumer.class);

    private static final String EVT_POLICY_CHANGED = ReglaAccesoPolicyChanged.class.getSimpleName();

    private static final String EVT_INVALIDATE_ALL =
            ReglaAccesoPolicyInvalidateAllRequested.class.getSimpleName();

    private final ObjectMapper objectMapper;
    private final ReglaCandidatesCacheInvalidator reglaCandidatesCacheInvalidator;

    public ReglaAccesoCacheInvalidationKafkaConsumer(ObjectMapper objectMapper,
            ReglaCandidatesCacheInvalidator reglaCandidatesCacheInvalidator) {

        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper es obligatorio");
        this.reglaCandidatesCacheInvalidator = Objects.requireNonNull(
                reglaCandidatesCacheInvalidator, "reglaCandidatesCacheInvalidator es obligatorio");
    }

    @Incoming("policy-regla-cache-invalidation")
    public Uni<Void> onMessage(Message<String> msg) {
        final String json = msg.getPayload();

        final IncomingKafkaRecordMetadata<?, ?> meta = (IncomingKafkaRecordMetadata<?, ?>) msg
                .getMetadata(IncomingKafkaRecordMetadata.class).orElse(null);

        try {
            OutboxKafkaEnvelope env = objectMapper.readValue(json, OutboxKafkaEnvelope.class);

            String eventType = env.eventType();
            if (eventType == null) {
                return ack(msg);
            }

            if (EVT_POLICY_CHANGED.equals(eventType)) {

                ReglaAccesoPolicyChanged ev =
                        objectMapper.readValue(env.payload(), ReglaAccesoPolicyChanged.class);

                invalidateAreaAllTipos(ev.orgId(), ev.areaId(), env);
                return ack(msg);

            } else if (EVT_INVALIDATE_ALL.equals(eventType)) {

                ReglaAccesoPolicyInvalidateAllRequested ev = objectMapper.readValue(env.payload(),
                        ReglaAccesoPolicyInvalidateAllRequested.class);

                invalidateAllForOrg(ev.orgId(), env);
                return ack(msg);

            } else {
                return ack(msg);
            }

        } catch (Exception e) {
            LOG.warnf(e, "cache_invalidation_consume_failed topic=%s partition=%s offset=%s",
                    meta != null ? meta.getTopic() : null,
                    meta != null ? meta.getPartition() : null,
                    meta != null ? meta.getOffset() : null);

            return ack(msg);
        }
    }

    /**
     * Invalida candidates cache para (orgId, areaId) para TODOS los tipos de sujeto.
     *
     * <p>
     * Esto es necesario porque tu evento no trae tipoSujeto, y el cache normalmente se segmenta al
     * menos por tipo de sujeto para reducir cardinalidad.
     * </p>
     */
    private void invalidateAreaAllTipos(UUID orgId, UUID areaId, OutboxKafkaEnvelope env) {
        if (orgId == null || areaId == null) {
            LOG.warnf("cache_invalidation_skip missing orgId/areaId outboxId=%s eventType=%s",
                    env != null ? env.idEvento() : null, env != null ? env.eventType() : null);
            return;
        }

        for (TipoSujetoAcceso t : TipoSujetoAcceso.values()) {
            reglaCandidatesCacheInvalidator.invalidate(orgId, areaId, t);
        }

        LOG.debugf("cache_invalidation_ok type=%s orgId=%s areaId=%s outboxId=%s aggregateId=%s",
                env != null ? env.eventType() : null, orgId, areaId,
                env != null ? env.idEvento() : null, env != null ? env.aggregateId() : null);
    }

    /**
     * Invalida todo el cache local (o por org si tu invalidator lo soporta).
     */
    private void invalidateAllForOrg(UUID orgId, OutboxKafkaEnvelope env) {
        reglaCandidatesCacheInvalidator.invalidateAll();

        LOG.warnf("cache_invalidate_all type=%s orgId=%s outboxId=%s",
                env != null ? env.eventType() : null, orgId, env != null ? env.idEvento() : null);
    }

    private static Uni<Void> ack(Message<?> msg) {
        return Uni.createFrom().completionStage(msg.ack());
    }
}
