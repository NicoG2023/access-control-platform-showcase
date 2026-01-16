package com.haedcom.access.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoComandoDispositivo;
import com.haedcom.access.domain.enums.TipoComandoDispositivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Comando persistido para ejecución sobre un dispositivo físico (multi-tenant).
 *
 * <p>
 * Representa una instrucción emitida por el core (ej. abrir puerta, negar acceso, mostrar mensaje),
 * que será ejecutada por el microservicio de dispositivos y luego confirmada de vuelta al core.
 * </p>
 *
 * <h2>Idempotencia</h2>
 * <p>
 * Esta entidad soporta idempotencia en dos niveles:
 * </p>
 * <ul>
 * <li><b>Interna</b> (core): mediante el guard de estado final (no se reprocesa si ya está
 * final).</li>
 * <li><b>De emisión</b>: constraint UNIQUE por {@code (id_organizacion, clave_idempotencia)} para
 * evitar duplicados al crear/emitir el comando.</li>
 * </ul>
 *
 * <h2>Trazabilidad externa</h2>
 * <p>
 * {@code idEjecucionExterna} permite correlacionar este comando con el identificador asignado por
 * el microservicio de dispositivos (o por el dispositivo/proveedor). Esto es útil para:
 * </p>
 * <ul>
 * <li>Correlación de logs y troubleshooting</li>
 * <li>Idempotencia “externa” (cuando el mismo resultado llega repetido con el mismo id
 * externo)</li>
 * </ul>
 */
@Entity
@Table(name = "comando_dispositivo",
                uniqueConstraints = @UniqueConstraint(name = "ux_comando_idempotencia_org",
                                columnNames = {"id_organizacion", "clave_idempotencia"}))
public class ComandoDispositivo extends TenantOnlyEntity {

        @Id
        @Column(name = "id_comando", nullable = false)
        private UUID idComando;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "id_intento", nullable = false)
        private IntentoAcceso intento;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumns({@JoinColumn(name = "id_dispositivo", referencedColumnName = "id_dispositivo",
                        nullable = false, insertable = false, updatable = false),
                        @JoinColumn(name = "id_organizacion",
                                        referencedColumnName = "id_organizacion", nullable = false,
                                        insertable = false, updatable = false)})
        private Dispositivo dispositivo;

        @Column(name = "id_dispositivo", nullable = false)
        private UUID idDispositivo;

        @Enumerated(EnumType.STRING)
        @Column(name = "comando", nullable = false)
        private TipoComandoDispositivo comando;

        @Column(name = "mensaje", length = 120)
        private String mensaje;

        @Enumerated(EnumType.STRING)
        @Column(name = "estado", nullable = false)
        private EstadoComandoDispositivo estado;

        @Column(name = "enviado_en_utc", nullable = false)
        private OffsetDateTime enviadoEnUtc;

        @Column(name = "confirmado_en_utc")
        private OffsetDateTime confirmadoEnUtc;

        @Column(name = "codigo_error", length = 60)
        private String codigoError;

        @Column(name = "detalle_error", length = 250)
        private String detalleError;

        @Column(name = "clave_idempotencia", nullable = false, length = 200)
        private String claveIdempotencia;

        /**
         * Identificador/correlación asignado por el microservicio de dispositivos (o por el
         * dispositivo).
         *
         * <p>
         * Campo opcional. Se recomienda persistirlo cuando el callback de resultado lo provea, para
         * facilitar trazabilidad y correlación extremo a extremo.
         * </p>
         */
        @Column(name = "id_ejecucion_externa", length = 120)
        private String idEjecucionExterna;

        // ---------------------------------------------------------------------
        // Getters / Setters
        // ---------------------------------------------------------------------

        public UUID getIdComando() {
                return idComando;
        }

        public void setIdComando(UUID idComando) {
                this.idComando = idComando;
        }

        public IntentoAcceso getIntento() {
                return intento;
        }

        public void setIntento(IntentoAcceso intento) {
                this.intento = intento;
        }

        public Dispositivo getDispositivo() {
                return dispositivo;
        }

        public void setDispositivo(Dispositivo dispositivo) {
                this.dispositivo = dispositivo;
        }

        public UUID getIdDispositivo() {
                return idDispositivo;
        }

        public void setIdDispositivo(UUID idDispositivo) {
                this.idDispositivo = idDispositivo;
        }

        public TipoComandoDispositivo getComando() {
                return comando;
        }

        public void setComando(TipoComandoDispositivo comando) {
                this.comando = comando;
        }

        public String getMensaje() {
                return mensaje;
        }

        public void setMensaje(String mensaje) {
                this.mensaje = mensaje;
        }

        public EstadoComandoDispositivo getEstado() {
                return estado;
        }

        public void setEstado(EstadoComandoDispositivo estado) {
                this.estado = estado;
        }

        public OffsetDateTime getEnviadoEnUtc() {
                return enviadoEnUtc;
        }

        public void setEnviadoEnUtc(OffsetDateTime enviadoEnUtc) {
                this.enviadoEnUtc = enviadoEnUtc;
        }

        public OffsetDateTime getConfirmadoEnUtc() {
                return confirmadoEnUtc;
        }

        public void setConfirmadoEnUtc(OffsetDateTime confirmadoEnUtc) {
                this.confirmadoEnUtc = confirmadoEnUtc;
        }

        public String getCodigoError() {
                return codigoError;
        }

        public void setCodigoError(String codigoError) {
                this.codigoError = codigoError;
        }

        public String getDetalleError() {
                return detalleError;
        }

        public void setDetalleError(String detalleError) {
                this.detalleError = detalleError;
        }

        public String getClaveIdempotencia() {
                return claveIdempotencia;
        }

        public void setClaveIdempotencia(String claveIdempotencia) {
                this.claveIdempotencia = claveIdempotencia;
        }

        public String getIdEjecucionExterna() {
                return idEjecucionExterna;
        }

        public void setIdEjecucionExterna(String idEjecucionExterna) {
                this.idEjecucionExterna = idEjecucionExterna;
        }
}
