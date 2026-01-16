package com.haedcom.access.domain.model;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "area", uniqueConstraints = {
        @UniqueConstraint(name = "ux_area_org_nombre", columnNames = {"id_organizacion", "nombre"}),
        @UniqueConstraint(name = "ux_area_id_org", columnNames = {"id_area", "id_organizacion"})})
public class Area extends TenantAuditableEntity {

    @Id
    @Column(name = "id_area", nullable = false)
    private UUID idArea;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "ruta_imagen_area")
    private String rutaImagenArea;

    @Column(name = "timezone_id", length = 50)
    private String timezoneId;

    protected Area() {}

    // Factory method
    public static Area crear(Organizacion organizacion, String nombre, String rutaImagenArea) {
        Area a = new Area();
        a.setIdArea(UUID.randomUUID());
        a.setOrganizacionTenant(organizacion);
        a.setNombre(nombre);
        a.setRutaImagenArea(rutaImagenArea);
        a.setTimezoneId(null);
        return a;
    }

    // Getters and Setters
    public UUID getIdArea() {
        return idArea;
    }

    public void setIdArea(UUID idArea) {
        if (idArea == null) {
            throw new IllegalArgumentException("idArea no puede ser null");
        }
        this.idArea = idArea;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        String v = (nombre == null) ? null : nombre.trim();
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("nombre es obligatorio");
        }
        this.nombre = v;
    }

    public String getRutaImagenArea() {
        return rutaImagenArea;
    }

    public void setRutaImagenArea(String rutaImagenArea) {
        this.rutaImagenArea = (rutaImagenArea == null) ? null : rutaImagenArea.trim();
    }

    public String getTimezoneId() {
        return timezoneId;
    }

    public void setTimezoneId(String timezoneId) {
        String v = (timezoneId == null) ? null : timezoneId.trim();
        if (v != null && v.isBlank())
            v = null;
        if (v != null && v.length() > 50)
            throw new IllegalArgumentException("timezoneId máximo 50");
        this.timezoneId = v;
    }


}
