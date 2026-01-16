package com.haedcom.access.domain.model;

import java.util.UUID;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import com.haedcom.access.domain.enums.TipoRolEnVisita;
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

@Entity
@Table(name = "persona_visita")
public class PersonaVisita extends TenantAuditableEntity {

    @Id
    @Column(name = "id_persona_visita", nullable = false)
    private UUID idPersonaVisita;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "id_visita", referencedColumnName = "id_visita", nullable = false,
                    insertable = false, updatable = false),
            @JoinColumn(name = "id_organizacion", referencedColumnName = "id_organizacion",
                    nullable = false, insertable = false, updatable = false)})
    private Visita visita;

    @Column(name = "id_visita", nullable = false)
    private UUID idVisita;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_en_visita", nullable = false)
    private TipoRolEnVisita rolEnVisita;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento")
    private TipoDocumentoIdentidad tipoDocumento;

    @Column(name = "numero_documento", length = 30)
    private String numeroDocumento;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "correo", length = 200)
    private String correo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sujeto_vinculado")
    private TipoSujetoAcceso tipoSujetoVinculado;

    @Column(name = "id_sujeto_vinculado")
    private UUID idSujetoVinculado;

    // Getters and Setters
    public UUID getIdPersonaVisita() {
        return idPersonaVisita;
    }

    public void setIdPersonaVisita(UUID idPersonaVisita) {
        this.idPersonaVisita = idPersonaVisita;
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

    public TipoRolEnVisita getRolEnVisita() {
        return rolEnVisita;
    }

    public void setRolEnVisita(TipoRolEnVisita rolEnVisita) {
        this.rolEnVisita = rolEnVisita;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public TipoDocumentoIdentidad getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(TipoDocumentoIdentidad tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public TipoSujetoAcceso getTipoSujetoVinculado() {
        return tipoSujetoVinculado;
    }

    public void setTipoSujetoVinculado(TipoSujetoAcceso tipoSujetoVinculado) {
        this.tipoSujetoVinculado = tipoSujetoVinculado;
    }

    public UUID getIdSujetoVinculado() {
        return idSujetoVinculado;
    }

    public void setIdSujetoVinculado(UUID idSujetoVinculado) {
        this.idSujetoVinculado = idSujetoVinculado;
    }
}
