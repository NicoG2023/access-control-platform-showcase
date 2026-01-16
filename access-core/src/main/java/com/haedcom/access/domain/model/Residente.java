package com.haedcom.access.domain.model;

import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoResidente;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "residente", uniqueConstraints = @UniqueConstraint(name = "ux_residente_doc",
        columnNames = {"id_organizacion", "tipo_documento", "numero_documento"}))
public class Residente extends TenantAuditableEntity {

    @Id
    @Column(name = "id_residente", nullable = false)
    private UUID idResidente;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false)
    private TipoDocumentoIdentidad tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 30)
    private String numeroDocumento;

    @Column(name = "correo", length = 200)
    private String correo;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "domicilio", length = 255)
    private String domicilio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoResidente estado;

    protected Residente() {}

    public static Residente crear(Organizacion organizacion, String nombre,
            TipoDocumentoIdentidad tipoDocumento, String numeroDocumento, String correo,
            String telefono, String domicilio) {
        Residente r = new Residente();
        r.setIdResidente(UUID.randomUUID());

        r.setOrganizacionTenant(organizacion);

        r.actualizarDatos(nombre, tipoDocumento, numeroDocumento, correo, telefono, domicilio);
        return r;
    }

    public void actualizarDatos(String nombre, TipoDocumentoIdentidad tipoDocumento,
            String numeroDocumento, String correo, String telefono, String domicilio) {
        setNombre(nombre);
        setTipoDocumento(tipoDocumento);
        setNumeroDocumento(numeroDocumento);
        setCorreo(correo);
        setTelefono(telefono);
        setDomicilio(domicilio);
    }

    public UUID getIdResidente() {
        return idResidente;
    }

    public void setIdResidente(UUID idResidente) {
        if (idResidente == null)
            throw new IllegalArgumentException("idResidente no puede ser null");
        this.idResidente = idResidente;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        String v = (nombre == null) ? null : nombre.trim();
        if (v == null || v.isBlank())
            throw new IllegalArgumentException("nombre es obligatorio");
        this.nombre = v;
    }

    public TipoDocumentoIdentidad getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(TipoDocumentoIdentidad tipoDocumento) {
        if (tipoDocumento == null)
            throw new IllegalArgumentException("tipoDocumento es obligatorio");
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        String v = (numeroDocumento == null) ? null : numeroDocumento.trim();
        if (v == null || v.isBlank())
            throw new IllegalArgumentException("numeroDocumento es obligatorio");
        if (v.length() > 30)
            throw new IllegalArgumentException("numeroDocumento máximo 30 caracteres");
        this.numeroDocumento = v;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        String v = (correo == null) ? null : correo.trim();
        if (v != null && v.length() > 200)
            throw new IllegalArgumentException("correo máximo 200 caracteres");
        this.correo = v;
    }


    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        String v = (telefono == null) ? null : telefono.trim();
        if (v != null && v.length() > 30)
            throw new IllegalArgumentException("telefono máximo 30 caracteres");
        this.telefono = v;
    }


    public String getDomicilio() {
        return domicilio;
    }

    public void setDomicilio(String domicilio) {
        String v = (domicilio == null) ? null : domicilio.trim();
        if (v != null && v.length() > 255)
            throw new IllegalArgumentException("domicilio máximo 255 caracteres");
        this.domicilio = v;
    }

    public EstadoResidente getEstado() {
        return estado;
    }

    public void setEstado(EstadoResidente estado) {
        if (estado == null)
            throw new IllegalArgumentException("estado es obligatorio");
        this.estado = estado;
    }

    @PrePersist
    void ensureDefaults() {
        if (idResidente == null) {
            idResidente = UUID.randomUUID();
        }
        if (estado == null) {
            estado = EstadoResidente.ACTIVO;
        }
    }
}
