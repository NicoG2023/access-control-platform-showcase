package com.haedcom.access.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.haedcom.access.domain.enums.TipoDireccionPaso;
import com.haedcom.access.domain.enums.TipoMetodoAutenticacion;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
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

@Entity
@Table(name = "intento_acceso",
                uniqueConstraints = @UniqueConstraint(name = "ux_intento_idempotencia_org",
                                columnNames = {"id_organizacion", "clave_idempotencia"}))
public class IntentoAcceso extends TenantCreatedEntity {

        @Id
        @Column(name = "id_intento", nullable = false)
        private UUID idIntento;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumns({@JoinColumn(name = "id_dispositivo", referencedColumnName = "id_dispositivo",
                        nullable = false, insertable = false, updatable = false),
                        @JoinColumn(name = "id_organizacion",
                                        referencedColumnName = "id_organizacion", nullable = false,
                                        insertable = false, updatable = false)})
        private Dispositivo dispositivo;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumns({@JoinColumn(name = "id_area", referencedColumnName = "id_area",
                        nullable = false, insertable = false, updatable = false),
                        @JoinColumn(name = "id_organizacion",
                                        referencedColumnName = "id_organizacion", nullable = false,
                                        insertable = false, updatable = false)})
        private Area area;

        @ManyToOne(fetch = FetchType.LAZY, optional = true)
        @JoinColumns({@JoinColumn(name = "id_visita", referencedColumnName = "id_visita",
                        nullable = true, insertable = false, updatable = false),
                        @JoinColumn(name = "id_organizacion",
                                        referencedColumnName = "id_organizacion", nullable = true,
                                        insertable = false, updatable = false)})
        private Visita visita;


        @ManyToOne(fetch = FetchType.LAZY, optional = true)
        @JoinColumns({@JoinColumn(name = "id_persona_visita",
                        referencedColumnName = "id_persona_visita", nullable = true,
                        insertable = false, updatable = false),
                        @JoinColumn(name = "id_organizacion",
                                        referencedColumnName = "id_organizacion", nullable = true,
                                        insertable = false, updatable = false)})
        private PersonaVisita personaVisita;


        @Column(name = "id_dispositivo", nullable = false)
        private UUID idDispositivo;

        @Column(name = "id_area", nullable = false)
        private UUID idArea;

        @Column(name = "id_visita")
        private UUID idVisita;

        @Column(name = "id_persona_visita")
        private UUID idPersonaVisita;

        @Enumerated(EnumType.STRING)
        @Column(name = "direccion_paso", nullable = false)
        private TipoDireccionPaso direccionPaso;

        @Enumerated(EnumType.STRING)
        @Column(name = "metodo_autenticacion", nullable = false)
        private TipoMetodoAutenticacion metodoAutenticacion;

        @Enumerated(EnumType.STRING)
        @Column(name = "tipo_sujeto", nullable = false)
        private TipoSujetoAcceso tipoSujeto;

        @Column(name = "referencia_credencial", length = 200)
        private String referenciaCredencial;

        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "carga_cruda", columnDefinition = "jsonb")
        private com.fasterxml.jackson.databind.JsonNode cargaCruda;

        @Column(name = "clave_idempotencia", nullable = false, length = 200)
        private String claveIdempotencia;

        @Column(name = "id_gateway_solicitud", length = 120)
        private String idGatewaySolicitud;

        @Column(name = "ocurrido_en_utc", nullable = false)
        private OffsetDateTime ocurridoEnUtc;

        // Getters and Setters
        public UUID getIdIntento() {
                return idIntento;
        }

        public void setIdIntento(UUID idIntento) {
                this.idIntento = idIntento;
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

        public Area getArea() {
                return area;
        }

        public void setArea(Area area) {
                this.area = area;
        }

        public UUID getIdArea() {
                return idArea;
        }

        public void setIdArea(UUID idArea) {
                this.idArea = idArea;
        }

        public Visita getVisita() {
                return visita;
        }

        public void setVisita(Visita visita) {
                this.visita = visita;
        }

        public UUID getIdVisita() {
                return idVisita;
        }

        public void setIdVisita(UUID idVisita) {
                this.idVisita = idVisita;
        }

        public PersonaVisita getPersonaVisita() {
                return personaVisita;
        }

        public void setPersonaVisita(PersonaVisita personaVisita) {
                this.personaVisita = personaVisita;
        }

        public UUID getIdPersonaVisita() {
                return idPersonaVisita;
        }

        public void setIdPersonaVisita(UUID idPersonaVisita) {
                this.idPersonaVisita = idPersonaVisita;
        }

        public TipoDireccionPaso getDireccionPaso() {
                return direccionPaso;
        }

        public void setDireccionPaso(TipoDireccionPaso direccionPaso) {
                this.direccionPaso = direccionPaso;
        }

        public TipoMetodoAutenticacion getMetodoAutenticacion() {
                return metodoAutenticacion;
        }

        public void setMetodoAutenticacion(TipoMetodoAutenticacion metodoAutenticacion) {
                this.metodoAutenticacion = metodoAutenticacion;
        }

        public TipoSujetoAcceso getTipoSujeto() {
                return tipoSujeto;
        }

        public void setTipoSujeto(TipoSujetoAcceso tipoSujeto) {
                this.tipoSujeto = tipoSujeto;
        }

        public String getReferenciaCredencial() {
                return referenciaCredencial;
        }

        public void setReferenciaCredencial(String referenciaCredencial) {
                this.referenciaCredencial = referenciaCredencial;
        }

        public com.fasterxml.jackson.databind.JsonNode getCargaCruda() {
                return cargaCruda;
        }

        public void setCargaCruda(com.fasterxml.jackson.databind.JsonNode cargaCruda) {
                this.cargaCruda = cargaCruda;
        }

        public String getClaveIdempotencia() {
                return claveIdempotencia;
        }

        public void setClaveIdempotencia(String claveIdempotencia) {
                this.claveIdempotencia = claveIdempotencia;
        }

        public String getIdGatewaySolicitud() {
                return idGatewaySolicitud;
        }

        public void setIdGatewaySolicitud(String idGatewaySolicitud) {
                this.idGatewaySolicitud = idGatewaySolicitud;
        }

        public OffsetDateTime getOcurridoEnUtc() {
                return ocurridoEnUtc;
        }

        public void setOcurridoEnUtc(OffsetDateTime ocurridoEnUtc) {
                this.ocurridoEnUtc = ocurridoEnUtc;
        }
}
