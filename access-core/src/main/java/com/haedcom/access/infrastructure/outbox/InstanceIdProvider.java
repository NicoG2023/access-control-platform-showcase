package com.haedcom.access.infrastructure.outbox;

import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Proveedor del identificador estable de instancia.
 *
 * <p>
 * Se usa para {@code locked_by} en Outbox y para trazabilidad en logs/métricas. Si
 * {@code haedcom.instance-id} no está configurado, se genera un UUID al iniciar.
 * </p>
 */
@ApplicationScoped
public class InstanceIdProvider {

    private final String instanceId;

    public InstanceIdProvider(
            @ConfigProperty(name = "haedcom.instance-id", defaultValue = "") String configured) {

        this.instanceId = (configured != null && !configured.isBlank()) ? configured
                : UUID.randomUUID().toString();
    }

    /**
     * @return identificador no vacío de esta instancia
     */
    public String get() {
        return instanceId;
    }
}
