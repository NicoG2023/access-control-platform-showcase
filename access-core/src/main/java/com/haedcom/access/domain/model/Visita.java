package com.haedcom.access.domain.model;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;

@Entity
public class Visita extends TenantAuditableEntity {

        @Id
        @Column(name = "id_visita", nullable = false)
        private UUID idVisita;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumns({@JoinColumn(name = "id_area_destino", referencedColumnName = "id_area",
                        nullable = false, insertable = false, updatable = false),
                        @JoinColumn(name = "id_organizacion",
                                        referencedColumnName = "id_organizacion", nullable = false,
                                        insertable = false, updatable = false)})
        private Area areaDestino;

        @Column(name = "id_area_destino", nullable = false)
        private UUID idAreaDestino;

        @Column(name = "motivo", length = 120)
        private String motivo;

        // Getters and Setters
        public UUID getIdVisita() {
                return idVisita;
        }

        public void setIdVisita(UUID idVisita) {
                if (idVisita == null) {
                        throw new IllegalArgumentException("idVisita no puede ser null");
                }
                this.idVisita = idVisita;
        }

        public Area getAreaDestino() {
                return areaDestino;
        }

        public void setAreaDestino(Area areaDestino) {
                this.areaDestino = areaDestino;
        }

        public UUID getIdAreaDestino() {
                return idAreaDestino;
        }

        public void setIdAreaDestino(UUID idAreaDestino) {
                this.idAreaDestino = idAreaDestino;
        }

        public String getMotivo() {
                return motivo;
        }

        public void setMotivo(String motivo) {
                String v = (motivo == null) ? null : motivo.trim();
                if (v != null && v.length() > 120) {
                        throw new IllegalArgumentException("Motivo excede longitud máxima");
                }
                this.motivo = v;
        }

        public void setAreaDestinoReferencia(Area area) {
                if (area == null) {
                        throw new IllegalArgumentException("areaDestino no puede ser null");
                }
                this.idAreaDestino = area.getIdArea(); // Usando el getter
                this.areaDestino = area;
        }
}
