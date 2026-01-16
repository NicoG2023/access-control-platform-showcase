package com.haedcom.access.infrastructure.messaging;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import org.eclipse.microprofile.reactive.messaging.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Publicador centralizado para enviar mensajes a DLQ y Parking Lot.
 *
 * <p>
 * Mantiene un contrato único ({@link AuditDeadLetterMessage}) y evita anidamientos.
 * </p>
 */
@ApplicationScoped
public class AuditDeadLetterPublisher {

    private final MutinyEmitter<Record<String, String>> dlqEmitter;
    private final MutinyEmitter<Record<String, String>> parkingEmitter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Inject
    public AuditDeadLetterPublisher(
            @Channel("audit-dlq") MutinyEmitter<Record<String, String>> dlqEmitter,
            @Channel("audit-parking-lot") MutinyEmitter<Record<String, String>> parkingEmitter,
            ObjectMapper objectMapper, Clock clock) {
        this.dlqEmitter = Objects.requireNonNull(dlqEmitter, "dlqEmitter es obligatorio");
        this.parkingEmitter =
                Objects.requireNonNull(parkingEmitter, "parkingEmitter es obligatorio");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper es obligatorio");
        this.clock = (clock != null) ? clock : Clock.systemUTC();
    }

    /** Caso típico: fallo en consumer principal. */
    public void toDlqFromMain(String key, String envelopeJson, Throwable error,
            Map<String, Object> kafka, Map<String, Object> envelopeMeta) {

        publish(dlqEmitter, key, new AuditDeadLetterMessage("MAIN_CONSUMER", envelopeJson, // originalPayload
                envelopeJson, // originalEnvelope
                null, // dlqMessage
                errorType(error), safeMsg(errorMessage(error), 500), OffsetDateTime.now(clock),
                nonNullMap(kafka), nonNullMap(envelopeMeta)));
    }

    /** Caso típico: fallo nuevamente al reprocesar un DLQ -> Parking Lot. */
    public void toParkingLotFromDlq(String key, String dlqJson, String originalEnvelopeJson,
            Throwable error, Map<String, Object> kafka, Map<String, Object> envelopeMeta) {

        publish(parkingEmitter, key, new AuditDeadLetterMessage("DLQ_REPROCESSOR", dlqJson, // originalPayload
                                                                                            // (lo
                                                                                            // que
                                                                                            // falló
                                                                                            // ahora)
                originalEnvelopeJson, // originalEnvelope (lo que realmente quieres analizar)
                dlqJson, // dlqMessage (opcional, para trazabilidad)
                errorType(error), safeMsg(errorMessage(error), 500), OffsetDateTime.now(clock),
                nonNullMap(kafka), nonNullMap(envelopeMeta)));
    }

    /** Fallback genérico (por si lo usas en otros consumers). */
    public void toParkingLotGeneric(String key, String originalPayload, String originalEnvelope,
            String dlqMessage, Throwable error, Map<String, Object> kafka,
            Map<String, Object> envelopeMeta) {

        publish(parkingEmitter, key,
                new AuditDeadLetterMessage("UNKNOWN", originalPayload, originalEnvelope, dlqMessage,
                        errorType(error), safeMsg(errorMessage(error), 500),
                        OffsetDateTime.now(clock), nonNullMap(kafka), nonNullMap(envelopeMeta)));
    }

    private void publish(MutinyEmitter<Record<String, String>> emitter, String key,
            AuditDeadLetterMessage msg) {
        try {
            String k = (key == null || key.isBlank()) ? "UNKNOWN_KEY" : key;
            String json = objectMapper.writeValueAsString(msg);
            Uni<Void> uni = emitter.send(Record.of(k, json));
            uni.await().indefinitely();
        } catch (Exception ignored) {
            // Best-effort: DLQ/parking lot no debe tumbar el consumer.
        }
    }

    private static String errorType(Throwable t) {
        return (t != null) ? t.getClass().getName() : "UnknownError";
    }

    private static String errorMessage(Throwable t) {
        return (t != null) ? t.getMessage() : null;
    }

    private static Map<String, Object> nonNullMap(Map<String, Object> m) {
        return (m != null) ? m : Map.of();
    }

    private static String safeMsg(String s, int max) {
        if (s == null)
            return null;
        String v = s.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
