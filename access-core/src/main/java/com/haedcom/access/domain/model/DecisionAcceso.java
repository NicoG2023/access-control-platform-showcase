package com.haedcom.access.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.TipoResultadoDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "decision_acceso")
public class DecisionAcceso extends TenantOnlyEntity {

    @Id
    @Column(name = "id_decision", nullable = false)
    private UUID idDecision;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_intento", nullable = false, unique = true)
    private IntentoAcceso intento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_regla_acceso", nullable = true)
    private ReglaAcceso reglaAcceso;

    @Column(name = "decidido_en_utc", nullable = false)
    private OffsetDateTime decididoEnUtc;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false)
    private TipoResultadoDecision resultado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "codigo_motivo", nullable = false)
    private CatalogoMotivoDecision motivo;

    @Column(name = "detalle_motivo", length = 250)
    private String detalleMotivo;

    @Column(name = "version_politica", length = 40)
    private String versionPolitica;

    @Column(name = "decidido_por_usuario")
    private UUID decididoPorUsuario;

    @Column(name = "expira_en_utc")
    private OffsetDateTime expiraEnUtc;

    // Getters and Setters
    public UUID getIdDecision() {
        return idDecision;
    }

    public void setIdDecision(UUID idDecision) {
        this.idDecision = idDecision;
    }

    public IntentoAcceso getIntento() {
        return intento;
    }

    public void setIntento(IntentoAcceso intento) {
        this.intento = intento;
    }

    public OffsetDateTime getDecididoEnUtc() {
        return decididoEnUtc;
    }

    public void setDecididoEnUtc(OffsetDateTime decididoEnUtc) {
        this.decididoEnUtc = decididoEnUtc;
    }

    public TipoResultadoDecision getResultado() {
        return resultado;
    }

    public void setResultado(TipoResultadoDecision resultado) {
        this.resultado = resultado;
    }

    public CatalogoMotivoDecision getMotivo() {
        return motivo;
    }

    public void setMotivo(CatalogoMotivoDecision motivo) {
        this.motivo = motivo;
    }

    public String getDetalleMotivo() {
        return detalleMotivo;
    }

    public void setDetalleMotivo(String detalleMotivo) {
        this.detalleMotivo = detalleMotivo;
    }

    public String getVersionPolitica() {
        return versionPolitica;
    }

    public void setVersionPolitica(String versionPolitica) {
        this.versionPolitica = versionPolitica;
    }

    public UUID getDecididoPorUsuario() {
        return decididoPorUsuario;
    }

    public void setDecididoPorUsuario(UUID decididoPorUsuario) {
        this.decididoPorUsuario = decididoPorUsuario;
    }

    public OffsetDateTime getExpiraEnUtc() {
        return expiraEnUtc;
    }

    public void setExpiraEnUtc(OffsetDateTime expiraEnUtc) {
        this.expiraEnUtc = expiraEnUtc;
    }
}
