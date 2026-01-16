package com.haedcom.access.application.acceso.decision.model;

import java.util.UUID;

/**
 * Snapshot inmutable de un dispositivo, usado por el motor de decisiones.
 *
 * <p>
 * Este objeto existe para mantener el motor de decisiones desacoplado de JPA/ORM y de entidades del
 * dominio. Debe contener únicamente los campos necesarios para aplicar reglas.
 * </p>
 *
 * <h2>Qué NO es</h2>
 * <ul>
 * <li>No es una entidad JPA</li>
 * <li>No tiene lazy-loading</li>
 * <li>No representa un agregado completo</li>
 * </ul>
 *
 * <h2>Cómo se usa</h2>
 * <p>
 * El {@code AccesoService} construye este snapshot a partir del {@code Dispositivo} obtenido del
 * repositorio (o de una consulta optimizada) y lo entrega al motor de decisiones.
 * </p>
 *
 * @param idDispositivo identificador del dispositivo
 * @param idOrganizacion tenant del dispositivo (para validación cruzada / auditoría)
 * @param idArea área a la cual está asociado el dispositivo (útil para reglas de consistencia)
 * @param nombre nombre del dispositivo (opcional, diagnóstico)
 * @param modelo modelo del dispositivo (opcional)
 * @param identificadorExterno identificador global externo (opcional)
 * @param estadoActivo si el dispositivo está habilitado para operar
 */
public record DeviceSnapshot(UUID idDispositivo, UUID idOrganizacion, UUID idArea, String nombre,
        String modelo, String identificadorExterno, boolean estadoActivo) {

    /**
     * Valida invariantes básicas del snapshot.
     *
     * <p>
     * Útil para pruebas y para validar integraciones (sin lanzar NPE en el motor).
     * </p>
     *
     * @throws IllegalArgumentException si faltan campos críticos
     */
    public void validate() {
        if (idDispositivo == null) {
            throw new IllegalArgumentException("idDispositivo es obligatorio");
        }
        if (idOrganizacion == null) {
            throw new IllegalArgumentException("idOrganizacion es obligatorio");
        }
        if (idArea == null) {
            throw new IllegalArgumentException("idArea es obligatorio");
        }
    }

    /**
     * Factory conveniente para crear un snapshot mínimo.
     *
     * @param idDispositivo id
     * @param idOrganizacion tenant
     * @param idArea área
     * @param estadoActivo estado
     * @return snapshot mínimo
     */
    public static DeviceSnapshot minimal(UUID idDispositivo, UUID idOrganizacion, UUID idArea,
            boolean estadoActivo) {
        return new DeviceSnapshot(idDispositivo, idOrganizacion, idArea, null, null, null,
                estadoActivo);
    }
}
