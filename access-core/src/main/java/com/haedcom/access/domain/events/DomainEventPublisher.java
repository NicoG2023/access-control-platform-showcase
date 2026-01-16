package com.haedcom.access.domain.events;

import java.util.Objects;

/**
 * Puerto de publicación de eventos de dominio.
 *
 * <p>
 * Permite desacoplar la capa de aplicación del mecanismo de entrega:
 * <ul>
 * <li>In-process (CDI events) para listeners dentro del monolito</li>
 * <li>Outbox + Kafka (Reactive Messaging) para integración con microservicios</li>
 * </ul>
 * </p>
 */
public interface DomainEventPublisher {

    /**
     * Publica un evento de dominio.
     *
     * @param event evento (no null)
     */
    void publish(Object event);

    static Object requireEvent(Object event) {
        return Objects.requireNonNull(event, "event es obligatorio");
    }
}
