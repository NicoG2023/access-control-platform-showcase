package com.haedcom.access.domain.events;

import com.haedcom.access.domain.model.OutboxEvent;

/**
 * Puerto para publicar eventos Outbox hacia sistemas externos (Kafka, HTTP, AMQP, etc.).
 */
public interface OutboxEventSender {

    /**
     * Envía el evento serializado a un sistema externo.
     *
     * <p>
     * Si ocurre un error, debe lanzar {@link OutboxSendException} indicando si el error es
     * reintentable o definitivo.
     * </p>
     *
     * @param event evento de outbox
     * @throws OutboxSendException si la publicación falla
     */
    void send(OutboxEvent event) throws OutboxSendException;
}
