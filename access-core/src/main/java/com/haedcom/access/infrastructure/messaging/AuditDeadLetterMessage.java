package com.haedcom.access.infrastructure.messaging;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Mensaje estándar para DLQ y Parking Lot.
 *
 * <p>
 * Objetivo: conservar el payload original + contexto suficiente para diagnóstico y (si aplica)
 * reprocesamiento, con un contrato estable y sin necesidad de “anidar” mensajes.
 * </p>
 *
 * <h2>Campos recomendados</h2>
 * <ul>
 * <li>{@code source}: etapa del pipeline que generó el mensaje</li>
 * <li>{@code originalPayload}: payload recibido por el consumer que falló</li>
 * <li>{@code originalEnvelope}: JSON del OutboxKafkaEnvelope (si se pudo extraer)</li>
 * <li>{@code errorType}/{@code errorMessage}: causa resumida</li>
 * <li>{@code kafka}: metadata útil (topic, partition, offset, key, timestamp)</li>
 * <li>{@code envelope}: metadata del outbox (si se logró parsear)</li>
 * </ul>
 */
public record AuditDeadLetterMessage(String source, String originalPayload, String originalEnvelope,
        String dlqMessage, String errorType, String errorMessage, OffsetDateTime failedAtUtc,
        Map<String, Object> kafka, Map<String, Object> envelope) {
}
