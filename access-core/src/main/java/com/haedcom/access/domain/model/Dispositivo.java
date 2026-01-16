package com.haedcom.access.domain.model;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "dispositivo", uniqueConstraints = @UniqueConstraint(name = "ux_dispositivo_id_org",
                columnNames = {"id_dispositivo", "id_organizacion"}))
public class Dispositivo extends TenantAuditableEntity {

        @Id
        @Column(name = "id_dispositivo", nullable = false)
        private UUID idDispositivo;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumns({@JoinColumn(name = "id_area", referencedColumnName = "id_area",
                        nullable = false, insertable = false, updatable = false),
                        @JoinColumn(name = "id_organizacion",
                                        referencedColumnName = "id_organizacion", nullable = false,
                                        updatable = false, insertable = false)})
        private Area area;

        @Column(name = "id_area", nullable = false)
        private UUID idArea;

        @Column(name = "nombre", nullable = false, length = 100)
        private String nombre;

        @Column(name = "modelo", length = 50)
        private String modelo;

        @Column(name = "identificador_externo", length = 120, unique = true)
        private String identificadorExterno;

        @Column(name = "estado_activo", nullable = false)
        private boolean estadoActivo;

        // Getters and Setters
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

        public String getNombre() {
                return nombre;
        }

        public void setNombre(String nombre) {
                this.nombre = nombre;
        }

        public String getModelo() {
                return modelo;
        }

        public void setModelo(String modelo) {
                this.modelo = modelo;
        }

        public String getIdentificadorExterno() {
                return identificadorExterno;
        }

        public void setIdentificadorExterno(String identificadorExterno) {
                this.identificadorExterno = identificadorExterno;
        }

        public boolean isEstadoActivo() {
                return estadoActivo;
        }

        public void setEstadoActivo(boolean estadoActivo) {
                this.estadoActivo = estadoActivo;
        }

        public void setAreaReferencia(Area area) {
                if (area == null) {
                        throw new IllegalArgumentException("area no puede ser null");
                }
                this.idArea = area.getIdArea();
                this.area = area;
        }
}
