package com.haedcom.access.application.audit;

import com.haedcom.access.domain.events.ComandoDispositivoEjecutado;
import com.haedcom.access.domain.events.ComandoDispositivoEmitido;
import com.haedcom.access.domain.events.DecisionAccesoTomada;
import com.haedcom.access.domain.events.IntentoAccesoRegistrado;
import com.haedcom.access.domain.events.ReglaAccesoChangeRejected;
import com.haedcom.access.domain.events.ReglaAccesoPolicyChanged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Listener de auditoría funcional (Nivel B) para eventos de dominio.
 *
 * <p>
 * Este listener escucha eventos emitidos por la capa de aplicación/dominio y persiste un registro
 * {@link com.haedcom.access.domain.model.AuditLog} por cada evento, usando
 * {@link AuditIngestService}.
 * </p>
 *
 * <h2>Política transaccional</h2>
 * <ul>
 * <li>{@link TransactionPhase#AFTER_SUCCESS}: solo se audita si la transacción que originó el
 * evento <b>commit</b> fue exitosa. Esto evita auditorías de cambios que finalmente se hicieron
 * rollback.</li>
 * <li>{@code REQUIRES_NEW}: la persistencia de auditoría se realiza en una transacción
 * independiente para aislarla del flujo principal y reducir acoplamiento. Si la auditoría falla, el
 * evento original ya se consolidó.</li>
 * </ul>
 *
 * <h2>Idempotencia</h2>
 * <p>
 * La deduplicación se aplica en {@link AuditIngestService} mediante {@code eventKey} y constraint
 * único por tenant, evitando inserciones duplicadas en reintentos o replays.
 * </p>
 */
@ApplicationScoped
public class AuditEventListener {

    @Inject
    AuditIngestService auditIngestService;

    /**
     * Audita el evento {@link ComandoDispositivoEjecutado}.
     *
     * <p>
     * Corresponde a la ejecución final de un comando en un dispositivo (incluyendo estado final y/o
     * errores). Se registra para trazabilidad operativa del flujo end-to-end.
     * </p>
     *
     * @param ev evento observado (no null)
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onComandoEjecutado(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) ComandoDispositivoEjecutado ev) {
        auditIngestService.ingest(ev);
    }

    /**
     * Audita el evento {@link IntentoAccesoRegistrado}.
     *
     * <p>
     * Representa el intento de acceso capturado (dispositivo, área, sujeto, dirección, etc.). Es la
     * “raíz” de correlación de los eventos siguientes (decisión y comando).
     * </p>
     *
     * @param ev evento observado (no null)
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onIntento(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) IntentoAccesoRegistrado ev) {
        auditIngestService.ingest(ev);
    }

    /**
     * Audita el evento {@link DecisionAccesoTomada}.
     *
     * <p>
     * Registra el resultado de la evaluación de reglas (ALLOW/DENY/...) junto con motivo/código y
     * vigencia (expiración) cuando aplica.
     * </p>
     *
     * @param ev evento observado (no null)
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onDecision(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) DecisionAccesoTomada ev) {
        auditIngestService.ingest(ev);
    }

    /**
     * Audita el evento {@link ComandoDispositivoEmitido}.
     *
     * <p>
     * Registra que el sistema emitió un comando (OPEN/DENY/...) hacia un dispositivo, normalmente
     * derivado de una decisión. Permite trazabilidad del “output” del motor.
     * </p>
     *
     * @param ev evento observado (no null)
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onComando(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) ComandoDispositivoEmitido ev) {
        auditIngestService.ingest(ev);
    }

    /**
     * Audita el evento {@link ReglaAccesoPolicyChanged}.
     *
     * <p>
     * Se emite cuando cambia una regla de acceso (create/update/activate/inactivate/soft-delete), y
     * su uso principal es invalidar caches distribuidos. También se registra para trazabilidad de
     * cambios de política del tenant por área.
     * </p>
     *
     * @param ev evento observado (no null)
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onPolicyChanged(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) ReglaAccesoPolicyChanged ev) {
        auditIngestService.ingest(ev);
    }

    /**
     * Audita el evento {@link ReglaAccesoChangeRejected}.
     *
     * <p>
     * Registra intentos rechazados de cambios sobre reglas (p.ej. duplicado 409, validación 400,
     * not found 404 cuando el contexto lo permite). Se utiliza para diagnóstico y cumplimiento.
     * </p>
     *
     * <p>
     * Nota: si tu contrato exige {@code areaId} no-null, solo se pueden auditar rechazos cuando el
     * área es conocida (p.ej. create/update con {@code req.idArea()}). Un 404 “regla inexistente”
     * no tiene área asociada y no puede auditarse sin relajar el contrato.
     * </p>
     *
     * @param ev evento observado (no null)
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onReglaRejected(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) ReglaAccesoChangeRejected ev) {
        auditIngestService.ingest(ev);
    }
}
