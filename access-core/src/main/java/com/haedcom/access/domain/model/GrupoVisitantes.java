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

@Entity
@Table(name = "grupo_visitantes",
        uniqueConstraints = @UniqueConstraint(name = "ux_grupo_visitantes_nombre",
                columnNames = {"id_organizacion", "nombre"}))
public class GrupoVisitantes extends TenantAuditableEntity {

    @Id
    @Column(name = "id_grupo_visitante", nullable = false)
    private UUID idGrupoVisitante;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "grupo_visitante_visitante",
            joinColumns = @JoinColumn(name = "id_grupo_visitante"),
            inverseJoinColumns = @JoinColumn(name = "id_visitante"))
    private Set<VisitantePreautorizado> visitantes;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoGrupo estado;

    /** Constructor protegido requerido por JPA. */
    protected GrupoVisitantes() {}

    public static GrupoVisitantes crear(String nombre) {
        GrupoVisitantes grupo = new GrupoVisitantes();
        grupo.setIdGrupoVisitante(UUID.randomUUID());
        grupo.setNombre(nombre);
        grupo.setEstado(EstadoGrupo.ACTIVO); // Estado por defecto
        return grupo;
    }

    public UUID getIdGrupoVisitante() {
        return idGrupoVisitante;
    }

    public void setIdGrupoVisitante(UUID idGrupoVisitante) {
        if (idGrupoVisitante == null)
            throw new IllegalArgumentException("idGrupoVisitante no puede ser null");
        this.idGrupoVisitante = idGrupoVisitante;
    }

    public String getNombre() {
        return nombre;
    }

    /**
     * Actualiza el nombre del grupo.
     *
     * <p>
     * Reglas:
     * <ul>
     * <li>Obligatorio (no null / no blank).</li>
     * <li>Se normaliza con {@code trim()}.</li>
     * <li>Máximo 100 caracteres (alineado a la columna).</li>
     * </ul>
     * </p>
     */
    public void setNombre(String nombre) {
        String v = (nombre == null) ? null : nombre.trim();
        if (v == null || v.isBlank())
            throw new IllegalArgumentException("nombre es obligatorio");
        if (v.length() > 100)
            throw new IllegalArgumentException("nombre máximo 100 caracteres");
        this.nombre = v.toLowerCase();
    }

    public Set<VisitantePreautorizado> getVisitantes() {
        return visitantes;
    }

    public void setVisitantes(Set<VisitantePreautorizado> visitantes) {
        this.visitantes = visitantes;
    }

    public EstadoGrupo getEstado() {
        return estado;
    }

    /**
     * Actualiza el estado del grupo.
     *
     * @param estado nuevo estado (obligatorio)
     */
    public void setEstado(EstadoGrupo estado) {
        if (estado == null)
            throw new IllegalArgumentException("estado es obligatorio");
        this.estado = estado;
    }

    /**
     * Garantiza valores por defecto antes de persistir.
     *
     * <p>
     * Mantiene la misma convención de {@link Residente}:
     * <ul>
     * <li>Si el ID viene null, se genera.</li>
     * <li>Si el estado viene null, se pone ACTIVO.</li>
     * </ul>
     * </p>
     */
    @PrePersist
    void ensureDefaults() {
        if (idGrupoVisitante == null) {
            idGrupoVisitante = UUID.randomUUID();
        }
        if (estado == null) {
            estado = EstadoGrupo.ACTIVO;
        }
    }
}
