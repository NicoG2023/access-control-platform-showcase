package com.haedcom.access.application.acceso.decision.model;

import java.time.OffsetDateTime;
import com.haedcom.access.domain.enums.TipoComandoDispositivo;
import com.haedcom.access.domain.enums.TipoResultadoDecision;

/**
 * Resultado producido por el motor de decisiones.
 *
 * <p>
 * Contiene:
 * <ul>
 * <li>{@link #resultado()}: PERMITIR / DENEGAR / PENDIENTE / ERROR</li>
 * <li>{@link #codigoMotivo()}: código para resolver {@code CatalogoMotivoDecision}</li>
 * <li>{@link #detalleMotivo()}: detalle legible (auditoría/diagnóstico)</li>
 * <li>{@link #decididoEnUtc()}: timestamp de decisión</li>
 * <li>{@link #comandoSugerido()}: comando recomendado hacia el dispositivo (opcional)</li>
 * <li>{@link #mensajeSugerido()}: mensaje opcional para mostrar en el dispositivo</li>
 * <li>{@link #expiraEnUtc()}: expiración para decisiones pendientes (opcional)</li>
 * </ul>
 * </p>
 *
 * <p>
 * El {@code AccesoService} traduce esta salida a entidades persistentes:
 * <ul>
 * <li>{@code DecisionAcceso} usando {@code resultado/codigoMotivo/detalle/decidido/expira}</li>
 * <li>{@code ComandoDispositivo} usando {@code comandoSugerido/mensajeSugerido}</li>
 * </ul>
 * </p>
 *
 * @param resultado resultado de la decisión
 * @param codigoMotivo código de motivo (debe existir en {@code CatalogoMotivoDecision})
 * @param detalleMotivo detalle (opcional)
 * @param decididoEnUtc timestamp UTC
 * @param comandoSugerido comando recomendado (opcional)
 * @param mensajeSugerido mensaje recomendado (opcional)
 * @param expiraEnUtc expiración (opcional, útil para PENDIENTE)
 */
public record DecisionOutput(TipoResultadoDecision resultado, String codigoMotivo,
        String detalleMotivo, OffsetDateTime decididoEnUtc, TipoComandoDispositivo comandoSugerido,
        String mensajeSugerido, OffsetDateTime expiraEnUtc) {

    /**
     * Construye una decisión PERMITIR.
     */
    public static DecisionOutput allow(OffsetDateTime now, String motivo, String detalle,
            TipoComandoDispositivo cmd, String msg) {
        return new DecisionOutput(TipoResultadoDecision.PERMITIR, motivo, detalle, now, cmd, msg,
                null);
    }

    /**
     * Construye una decisión DENEGAR.
     */
    public static DecisionOutput deny(OffsetDateTime now, String motivo, String detalle,
            TipoComandoDispositivo cmd, String msg) {
        return new DecisionOutput(TipoResultadoDecision.DENEGAR, motivo, detalle, now, cmd, msg,
                null);
    }

    /**
     * Construye una decisión PENDIENTE con expiración.
     */
    public static DecisionOutput pending(OffsetDateTime now, String motivo, String detalle,
            TipoComandoDispositivo cmd, String msg, OffsetDateTime expira) {
        return new DecisionOutput(TipoResultadoDecision.PENDIENTE, motivo, detalle, now, cmd, msg,
                expira);
    }

    /**
     * Construye una decisión ERROR.
     *
     * <p>
     * Se recomienda usar un motivo de error que exista en catálogo (p.ej. {@code POLICY_ERROR}).
     * </p>
     */
    public static DecisionOutput error(OffsetDateTime now, String motivo, String detalle) {
        return new DecisionOutput(TipoResultadoDecision.ERROR, motivo, detalle, now, null,
                "Error en el acceso", null);
    }
}
