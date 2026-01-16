package com.haedcom.access.application.time;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Proveedor de zona horaria efectiva para un tenant (organización) y, opcionalmente, un área.
 *
 * <p>
 * Reglas:
 * <ul>
 * <li>Si el área tiene {@code timezoneId} (IANA) configurado, ese override gana.</li>
 * <li>Si el área no tiene override, se hereda la zona de la organización.</li>
 * <li>Si no existe el tenant (o falla la resolución por datos), se usa un fallback (por defecto
 * UTC).</li>
 * </ul>
 * </p>
 *
 * <p>
 * Este componente está pensado para ser usado por motores de decisión y lógica de "ventana diaria"
 * (HH:mm) donde necesitas convertir "ahora UTC" a "hora local del tenant/área".
 * </p>
 */
public interface TenantZoneProvider {

    /**
     * Resuelve la zona horaria efectiva para un intento que ocurre en un área.
     *
     * @param orgId tenant (obligatorio)
     * @param areaId área (puede ser null; si es null, se usa solo la zona del tenant)
     * @return zona horaria efectiva (no null)
     */
    ZoneId zoneFor(UUID orgId, UUID areaId);

    /**
     * Invalida entradas de caché asociadas a un tenant (útil al actualizar la organización).
     *
     * @param orgId tenant (obligatorio)
     */
    void invalidateOrg(UUID orgId);

    /**
     * Invalida entradas de caché asociadas a un área (útil al actualizar la zona del área).
     *
     * @param orgId tenant (obligatorio)
     * @param areaId área (obligatorio)
     */
    void invalidateArea(UUID orgId, UUID areaId);
}
