package com.haedcom.access.domain.enums;

public enum EstadoComandoDispositivo {

    /** Persistido en el core */
    CREADO,

    /** Publicado al bus / enviado al microservicio */
    ENVIADO,

    /** El gateway/microservicio confirmó recepción */
    RECIBIDO,

    /** El dispositivo ejecutó el comando correctamente */
    EJECUTADO_OK,

    /** El dispositivo ejecutó pero falló (puerta trabada, etc.) */
    EJECUTADO_ERROR,

    /** No hubo respuesta en el tiempo esperado */
    TIMEOUT
}
