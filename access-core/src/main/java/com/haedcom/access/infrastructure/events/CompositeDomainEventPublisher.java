package com.haedcom.access.infrastructure.events;

import java.util.Objects;
import com.haedcom.access.domain.events.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;

/**
 * Publicador compuesto: dispara eventos in-process (CDI) y además los persiste en Outbox.
 *
 * <p>
 * Úsalo cuando necesitas:
 * <ul>
 * <li>Reacciones internas inmediatas (auditoría, métricas, proyecciones) vía CDI</li>
 * <li>Entrega confiable a externos vía Transactional Outbox</li>
 * </ul>
 * </p>
 */
@ApplicationScoped
@Default
public class CompositeDomainEventPublisher implements DomainEventPublisher {

    private final CdiDomainEventPublisher cdiPublisher;
    private final DomainEventPublisher outboxPublisher;

    @Inject
    public CompositeDomainEventPublisher(@InProcess CdiDomainEventPublisher cdiPublisher,
            @Outbox DomainEventPublisher outboxPublisher) {
        this.cdiPublisher = Objects.requireNonNull(cdiPublisher, "cdiPublisher es obligatorio");
        this.outboxPublisher =
                Objects.requireNonNull(outboxPublisher, "outboxPublisher es obligatorio");
    }

    @Override
    public void publish(Object event) {
        // 1) Interno (auditoría) - best effort: NO rompe caso de uso si falla auditoría
        try {
            cdiPublisher.publish(event);
        } catch (Exception ignored) {
            // intencional: auditoría nivel B ya es tolerante a fallos
        }

        // 2) Externo (integración) - confiable: SI falla, debe romper la transacción
        outboxPublisher.publish(event);
    }
}
