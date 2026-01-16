package com.haedcom.access.application.acceso.decision.model;

import java.util.UUID;
import com.haedcom.access.domain.enums.TipoDireccionPaso;
import com.haedcom.access.domain.enums.TipoMetodoAutenticacion;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;

/**
 * Contexto inmutable de evaluación para el motor de decisiones.
 *
 * <p>
 * Es un snapshot normalizado de datos relevantes para decidir un acceso. Está diseñado para que el
 * motor sea "puro": sin dependencia de entidades JPA ni infraestructura.
 * </p>
 *
 * <h2>Reglas de uso</h2>
 * <ul>
 * <li>{@code orgId} debe ser el tenant del request.</li>
 * <li>{@code device} debe pertenecer a {@code orgId} (validación idealmente en el servicio).</li>
 * <li>{@code idArea} representa el área en la que ocurre el intento (puede ser la del dispositivo o
 * un área destino reportada por el gateway, según tu caso).</li>
 * </ul>
 *
 * <p>
 * En versiones futuras puedes enriquecer el contexto sin romper el motor, agregando campos como:
 * <ul>
 * <li>timestamp del intento</li>
 * <li>banderas anti-passback</li>
 * <li>permisos/roles resueltos</li>
 * <li>ventanas horarias</li>
 * </ul>
 * </p>
 *
 * @param orgId tenant
 * @param idIntento id del intento
 * @param idDispositivo id del dispositivo (redundante con {@code device.idDispositivo} pero útil)
 * @param idArea área del intento (área donde se solicita acceso)
 * @param direccionPaso entrada/salida
 * @param metodoAutenticacion método reportado por el dispositivo
 * @param tipoSujeto tipo de sujeto resuelto (o {@code DESCONOCIDO})
 * @param device snapshot del dispositivo (sin JPA)
 */
public record DecisionContext(UUID orgId, UUID idIntento, UUID idDispositivo, UUID idArea,
                TipoDireccionPaso direccionPaso, TipoMetodoAutenticacion metodoAutenticacion,
                TipoSujetoAcceso tipoSujeto, DeviceSnapshot device) {

        /**
         * Validación defensiva del contexto (para integraciones).
         *
         * <p>
         * El motor puede optar por no lanzar excepciones y devolver {@code ERROR}, pero esta
         * validación es útil en tests o en la capa de aplicación antes de invocar el motor.
         * </p>
         *
         * @throws IllegalArgumentException si faltan campos críticos
         */
        public void validate() {
                if (orgId == null) {
                        throw new IllegalArgumentException("orgId es obligatorio");
                }
                if (idIntento == null) {
                        throw new IllegalArgumentException("idIntento es obligatorio");
                }
                if (idDispositivo == null) {
                        throw new IllegalArgumentException("idDispositivo es obligatorio");
                }
                if (idArea == null) {
                        throw new IllegalArgumentException("idArea es obligatorio");
                }
                if (direccionPaso == null) {
                        throw new IllegalArgumentException("direccionPaso es obligatorio");
                }
                if (metodoAutenticacion == null) {
                        throw new IllegalArgumentException("metodoAutenticacion es obligatorio");
                }
                if (device == null) {
                        throw new IllegalArgumentException("device es obligatorio");
                }
                device.validate();
        }
}
