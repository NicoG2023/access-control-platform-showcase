package com.haedcom.access.domain.model;

import java.util.Set;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoGrupo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Grupo de residentes (tenant-aware).
 *
 * <p>
 * Un {@code GrupoResidentes} pertenece a una organización ({@code idOrganizacion}) y permite
 * agrupar residentes para aplicar reglas/condiciones de acceso de forma colectiva.
 * </p>
 *
 * <h3>Notas de integridad</h3>
 * <ul>
 * <li>El nombre es obligatorio y se normaliza (lower-case) para soportar unicidad
 * "case-insensitive" mediante un UNIQUE estándar.</li>
 * <li>Se define un constraint UNIQUE por tenant para evitar duplicados:
 * {@code (id_organizacion, nombre)}.</li>
 * </ul>
 */
@Entity
@Table(name = "grupo_residentes",
        uniqueConstraints = @UniqueConstraint(name = "ux_grupo_residentes_nombre",
                columnNames = {"id_organizacion", "nombre"}))
public class GrupoResidentes extends TenantAuditableEntity {

    @Id
    @Column(name = "id_grupo_residente", nullable = false)
    private UUID idGrupoResidente;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "grupo_residente_residente",
            joinColumns = @JoinColumn(name = "id_grupo_residente"),
            inverseJoinColumns = @JoinColumn(name = "id_residente"))
    private Set<Residente> residentes;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoGrupo estado;

    protected GrupoResidentes() {}

    /**
     * Crea un grupo con estado por defecto {@link EstadoGrupo#ACTIVO}.
     *
     * <p>
     * Nota: el tenant ({@code idOrganizacion}) se asigna en capa de servicio mediante
     * {@link #setOrganizacionTenant(Organizacion)} o {@link #assignTenant(UUID)}.
     * </p>
     *
     * @param nombre nombre del grupo (obligatorio)
     * @return instancia del grupo
     */
    public static GrupoResidentes crear(String nombre) {
        GrupoResidentes grupo = new GrupoResidentes();
        grupo.setIdGrupoResidente(UUID.randomUUID());
        grupo.setNombre(nombre);
        grupo.setEstado(EstadoGrupo.ACTIVO);
        return grupo;
    }

    public UUID getIdGrupoResidente() {
        return idGrupoResidente;
    }

    public void setIdGrupoResidente(UUID idGrupoResidente) {
        if (idGrupoResidente == null) {
            throw new IllegalArgumentException("idGrupoResidente no puede ser null");
        }
        this.idGrupoResidente = idGrupoResidente;
    }

    public String getNombre() {
        return nombre;
    }

    /**
     * Asigna el nombre del grupo.
     *
     * <p>
     * Defensa en profundidad:
     * <ul>
     * <li>Rechaza null/blank.</li>
     * <li>Valida longitud máxima.</li>
     * <li>Normaliza a lower-case para que la unicidad por tenant sea realmente case-insensitive
     * (apoyada por el UNIQUE {@code (id_organizacion, nombre)}).</li>
     * </ul>
     * </p>
     *
     * @param nombre nombre del grupo
     */
    public void setNombre(String nombre) {
        String v = (nombre == null) ? null : nombre.trim();
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("nombre es obligatorio");
        }
        if (v.length() > 100) {
            throw new IllegalArgumentException("nombre máximo 100 caracteres");
        }
        this.nombre = v.toLowerCase();
    }

    public Set<Residente> getResidentes() {
        return residentes;
    }

    public void setResidentes(Set<Residente> residentes) {
        this.residentes = residentes;
    }

    public EstadoGrupo getEstado() {
        return estado;
    }

    /**
     * Asigna el estado del grupo.
     *
     * @param estado estado (obligatorio)
     */
    public void setEstado(EstadoGrupo estado) {
        if (estado == null) {
            throw new IllegalArgumentException("estado es obligatorio");
        }
        this.estado = estado;
    }

    /**
     * Asegura defaults antes de persistir.
     *
     * <p>
     * Útil para escenarios donde la instancia se construye por JPA/reflection o en tests.
     * </p>
     */
    @PrePersist
    void ensureDefaults() {
        if (idGrupoResidente == null) {
            idGrupoResidente = UUID.randomUUID();
        }
        if (estado == null) {
            estado = EstadoGrupo.ACTIVO;
        }
    }
}
