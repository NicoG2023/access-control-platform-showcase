package com.haedcom.access.domain.events;

import java.time.Duration;
import java.util.Objects;

/**
 * Excepción estándar para fallos al publicar un
 * {@link com.haedcom.access.domain.model.OutboxEvent}.
 *
 * <p>
 * Esta excepción encapsula información suficiente para que el dispatcher implemente <b>retry
 * inteligente</b> (reintentable vs definitivo), sin acoplarse a detalles del transporte (HTTP,
 * Kafka, AMQP, etc.).
 * </p>
 *
 * <h2>Uso recomendado</h2>
 * <ul>
 * <li><b>Retryable</b>: fallas transitorias (timeouts, red, 5xx, 429, 408, saturación).</li>
 * <li><b>No retryable</b>: fallas definitivas (4xx excepto 408/429, validación, contrato
 * roto).</li>
 * <li><b>Retry-After</b>: cuando el origen provee una ventana sugerida de reintento (p.ej. HTTP
 * 429/503 con header Retry-After).</li>
 * </ul>
 */
public class OutboxSendException extends Exception {

    /**
     * Clasificación estable del tipo de fallo. Útil para métricas y políticas.
     */
    public enum FailureType {
        /** Errores HTTP con status disponible. */
        HTTP,
        /** Timeout (de socket, request, etc.). */
        TIMEOUT,
        /** Error de conexión/DNS/refused. */
        CONNECTION,
        /** Error IO genérico/transitorio. */
        IO,
        /** Error de transporte no clasificado (framework). */
        TRANSPORT,
        /** Error inesperado/desconocido. */
        UNKNOWN
    }

    private final boolean retryable;
    private final Integer httpStatus; // null si no aplica
    private final String errorCode; // corto, estable: HTTP_503, TIMEOUT, CONNECTION...
    private final FailureType failureType; // clasificación estable
    private final Duration retryAfter; // null si no aplica (p.ej. 429 con Retry-After)

    /**
     * Constructor completo.
     *
     * @param message mensaje de negocio/diagnóstico (sin incluir payloads sensibles)
     * @param cause causa original
     * @param retryable {@code true} si el dispatcher debería reintentar
     * @param httpStatus status HTTP si aplica (si no aplica, null)
     * @param errorCode código corto para diagnóstico y métricas (no null)
     * @param failureType clasificación del fallo (no null)
     * @param retryAfter ventana sugerida de reintento; null si no aplica
     */
    public OutboxSendException(String message, Throwable cause, boolean retryable,
            Integer httpStatus, String errorCode, FailureType failureType, Duration retryAfter) {
        super(message, cause);
        this.retryable = retryable;
        this.httpStatus = httpStatus;
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode es obligatorio");
        this.failureType = Objects.requireNonNull(failureType, "failureType es obligatorio");
        this.retryAfter = retryAfter;
    }

    // ---------------------------------------------------------------------
    // Fábricas (recomendadas para consistencia)
    // ---------------------------------------------------------------------

    /** Crea una excepción para error HTTP. */
    public static OutboxSendException http(String message, Throwable cause, int status,
            boolean retryable, String errorCode, Duration retryAfter) {
        return new OutboxSendException(message, cause, retryable, status, errorCode,
                FailureType.HTTP, retryAfter);
    }

    /** Crea una excepción para timeout. */
    public static OutboxSendException timeout(String message, Throwable cause, String errorCode) {
        return new OutboxSendException(message, cause, true, 408, errorCode, FailureType.TIMEOUT,
                null);
    }

    /** Crea una excepción para conexión. */
    public static OutboxSendException connection(String message, Throwable cause,
            String errorCode) {
        return new OutboxSendException(message, cause, true, null, errorCode,
                FailureType.CONNECTION, null);
    }

    /** Crea una excepción para IO transitorio. */
    public static OutboxSendException io(String message, Throwable cause, String errorCode) {
        return new OutboxSendException(message, cause, true, null, errorCode, FailureType.IO, null);
    }

    /** Crea una excepción desconocida (por defecto reintentable). */
    public static OutboxSendException unknown(String message, Throwable cause, boolean retryable,
            String errorCode) {
        return new OutboxSendException(message, cause, retryable, null, errorCode,
                FailureType.UNKNOWN, null);
    }

    // ---------------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------------

    /** Indica si el fallo debería reintentarse. */
    public boolean isRetryable() {
        return retryable;
    }

    /** HTTP status si el transporte es HTTP; null en otros casos. */
    public Integer getHttpStatus() {
        return httpStatus;
    }

    /** Código de error corto para diagnóstico y métricas (estable). */
    public String getErrorCode() {
        return errorCode;
    }

    /** Clasificación estable del fallo para políticas/observabilidad. */
    public FailureType getFailureType() {
        return failureType;
    }

    /**
     * Ventana sugerida para reintentar (si el origen lo indica, p.ej. HTTP Retry-After).
     *
     * <p>
     * Si es null, el dispatcher debe calcular backoff con su política normal.
     * </p>
     */
    public Duration getRetryAfter() {
        return retryAfter;
    }

    @Override
    public String toString() {
        return "OutboxSendException{" + "retryable=" + retryable + ", httpStatus=" + httpStatus
                + ", errorCode='" + errorCode + '\'' + ", failureType=" + failureType
                + ", retryAfter=" + retryAfter + ", message='" + getMessage() + '\'' + '}';
    }
}
