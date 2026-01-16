package com.haedcom.access.application.time;

import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.jboss.logging.Logger;
import com.haedcom.access.domain.model.TimeZoneValidator;
import com.haedcom.access.domain.repo.AreaRepository;
import com.haedcom.access.domain.repo.OrganizacionRepository;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Implementación de {@link TenantZoneProvider} respaldada por base de datos con caché.
 *
 * <h2>Cachés</h2>
 * <ul>
 * <li><b>tenant-zone-org</b>: resuelve {@code ZoneId} por {@code orgId}.</li>
 * <li><b>tenant-zone-area</b>: resuelve {@code ZoneId} efectiva por par
 * {@code (orgId, areaId)}.</li>
 * </ul>
 *
 * <p>
 * Las entradas cacheadas devuelven {@link ZoneId} ya validado. Se recomienda configurar TTL/size en
 * {@code application.properties}.
 * </p>
 */
@ApplicationScoped
public class DbTenantZoneProvider implements TenantZoneProvider {

    private static final Logger LOG = Logger.getLogger(DbTenantZoneProvider.class);

    private final OrganizacionRepository orgRepo;
    private final AreaRepository areaRepo;

    /** Fallback seguro si hay datos inconsistentes o tenant inexistente. */
    private final ZoneId fallback = ZoneId.of("UTC");

    public DbTenantZoneProvider(OrganizacionRepository orgRepo, AreaRepository areaRepo) {
        this.orgRepo = Objects.requireNonNull(orgRepo, "orgRepo es obligatorio");
        this.areaRepo = Objects.requireNonNull(areaRepo, "areaRepo es obligatorio");
    }

    @Override
    public ZoneId zoneFor(UUID orgId, UUID areaId) {
        Objects.requireNonNull(orgId, "orgId es obligatorio");
        if (areaId == null) {
            return zoneForOrg(orgId);
        }
        return zoneForArea(orgId, areaId);
    }

    /**
     * Resuelve zona del tenant (organización) con caché.
     */
    @CacheResult(cacheName = "tenant-zone-org")
    ZoneId zoneForOrg(UUID orgId) {
        // timezoneId en organizacion es NOT NULL (por diseño), pero defensivo:
        String tz = orgRepo.findTimezoneId(orgId).orElse(null);

        String normalized = TimeZoneValidator.normalizeAndValidateIana(tz);
        if (normalized == null) {
            LOG.warnf("Organizacion %s sin timezoneId válido. Usando fallback=%s", orgId, fallback);
            return fallback;
        }
        return ZoneId.of(normalized);
    }

    /**
     * Resuelve zona efectiva por área con caché:
     * <ul>
     * <li>si área tiene override válido, gana</li>
     * <li>si no, hereda org</li>
     * </ul>
     */
    @CacheResult(cacheName = "tenant-zone-area")
    ZoneId zoneForArea(UUID orgId, UUID areaId) {
        // Nota: si el área no existe o no pertenece al tenant, esto retorna null.
        String areaTz = areaRepo.findTimezoneIdOrNull(orgId, areaId);

        String normalizedArea = TimeZoneValidator.normalizeAndValidateIana(areaTz);
        if (normalizedArea != null) {
            return ZoneId.of(normalizedArea);
        }

        // heredar tenant
        return zoneForOrg(orgId);
    }

    @Override
    @CacheInvalidate(cacheName = "tenant-zone-org")
    public void invalidateOrg(UUID orgId) {
        // invalidación por key = orgId
        LOG.debugf("Invalidating cache tenant-zone-org for orgId=%s", orgId);
    }

    @Override
    @CacheInvalidate(cacheName = "tenant-zone-area")
    public void invalidateArea(UUID orgId, UUID areaId) {
        // invalidación por key = (orgId, areaId) según firma del método cacheado
        LOG.debugf("Invalidating cache tenant-zone-area for orgId=%s areaId=%s", orgId, areaId);
    }
}
