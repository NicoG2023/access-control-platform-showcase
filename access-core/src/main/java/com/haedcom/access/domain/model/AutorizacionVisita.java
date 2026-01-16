package com.haedcom.access.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "autorizacion_visita")
public class AutorizacionVisita extends TenantAuditableEntity {

  @Id
  @Column(name = "id_autorizacion", nullable = false)
  private UUID idAutorizacion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "id_visitante", nullable = false)
  private VisitantePreautorizado visitante;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumns({
      @JoinColumn(name = "id_area", referencedColumnName = "id_area", insertable = false,
          updatable = false),
      @JoinColumn(name = "id_organizacion", referencedColumnName = "id_organizacion",
          insertable = false, updatable = false)})
  private Area area;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "id_regla_acceso", nullable = true)
  private ReglaAcceso reglaAcceso;

  @Column(name = "id_area")
  private UUID idArea;

  @Column(name = "valido_desde_utc", nullable = false)
  private OffsetDateTime validoDesdeUtc;

  @Column(name = "valido_hasta_utc", nullable = false)
  private OffsetDateTime validoHastaUtc;

  @Column(name = "limite_ingresos")
  private Integer limiteIngresos;

  // Getters and Setters
  public UUID getIdAutorizacion() {
    return idAutorizacion;
  }

  public void setIdAutorizacion(UUID idAutorizacion) {
    this.idAutorizacion = idAutorizacion;
  }

  public VisitantePreautorizado getVisitante() {
    return visitante;
  }

  public void setVisitante(VisitantePreautorizado visitante) {
    this.visitante = visitante;
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

  public OffsetDateTime getValidoDesdeUtc() {
    return validoDesdeUtc;
  }

  public void setValidoDesdeUtc(OffsetDateTime validoDesdeUtc) {
    this.validoDesdeUtc = validoDesdeUtc;
  }

  public OffsetDateTime getValidoHastaUtc() {
    return validoHastaUtc;
  }

  public void setValidoHastaUtc(OffsetDateTime validoHastaUtc) {
    this.validoHastaUtc = validoHastaUtc;
  }

  public Integer getLimiteIngresos() {
    return limiteIngresos;
  }

  public void setLimiteIngresos(Integer limiteIngresos) {
    this.limiteIngresos = limiteIngresos;
  }

  public void setAreaReferencia(Area area) {
    if (area != null) {
      this.idArea = area.getIdArea();
      this.area = area;
    }
  }
}
