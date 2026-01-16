package com.haedcom.access.infrastructure.messaging;

import com.haedcom.access.domain.events.OutboxSendException;

/**
 * Heurística para clasificar fallos de Kafka en retryable/no-retryable.
 *
 * <p>
 * SmallRye Reactive Messaging puede envolver excepciones del driver Kafka. Por eso se inspecciona
 * la cadena de causas y se compara por FQCN (nombre completo de clase) para evitar acoplar imports
 * directos a clases específicas.
 * </p>
 *
 * <h2>Reglas</h2>
 * <ul>
 * <li>Timeout → retryable</li>
 * <li>Network/Disconnect → retryable</li>
 * <li>RetriableException → retryable</li>
 * <li>Serialization/Config/RecordTooLarge → no retryable</li>
 * <li>Unknown → retryable conservador</li>
 * </ul>
 */
final class KafkaOutboxFailureClassifier {

    private KafkaOutboxFailureClassifier() {}

    static OutboxSendException classify(Exception ex) {
        Throwable root = unwrap(ex);

        if (isInstanceOf(root, "org.apache.kafka.common.errors.TimeoutException")) {
            return OutboxSendException.timeout("Kafka timeout publicando mensaje", ex,
                    "KAFKA_TIMEOUT");
        }

        if (isInstanceOf(root, "org.apache.kafka.common.errors.NetworkException")
                || isInstanceOf(root, "org.apache.kafka.common.errors.DisconnectException")) {
            return OutboxSendException.connection(
                    "Kafka network/connection error publicando mensaje", ex, "KAFKA_CONNECTION");
        }

        if (isInstanceOf(root, "org.apache.kafka.common.errors.RetriableException")) {
            return OutboxSendException.unknown("Kafka retriable error publicando mensaje", ex, true,
                    "KAFKA_RETRIABLE");
        }

        if (isInstanceOf(root, "org.apache.kafka.common.errors.SerializationException")
                || isInstanceOf(root, "org.apache.kafka.common.config.ConfigException")
                || isInstanceOf(root, "org.apache.kafka.common.errors.RecordTooLargeException")) {
            return OutboxSendException.unknown(
                    "Kafka non-retryable error (serialization/config/record too large)", ex, false,
                    "KAFKA_NON_RETRYABLE");
        }

        return OutboxSendException.unknown("Kafka error desconocido publicando mensaje", ex, true,
                "KAFKA_UNKNOWN");
    }

    /** Unwrap simple para llegar a la causa raíz (si existe). */
    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    /** Checks por FQCN sin dependencia directa del driver Kafka. */
    private static boolean isInstanceOf(Throwable t, String fqcn) {
        while (t != null) {
            if (t.getClass().getName().equals(fqcn)) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
