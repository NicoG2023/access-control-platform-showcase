package com.haedcom.access.domain.model;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoReglaAcceso;
import com.haedcom.access.domain.enums.TipoAccionAcceso;
import com.haedcom.access.domain.enums.TipoDireccionPaso;
import com.haedcom.access.domain.enums.TipoMetodoAutenticacion;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Regla configurable para decidir el acceso de un sujeto en un contexto (área/dispositivo), con
 * condiciones opcionales (dirección, método de autenticación, ventanas de vigencia y horario) y una
 * acción final (PERMITIR/DENEGAR/PENDIENTE/etc.).
 *
 * <h2>Diseño</h2>
 * <ul>
 * <li><b>Multi-tenant:</b> hereda {@link TenantAuditableEntity} y se aisla por
 * {@code idOrganizacion}.</li>
 * <li><b>Matching opcional:</b> {@code null} en un criterio significa “aplica a cualquiera”.</li>
 * <li><b>Vigencia UTC:</b> ventana absoluta opcional con
 * {@code validoDesdeUtc}/{@code validoHastaUtc}.</li>
 * <li><b>Ventana diaria:</b> ventana opcional por hora local con
 * {@code desdeHoraLocal}/{@code hastaHoraLocal}.</li>
 * <li><b>Prioridad:</b> a mayor valor, mayor precedencia en el motor.</li>
 * <li><b>Estado:</b> permite activar/inactivar sin borrar.</li>
 * </ul>
 */
@Entity
@Table(name = "reglas_acceso", indexes = {
        @Index(name = "ix_regla_org_estado", columnList = "id_organizacion, estado"),
        @Index(name = "ix_regla_org_area", columnList = "id_organizacion, id_area"),
        @Index(name = "ix_regla_org_area_sujeto",
                columnList = "id_organizacion, id_area, tipo_sujeto"),
        @Index(name = "ix_regla_org_dispositivo", columnList = "id_organizacion, id_dispositivo"),
        @Index(name = "ix_regla_org_prioridad", columnList = "id_organizacion, prioridad")})

public class ReglaAcceso extends TenantAuditableEntity {

    @Id
    @Column(name = "id_regla", nullable = false)
    private UUID idRegla;

    @Column(name = "id_area", nullable = false)
    private UUID idArea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "id_area", referencedColumnName = "id_area", insertable = false,
                    updatable = false),
            @JoinColumn(name = "id_organizacion", referencedColumnName = "id_organizacion",
                    insertable = false, updatable = false)})
    private Area area;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sujeto", nullable = false, length = 20)
    private TipoSujetoAcceso tipoSujeto;

    @Column(name = "id_dispositivo")
    private UUID idDispositivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "id_dispositivo", referencedColumnName = "id_dispositivo",
                    insertable = false, updatable = false),
            @JoinColumn(name = "id_organizacion", referencedColumnName = "id_organizacion",
                    insertable = false, updatable = false)})
    private Dispositivo dispositivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "direccion_paso", length = 10)
    private TipoDireccionPaso direccionPaso;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_autenticacion", length = 20)
    private TipoMetodoAutenticacion metodoAutenticacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, length = 20)
    private TipoAccionAcceso accion;

    @Column(name = "prioridad", nullable = false)
    private Integer prioridad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoReglaAcceso estado;

    @Column(name = "mensaje", length = 250)
    private String mensaje;

    @Column(name = "valido_desde_utc")
    private OffsetDateTime validoDesdeUtc;

    @Column(name = "valido_hasta_utc")
    private OffsetDateTime validoHastaUtc;

    /**
     * Ventana diaria local - opcional.
     *
     * <p>
     * Se modela como {@link LocalTime} para evitar errores de parsing y validación de formato. La
     * interpretación por zona horaria la realiza el motor con {@code TenantZoneProvider}.
     * </p>
     */
    @Column(name = "desde_hora_local")
    private LocalTime desdeHoraLocal;

    @Column(name = "hasta_hora_local")
    private LocalTime hastaHoraLocal;

    protected ReglaAcceso() {}

    /**
     * Factory method para crear una regla de acceso.
     *
     * <p>
     * Defaults seguros:
     * <ul>
     * <li>{@code prioridad=100}</li>
     * <li>{@code estado=ACTIVA}</li>
     * </ul>
     * </p>
     */
    public static ReglaAcceso crear(UUID orgId, UUID idArea, TipoSujetoAcceso tipoSujeto,
            UUID idDispositivo, TipoDireccionPaso direccionPaso,
            TipoMetodoAutenticacion metodoAutenticacion, TipoAccionAcceso accion,
            OffsetDateTime validoDesdeUtc, OffsetDateTime validoHastaUtc, LocalTime desdeHoraLocal,
            LocalTime hastaHoraLocal, Integer prioridad, String mensaje) {

        ReglaAcceso r = new ReglaAcceso();
        r.setIdRegla(UUID.randomUUID());
        r.assignTenant(orgId);

        r.setIdArea(idArea);
        r.setTipoSujeto(tipoSujeto);

        r.setIdDispositivo(idDispositivo);
        r.setDireccionPaso(direccionPaso);
        r.setMetodoAutenticacion(metodoAutenticacion);

        r.setAccion(accion);

        r.setValidoDesdeUtc(validoDesdeUtc);
        r.setValidoHastaUtc(validoHastaUtc);

        r.setDesdeHoraLocal(desdeHoraLocal);
        r.setHastaHoraLocal(hastaHoraLocal);

        r.setPrioridad(prioridad != null ? prioridad : 100);
        r.setEstado(EstadoReglaAcceso.ACTIVA);
        r.setMensaje(mensaje);

        return r;
    }

    // -------------------------
    // Getters/Setters
    // -------------------------

    public UUID getIdRegla() {
        return idRegla;
    }

    public void setIdRegla(UUID idRegla) {
        if (idRegla == null)
            throw new IllegalArgumentException("idRegla es obligatorio");
        this.idRegla = idRegla;
    }

    public UUID getIdArea() {
        return idArea;
    }

    public void setIdArea(UUID idArea) {
        if (idArea == null)
            throw new IllegalArgumentException("idArea es obligatorio");
        this.idArea = idArea;
    }

    public Area getArea() {
        return area;
    }

    public UUID getIdDispositivo() {
        return idDispositivo;
    }

    public void setIdDispositivo(UUID idDispositivo) {
        this.idDispositivo = idDispositivo;
    }

    public Dispositivo getDispositivo() {
        return dispositivo;
    }

    public TipoSujetoAcceso getTipoSujeto() {
        return tipoSujeto;
    }

    public void setTipoSujeto(TipoSujetoAcceso tipoSujeto) {
        if (tipoSujeto == null)
            throw new IllegalArgumentException("tipoSujeto es obligatorio");
        this.tipoSujeto = tipoSujeto;
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

    public TipoAccionAcceso getAccion() {
        return accion;
    }

    public void setAccion(TipoAccionAcceso accion) {
        if (accion == null)
            throw new IllegalArgumentException("accion es obligatorio");
        this.accion = accion;
    }

    public Integer getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(Integer prioridad) {
        if (prioridad == null)
            throw new IllegalArgumentException("prioridad es obligatoria");
        this.prioridad = prioridad;
    }

    public EstadoReglaAcceso getEstado() {
        return estado;
    }

    public void setEstado(EstadoReglaAcceso estado) {
        if (estado == null)
            throw new IllegalArgumentException("estado es obligatorio");
        this.estado = estado;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = (mensaje == null || mensaje.isBlank()) ? null : mensaje.trim();
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

    public LocalTime getDesdeHoraLocal() {
        return desdeHoraLocal;
    }

    public void setDesdeHoraLocal(LocalTime desdeHoraLocal) {
        this.desdeHoraLocal = desdeHoraLocal;
    }

    public LocalTime getHastaHoraLocal() {
        return hastaHoraLocal;
    }

    public void setHastaHoraLocal(LocalTime hastaHoraLocal) {
        this.hastaHoraLocal = hastaHoraLocal;
    }

    // Helpers operativos
    public void activar() {
        setEstado(EstadoReglaAcceso.ACTIVA);
    }

    public void inactivar() {
        setEstado(EstadoReglaAcceso.INACTIVA);
    }
}
