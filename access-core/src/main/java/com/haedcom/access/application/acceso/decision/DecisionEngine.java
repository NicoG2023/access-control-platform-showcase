package com.haedcom.access.application.acceso.decision;

import com.haedcom.access.application.acceso.decision.model.DecisionContext;
import com.haedcom.access.application.acceso.decision.model.DecisionOutput;

/**
 * Contrato del motor de decisiones de acceso.
 *
 * <p>
 * Este motor debe ser un componente "puro" (idealmente):
 * </p>
 * <ul>
 * <li>No persiste entidades</li>
 * <li>No emite eventos</li>
 * <li>No conoce HTTP ni recursos REST</li>
 * <li>No depende de JPA ni repositorios</li>
 * </ul>
 *
 * <p>
 * Su responsabilidad es evaluar un {@link DecisionContext} (snapshot normalizado) y producir un
 * {@link DecisionOutput} (resultado + motivo + comando sugerido).
 * </p>
 *
 * <h2>Manejo de errores</h2>
 * <ul>
 * <li>No lanzar excepciones por datos de negocio o contexto incompleto; retornar
 * {@code ERROR}.</li>
 * <li>Reservar excepciones para bugs/errores inesperados (NPE por bug, etc.).</li>
 * </ul>
 */
public interface DecisionEngine {

    /**
     * Evalúa un contexto de acceso y produce una salida de decisión.
     *
     * @param ctx contexto normalizado (no null)
     * @return salida de decisión (no null)
     */
    DecisionOutput evaluate(DecisionContext ctx);
}
