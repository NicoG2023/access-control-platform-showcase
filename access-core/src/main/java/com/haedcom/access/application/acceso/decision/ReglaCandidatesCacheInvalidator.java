package com.haedcom.access.application.acceso.decision;

import java.util.UUID;
import org.jboss.logging.Logger;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Invalidator centralizado del cache {@code regla-candidates}.
 *
 * <p>
 * Este componente encapsula TODA la lógica de invalidación del cache de reglas candidatas, evitando
 * que los consumers o servicios conozcan detalles de {@link io.quarkus.cache.Cache}.
 * </p>
 *
 * <h2>Contrato de la key</h2>
 * <p>
 * El cache {@code regla-candidates} debe estar definido con un método anotado con
 * {@code @CacheResult} cuya firma sea exactamente:
 * </p>
 *
 * <pre>
 * (UUID orgId, UUID areaId, TipoSujetoAcceso tipoSujeto)
 * </pre>
 *
 * <p>
 * Cualquier cambio en ese orden o tipos rompe la invalidación.
 * </p>
 *
 * <h2>Uso típico</h2>
 * <ul>
 * <li>Eventos {@code ReglaAccesoPolicyChanged} →
 * {@link #invalidate(UUID, UUID, TipoSujetoAcceso)}</li>
 * <li>Eventos {@code ReglaAccesoPolicyInvalidateAllRequested} → {@link #invalidateAll()}</li>
 * </ul>
 *
 * <p>
 * Idempotente: invalidar una entrada inexistente no produce error.
 * </p>
 */
@ApplicationScoped
public class ReglaCandidatesCacheInvalidator {

    private static final Logger LOG = Logger.getLogger(ReglaCandidatesCacheInvalidator.class);

    /**
     * Invalida una entrada específica del cache {@code regla-candidates}.
     *
     * <p>
     * La invalidación se realiza por la anotación {@link CacheInvalidate}; el cuerpo del método es
     * intencionalmente un NO-OP.
     * </p>
     *
     * @param orgId tenant (obligatorio)
     * @param areaId área (obligatorio)
     * @param tipoSujeto tipo de sujeto (obligatorio)
     */
    @CacheInvalidate(cacheName = "regla-candidates")
    public void invalidate(UUID orgId, UUID areaId, TipoSujetoAcceso tipoSujeto) {
        LOG.debugf("Invalidating regla-candidates orgId=%s areaId=%s tipoSujeto=%s", orgId, areaId,
                tipoSujeto);
    }

    /**
     * Invalida TODO el cache {@code regla-candidates} del nodo local.
     *
     * <p>
     * Con Caffeine (cache local), esta operación se ejecuta en cada nodo del cluster gracias a
     * Kafka + Outbox, logrando una invalidación distribuida efectiva.
     * </p>
     *
     * <p>
     * Usar solo para:
     * </p>
     * <ul>
     * <li>migraciones</li>
     * <li>cambios masivos de política</li>
     * <li>operaciones administrativas explícitas</li>
     * </ul>
     */
    @CacheInvalidateAll(cacheName = "regla-candidates")
    public void invalidateAll() {
        LOG.warn("Invalidating ALL regla-candidates cache entries");
    }
}
