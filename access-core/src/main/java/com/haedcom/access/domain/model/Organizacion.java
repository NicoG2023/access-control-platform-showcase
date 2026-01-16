package com.haedcom.access.domain.model;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organizacion")
public class Organizacion extends AuditableEntity {

    @Id
    @Column(name = "id_organizacion", nullable = false)
    private UUID idOrganizacion;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "timezone_id", nullable = false, length = 50)
    private String timezoneId;

    protected Organizacion() {}

    public static Organizacion crear(UUID idOrganizacion, String nombre, String estado) {
        Organizacion o = new Organizacion();
        o.setIdOrganizacion(idOrganizacion != null ? idOrganizacion : UUID.randomUUID());
        o.setNombre(nombre);
        o.setEstado(estado);
        o.setTimezoneId("UTC");
        return o;
    }

    // Getters and Setters
    public UUID getIdOrganizacion() {
        return idOrganizacion;
    }

    public void setIdOrganizacion(UUID idOrganizacion) {
        if (idOrganizacion == null) {
            throw new IllegalArgumentException("idOrganizacion no puede ser null");
        }
        this.idOrganizacion = idOrganizacion;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        String v = (nombre == null) ? null : nombre.trim();
        if (v == null || v.isBlank())
            throw new IllegalArgumentException("Nombre obligatorio");
        if (v.length() > 80)
            throw new IllegalArgumentException("Nombre máximo 80 caracteres");
        this.nombre = v;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        String v = (estado == null) ? null : estado.trim();
        if (v == null || v.isBlank())
            throw new IllegalArgumentException("Estado obligatorio");
        if (v.length() > 20)
            throw new IllegalArgumentException("Estado máximo 20 caracteres");
        this.estado = v;
    }

    public String getTimezoneId() {
        return timezoneId;
    }

    public void setTimezoneId(String timezoneId) {
        String v = (timezoneId == null) ? null : timezoneId.trim();
        if (v == null || v.isBlank())
            throw new IllegalArgumentException("timezoneId obligatorio");
        if (v.length() > 50)
            throw new IllegalArgumentException("timezoneId máximo 50");
        this.timezoneId = v;
    }
}
