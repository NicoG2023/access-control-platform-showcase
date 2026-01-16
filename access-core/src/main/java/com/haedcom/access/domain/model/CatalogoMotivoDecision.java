package com.haedcom.access.domain.model;

import java.util.Objects;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Catálogo de motivos de decisión (código y descripción).
 *
 * <p>
 * Estos motivos se usan en el flujo de decisiones para registrar el motivo de una decisión
 * (permiso, denegación, requerimiento de autenticación, control de espera).
 * </p>
 *
 * <p>
 * Es importante tener estos motivos en el catálogo para asegurar la trazabilidad de las decisiones
 * tomadas en el sistema.
 * </p>
 */
@Entity
@Table(name = "catalogo_motivo_decision")
public class CatalogoMotivoDecision {

    public static final CatalogoMotivoDecision MOTIVO_NO_MATCHING_RULE =
            new CatalogoMotivoDecision("NO_MATCHING_RULE", "No existe una regla aplicable");
    public static final CatalogoMotivoDecision MOTIVO_NO_RULES_FOR_CONTEXT =
            new CatalogoMotivoDecision("NO_RULES_FOR_CONTEXT",
                    "No existen reglas base para el contexto");

    @Id
    private String codigoMotivo;
    private String descripcion;

    // Constructor vacío para JPA
    public CatalogoMotivoDecision() {}

    /**
     * Constructor de la clase.
     *
     * @param codigoMotivo código que representa el motivo
     * @param descripcion descripción del motivo
     */
    public CatalogoMotivoDecision(String codigoMotivo, String descripcion) {
        this.codigoMotivo = Objects.requireNonNull(codigoMotivo, "codigoMotivo es obligatorio");
        this.descripcion = descripcion;
    }

    public String getCodigoMotivo() {
        return codigoMotivo;
    }

    public void setCodigoMotivo(String codigoMotivo) {
        this.codigoMotivo = codigoMotivo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    // ==========================================================================
    // Métodos estáticos para el catálogo de motivos predefinidos
    // ==========================================================================

    public static final CatalogoMotivoDecision MOTIVO_ALLOW =
            new CatalogoMotivoDecision("RULE_MATCH_ALLOW", "Acceso permitido por regla");
    public static final CatalogoMotivoDecision MOTIVO_DENY =
            new CatalogoMotivoDecision("RULE_MATCH_DENY", "Acceso denegado por regla");
    public static final CatalogoMotivoDecision MOTIVO_REQUIRE_AUTH = new CatalogoMotivoDecision(
            "RULE_MATCH_REQUIRE_AUTH", "Requiere autenticación adicional");
    public static final CatalogoMotivoDecision MOTIVO_WAIT_CONTROL =
            new CatalogoMotivoDecision("RULE_MATCH_WAIT_CONTROL", "Control requiere espera");
    public static final CatalogoMotivoDecision MOTIVO_POLICY_ERROR =
            new CatalogoMotivoDecision("POLICY_ERROR", "Error en los datos o política de decisión");

    /**
     * Resuelve el motivo por código. Si no existe, retorna un motivo de error.
     *
     * @param codigo el código del motivo
     * @return un objeto {@link CatalogoMotivoDecision}
     */
    public static CatalogoMotivoDecision resolveByCodigo(String codigo) {
        switch (codigo) {
            case "RULE_MATCH_ALLOW":
                return MOTIVO_ALLOW;
            case "RULE_MATCH_DENY":
                return MOTIVO_DENY;
            case "RULE_MATCH_REQUIRE_AUTH":
                return MOTIVO_REQUIRE_AUTH;
            case "RULE_MATCH_WAIT_CONTROL":
                return MOTIVO_WAIT_CONTROL;
            case "POLICY_ERROR":
                return MOTIVO_POLICY_ERROR;
            case "NO_MATCHING_RULE":
                return MOTIVO_NO_MATCHING_RULE;
            case "NO_RULES_FOR_CONTEXT":
                return MOTIVO_NO_RULES_FOR_CONTEXT;
            default:
                return new CatalogoMotivoDecision(codigo, "Motivo desconocido");
        }
    }
}
