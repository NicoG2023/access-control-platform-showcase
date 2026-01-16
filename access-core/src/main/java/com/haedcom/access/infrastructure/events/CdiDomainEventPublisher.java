package com.haedcom.access.infrastructure.events;

import com.haedcom.access.domain.events.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Publicador de eventos usando CDI {@link Event}.
 *
 * <p>
 * Útil para reaccionar dentro del monolito (auditoría, métricas, proyecciones, etc.).
 * </p>
 */
@ApplicationScoped
@InProcess
public class CdiDomainEventPublisher implements DomainEventPublisher {

    @Inject
    Event<Object> cdiEvent;

    @Override
    public void publish(Object event) {
        cdiEvent.fire(DomainEventPublisher.requireEvent(event));
    }
}
