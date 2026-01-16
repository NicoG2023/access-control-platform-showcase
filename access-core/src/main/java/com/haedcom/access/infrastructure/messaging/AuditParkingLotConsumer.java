package com.haedcom.access.infrastructure.messaging;

import java.util.Map;
import java.util.Objects;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Consumer de <b>Parking Lot</b> para auditoría.
 *
 * <h2>Qué es “Parking Lot”</h2>
 * <p>
 * Un tópico de <b>estacionamiento terminal</b> para mensajes que no pudieron ser procesados
 * automáticamente ni siquiera tras un segundo intento:
 * </p>
 * <ol>
 * <li>Fallo en el consumer principal (tópico principal).</li>
 * <li>Mensaje enviado a DLQ.</li>
 * <li>Fallo nuevamente en el reprocesador de DLQ → mensaje enviado a Parking Lot.</li>
 * </ol>
 *
 * <h2>Rol de este consumer</h2>
 * <p>
 * Este consumer <b>no reprocesa</b> ni reingesta; su objetivo es:
 * </p>
 * <ul>
 * <li>Registrar un resumen accionable del fallo.</li>
 * <li>Conservar visibilidad sobre el “caso perdido” para runbooks (diagnóstico/manual
 * actions).</li>
 * <li>Evitar loops: el Parking Lot siempre se ACKea.</li>
 * </ul>
 *
 * <h2>Formato esperado</h2>
 * <p>
 * Se espera un JSON de {@link AuditDeadLetterMessage}. Típicamente:
 * </p>
 * <ul>
 * <li>El mensaje en Parking Lot es un {@link AuditDeadLetterMessage}.</li>
 * <li>Su {@code originalPayload} puede contener el JSON de DLQ (otro
 * {@link AuditDeadLetterMessage}) si el reprocesador decidió enviar “el DLQ completo” como
 * original.</li>
 * </ul>
 *
 * <h2>Política de ACK</h2>
 * <p>
 * Regla de oro: <b>siempre ACK</b>. El Parking Lot es terminal; no debe ciclar.
 * </p>
 */
@ApplicationScoped
public class AuditParkingLotConsumer {

    private static final Logger LOG = Logger.getLogger(AuditParkingLotConsumer.class);

    /** Evita imprimir mensajes enormes en logs. */
    private static final int MAX_ERROR_MSG = 500;
    private static final int MAX_PREVIEW = 400;
    private static final int MAX_MAP_LOG = 1500;

    private final ObjectMapper objectMapper;

    public AuditParkingLotConsumer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper es obligatorio");
    }

    /**
     * Consume mensajes del tópico de Parking Lot.
     *
     * <p>
     * Flujo:
     * </p>
     * <ol>
     * <li>Intenta parsear el payload como {@link AuditDeadLetterMessage}.</li>
     * <li>Opcionalmente intenta “desanidar” si {@code originalPayload} contiene un DLQ
     * message.</li>
     * <li>Loggea un resumen defendible (sin imprimir payloads gigantes).</li>
     * <li>Hace ACK <b>siempre</b>.</li>
     * </ol>
     *
     * @param msg mensaje entrante (payload = JSON)
     * @return Uni que completa cuando el ACK termina
     */
    @Incoming("audit-parking-lot-in")
    public Uni<Void> onParkingLot(Message<String> msg) {
        final String json = msg.getPayload();

        try {
            // 1) Mensaje principal (Parking Lot)
            AuditDeadLetterMessage parking =
                    objectMapper.readValue(json, AuditDeadLetterMessage.class);

            // 2) Intento de “desanidar”: originalPayload podría ser un DLQ message completo
            NestedDlqInfo nested = tryParseNestedDlq(parking.originalPayload());

            // 3) Resumen principal (accionable)
            LOG.errorf(
                    "audit_parking_lot_received failedAtUtc=%s errorType=%s errorMessage=%s nested=%s",
                    parking.failedAtUtc(), parking.errorType(),
                    truncate(parking.errorMessage(), MAX_ERROR_MSG), nested.summary());

            // 4) Metadata útil (sin pasarnos de largo)
            logMap("audit_parking_lot_kafka_meta", parking.kafka());
            logMap("audit_parking_lot_envelope_meta", parking.envelope());

            // 5) Si hubo nested DLQ, también lo resumimos
            if (nested.dlq() != null) {
                AuditDeadLetterMessage dlq = nested.dlq();

                LOG.errorf(
                        "audit_parking_lot_nested_dlq failedAtUtc=%s errorType=%s errorMessage=%s",
                        dlq.failedAtUtc(), dlq.errorType(),
                        truncate(dlq.errorMessage(), MAX_ERROR_MSG));

                logMap("audit_parking_lot_nested_kafka_meta", dlq.kafka());
                logMap("audit_parking_lot_nested_envelope_meta", dlq.envelope());
            }

        } catch (Exception e) {
            // JSON corrupto o formato inesperado.
            // Igual: Parking lot es terminal, solo log defensivo para poder ubicar el caso.
            LOG.errorf(e, "audit_parking_lot_unparseable payloadPreview=%s",
                    preview(json, MAX_PREVIEW));

            // Best-effort adicional: a veces el parking lot podría contener directamente el DLQ
            // JSON
            // o el envelope. Intentamos detectar si es un DLQ message “directo”.
            try {
                AuditDeadLetterMessage maybeDlq =
                        objectMapper.readValue(json, AuditDeadLetterMessage.class);

                LOG.errorf(
                        "audit_parking_lot_unparseable_but_deadletter_like failedAtUtc=%s errorType=%s errorMessage=%s",
                        maybeDlq.failedAtUtc(), maybeDlq.errorType(),
                        truncate(maybeDlq.errorMessage(), MAX_ERROR_MSG));
            } catch (Exception ignored) {
                // Nada más que hacer.
            }
        }

        return ack(msg);
    }

    /**
     * Intenta interpretar el {@code originalPayload} del Parking Lot como un
     * {@link AuditDeadLetterMessage} “anidado”.
     *
     * <p>
     * Esto cubre el caso típico donde el reprocesador DLQ falla y manda el JSON del DLQ message
     * completo como {@code originalPayload} al Parking Lot.
     * </p>
     *
     * @param originalPayload payload original guardado dentro del Parking Lot message
     * @return info de si pudo parsearse un DLQ message anidado
     */
    private NestedDlqInfo tryParseNestedDlq(String originalPayload) {
        if (originalPayload == null || originalPayload.isBlank()) {
            return new NestedDlqInfo(null, "no_original_payload");
        }
        try {
            AuditDeadLetterMessage nested =
                    objectMapper.readValue(originalPayload, AuditDeadLetterMessage.class);
            return new NestedDlqInfo(nested, "nested_dlq_parsed");
        } catch (Exception ignored) {
            // Puede ser un envelope directo u otro formato.
            return new NestedDlqInfo(null, "nested_dlq_not_parsed");
        }
    }

    /**
     * Convierte {@code msg.ack()} (CompletionStage) a {@link Uni}.
     *
     * <p>
     * Se usa para mantener una firma reactiva consistente y controlar el commit (cuando
     * {@code enable.auto.commit=false}).
     * </p>
     */
    private static Uni<Void> ack(Message<?> msg) {
        return Uni.createFrom().completionStage(msg.ack());
    }

    /** Loggea mapas sin arriesgar logs gigantes. */
    private static void logMap(String prefix, Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return;
        }
        String s = m.toString();
        if (s.length() > MAX_MAP_LOG) {
            s = s.substring(0, MAX_MAP_LOG) + "...";
        }
        LOG.errorf("%s %s", prefix, s);
    }

    private static String truncate(String s, int max) {
        if (s == null)
            return null;
        String v = s.trim();
        if (v.isEmpty())
            return null;
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static String preview(String s, int max) {
        if (s == null)
            return null;
        String v = s.trim();
        if (v.isEmpty())
            return null;
        return v.length() <= max ? v : v.substring(0, max);
    }

    /**
     * DTO interno para reportar si el mensaje anidado pudo parsearse.
     *
     * @param dlq DLQ message anidado (si se pudo parsear)
     * @param summary etiqueta corta para logs ("nested_dlq_parsed", etc.)
     */
    private record NestedDlqInfo(AuditDeadLetterMessage dlq, String summary) {
    }
}
