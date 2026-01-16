package com.haedcom.access.domain.repo;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoReglaAcceso;
import com.haedcom.access.domain.enums.TipoAccionAcceso;
import com.haedcom.access.domain.enums.TipoDireccionPaso;
import com.haedcom.access.domain.enums.TipoMetodoAutenticacion;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import com.haedcom.access.domain.model.ReglaAcceso;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;

/**
 * Repositorio JPA para {@link ReglaAcceso}.
 *
 * <p>
 * Este repositorio implementa consultas <b>multi-tenant</b>: toda búsqueda debe filtrar por
 * {@code r.idOrganizacion}.
 * </p>
 *
 * <h2>Convenciones</h2>
 * <ul>
 * <li>Campos opcionales en reglas se interpretan como “wildcard” (null = aplica a cualquiera) en
 * consultas tipo “motor-friendly”.</li>
 * <li>Las validaciones de formato/consistencia de ventana diaria se hacen en la capa de aplicación
 * (p.ej. {@code ReglaAccesoService}) antes de persistir.</li>
 * <li>La evaluación timezone-aware de la ventana diaria (overnight, etc.) se realiza en el motor de
 * decisión, resolviendo zona efectiva por tenant/área (p.ej. {@code TenantZoneProvider}).</li>
 * </ul>
 */
@ApplicationScoped
public class ReglaAccesoRepository extends BaseRepository<ReglaAcceso, UUID> {

    public ReglaAccesoRepository() {
        super(ReglaAcceso.class);
    }

    // =========================================================================
    // LIST / ADMIN
    // =========================================================================

    /**
     * Lista reglas de una organización (tenant) con filtros opcionales y paginación.
     *
     * <p>
     * Pensado para CRUD/listados administrativos. Aquí, si un filtro llega {@code null},
     * simplemente NO se filtra por ese campo (no aplica semántica wildcard).
     * </p>
     *
     * <p>
     * Orden por defecto: {@code actualizadoEnUtc DESC}.
     * </p>
     */
    public List<ReglaAcceso> searchByOrganizacion(UUID orgId, UUID idArea, UUID idDispositivo,
            TipoSujetoAcceso tipoSujeto, TipoDireccionPaso direccionPaso,
            TipoMetodoAutenticacion metodoAutenticacion, TipoAccionAcceso accion,
            EstadoReglaAcceso estado, int page, int size) {

        StringBuilder jpql =
                new StringBuilder("SELECT r FROM ReglaAcceso r WHERE r.idOrganizacion = :orgId");
        List<String> filters = new ArrayList<>();

        if (idArea != null)
            filters.add("r.idArea = :idArea");
        if (idDispositivo != null)
            filters.add("r.idDispositivo = :idDispositivo");
        if (tipoSujeto != null)
            filters.add("r.tipoSujeto = :tipoSujeto");
        if (direccionPaso != null)
            filters.add("r.direccionPaso = :direccionPaso");
        if (metodoAutenticacion != null)
            filters.add("r.metodoAutenticacion = :metodoAutenticacion");
        if (accion != null)
            filters.add("r.accion = :accion");
        if (estado != null)
            filters.add("r.estado = :estado");

        if (!filters.isEmpty()) {
            jpql.append(" AND ").append(String.join(" AND ", filters));
        }

        jpql.append(" ORDER BY r.actualizadoEnUtc DESC");

        TypedQuery<ReglaAcceso> q = em.createQuery(jpql.toString(), ReglaAcceso.class);
        q.setParameter("orgId", orgId);

        if (idArea != null)
            q.setParameter("idArea", idArea);
        if (idDispositivo != null)
            q.setParameter("idDispositivo", idDispositivo);
        if (tipoSujeto != null)
            q.setParameter("tipoSujeto", tipoSujeto);
        if (direccionPaso != null)
            q.setParameter("direccionPaso", direccionPaso);
        if (metodoAutenticacion != null)
            q.setParameter("metodoAutenticacion", metodoAutenticacion);
        if (accion != null)
            q.setParameter("accion", accion);
        if (estado != null)
            q.setParameter("estado", estado);

        return q.setFirstResult(page * size).setMaxResults(size).getResultList();
    }

    /**
     * Cuenta reglas de una organización (tenant) con los mismos filtros de
     * {@link #searchByOrganizacion(UUID, UUID, UUID, TipoSujetoAcceso, TipoDireccionPaso, TipoMetodoAutenticacion, TipoAccionAcceso, EstadoReglaAcceso, int, int)}.
     */
    public long countSearchByOrganizacion(UUID orgId, UUID idArea, UUID idDispositivo,
            TipoSujetoAcceso tipoSujeto, TipoDireccionPaso direccionPaso,
            TipoMetodoAutenticacion metodoAutenticacion, TipoAccionAcceso accion,
            EstadoReglaAcceso estado) {

        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(r) FROM ReglaAcceso r WHERE r.idOrganizacion = :orgId");
        List<String> filters = new ArrayList<>();

        if (idArea != null)
            filters.add("r.idArea = :idArea");
        if (idDispositivo != null)
            filters.add("r.idDispositivo = :idDispositivo");
        if (tipoSujeto != null)
            filters.add("r.tipoSujeto = :tipoSujeto");
        if (direccionPaso != null)
            filters.add("r.direccionPaso = :direccionPaso");
        if (metodoAutenticacion != null)
            filters.add("r.metodoAutenticacion = :metodoAutenticacion");
        if (accion != null)
            filters.add("r.accion = :accion");
        if (estado != null)
            filters.add("r.estado = :estado");

        if (!filters.isEmpty()) {
            jpql.append(" AND ").append(String.join(" AND ", filters));
        }

        var q = em.createQuery(jpql.toString(), Long.class);
        q.setParameter("orgId", orgId);

        if (idArea != null)
            q.setParameter("idArea", idArea);
        if (idDispositivo != null)
            q.setParameter("idDispositivo", idDispositivo);
        if (tipoSujeto != null)
            q.setParameter("tipoSujeto", tipoSujeto);
        if (direccionPaso != null)
            q.setParameter("direccionPaso", direccionPaso);
        if (metodoAutenticacion != null)
            q.setParameter("metodoAutenticacion", metodoAutenticacion);
        if (accion != null)
            q.setParameter("accion", accion);
        if (estado != null)
            q.setParameter("estado", estado);

        return q.getSingleResult();
    }

    // =========================================================================
    // DUPLICATES
    // =========================================================================

    /**
     * Detecta si existe una regla “lógicamente duplicada” dentro del tenant.
     *
     * <p>
     * Se considera duplicada si coincide:
     * </p>
     * <ul>
     * <li>scope: {@code idArea + tipoSujeto}</li>
     * <li>condiciones opcionales: {@code idDispositivo, direccionPaso, metodoAutenticacion}</li>
     * <li>acción: {@code accion}</li>
     * <li>ventanas: {@code validoDesdeUtc, validoHastaUtc, desdeHoraLocal, hastaHoraLocal}</li>
     * </ul>
     *
     * <p>
     * Implementación null-safe: {@code (campo IS NULL AND :param IS NULL) OR (campo = :param)}.
     * </p>
     *
     * @param excludeReglaId id de regla a excluir (útil en updates); null para inserts
     * @return true si existe duplicado
     */
    public boolean existsDuplicateRule(UUID orgId, UUID idArea, TipoSujetoAcceso tipoSujeto,
            UUID idDispositivo, TipoDireccionPaso direccionPaso,
            TipoMetodoAutenticacion metodoAutenticacion, TipoAccionAcceso accion,
            OffsetDateTime validoDesdeUtc, OffsetDateTime validoHastaUtc, LocalTime desdeHoraLocal,
            LocalTime hastaHoraLocal, UUID excludeReglaId) {
        StringBuilder jpql = new StringBuilder("""
                    SELECT COUNT(r) FROM ReglaAcceso r
                    WHERE r.idOrganizacion = :orgId
                      AND r.idArea = :idArea
                      AND r.tipoSujeto = :tipoSujeto
                      AND r.accion = :accion
                """);

        // Helpers: agrega "IS NULL" o "= :param"
        if (idDispositivo == null) {
            jpql.append(" AND r.idDispositivo IS NULL");
        } else {
            jpql.append(" AND r.idDispositivo = :idDispositivo");
        }

        if (direccionPaso == null) {
            jpql.append(" AND r.direccionPaso IS NULL");
        } else {
            jpql.append(" AND r.direccionPaso = :direccionPaso");
        }

        if (metodoAutenticacion == null) {
            jpql.append(" AND r.metodoAutenticacion IS NULL");
        } else {
            jpql.append(" AND r.metodoAutenticacion = :metodoAutenticacion");
        }

        if (validoDesdeUtc == null) {
            jpql.append(" AND r.validoDesdeUtc IS NULL");
        } else {
            jpql.append(" AND r.validoDesdeUtc = :validoDesdeUtc");
        }

        if (validoHastaUtc == null) {
            jpql.append(" AND r.validoHastaUtc IS NULL");
        } else {
            jpql.append(" AND r.validoHastaUtc = :validoHastaUtc");
        }

        if (desdeHoraLocal == null) {
            jpql.append(" AND r.desdeHoraLocal IS NULL");
        } else {
            jpql.append(" AND r.desdeHoraLocal = :desdeHoraLocal");
        }

        if (hastaHoraLocal == null) {
            jpql.append(" AND r.hastaHoraLocal IS NULL");
        } else {
            jpql.append(" AND r.hastaHoraLocal = :hastaHoraLocal");
        }

        if (excludeReglaId != null) {
            jpql.append(" AND r.idRegla <> :excludeId");
        }

        var q = em.createQuery(jpql.toString(), Long.class);

        // Obligatorios (siempre están)
        q.setParameter("orgId", orgId);
        q.setParameter("idArea", idArea);
        q.setParameter("tipoSujeto", tipoSujeto);
        q.setParameter("accion", accion);

        // Opcionales: SOLO si se usaron en JPQL
        if (idDispositivo != null) {
            q.setParameter("idDispositivo", idDispositivo);
        }
        if (direccionPaso != null) {
            q.setParameter("direccionPaso", direccionPaso);
        }
        if (metodoAutenticacion != null) {
            q.setParameter("metodoAutenticacion", metodoAutenticacion);
        }
        if (validoDesdeUtc != null) {
            q.setParameter("validoDesdeUtc", validoDesdeUtc);
        }
        if (validoHastaUtc != null) {
            q.setParameter("validoHastaUtc", validoHastaUtc);
        }
        if (desdeHoraLocal != null) {
            q.setParameter("desdeHoraLocal", desdeHoraLocal);
        }
        if (hastaHoraLocal != null) {
            q.setParameter("hastaHoraLocal", hastaHoraLocal);
        }
        if (excludeReglaId != null) {
            q.setParameter("excludeId", excludeReglaId);
        }

        return q.getSingleResult() > 0;
    }


    // =========================================================================
    // ENGINE-FRIENDLY CANDIDATES
    // =========================================================================

    /**
     * Devuelve reglas candidatas para evaluar un intento de acceso (consulta “motor-friendly”).
     *
     * <p>
     * Filtra por:
     * </p>
     * <ul>
     * <li>tenant: {@code idOrganizacion}</li>
     * <li>estado activa</li>
     * <li>scope mínimo: {@code idArea + tipoSujeto}</li>
     * <li>matching opcional (wildcards): si un campo en la regla es null, se considera “aplica a
     * cualquiera”</li>
     * <li>vigencia UTC: si existe, debe contener a {@code nowUtc}</li>
     * </ul>
     *
     * <p>
     * Importante: la ventana diaria ({@code desdeHoraLocal/hastaHoraLocal}) NO se evalúa aquí,
     * porque requiere convertir “ahora” a hora local según la zona efectiva del tenant/área. Esa
     * evaluación se hace en el motor de decisión (p.ej. {@code DecisionEngineService}) con
     * {@code TenantZoneProvider}.
     * </p>
     *
     * <h2>Ordenamiento</h2>
     * <ol>
     * <li>{@code prioridad DESC}</li>
     * <li>“especificidad” DESC (más condiciones definidas gana)</li>
     * <li>{@code actualizadoEnUtc DESC}</li>
     * </ol>
     *
     * @return lista de reglas candidatas ya ordenadas para que el motor aplique FIRST_MATCH
     */
    public List<ReglaAcceso> findCandidatesForIntent(UUID orgId, UUID idArea, UUID idDispositivo,
            TipoSujetoAcceso tipoSujeto, TipoDireccionPaso direccionPaso,
            TipoMetodoAutenticacion metodoAutenticacion, OffsetDateTime nowUtc) {

        String jpql =
                """
                        SELECT r FROM ReglaAcceso r
                        WHERE r.idOrganizacion = :orgId
                          AND r.estado = :estado
                          AND r.idArea = :idArea
                          AND r.tipoSujeto = :tipoSujeto
                          AND (r.idDispositivo IS NULL OR r.idDispositivo = :idDispositivo)
                          AND (r.direccionPaso IS NULL OR r.direccionPaso = :direccionPaso)
                          AND (r.metodoAutenticacion IS NULL OR r.metodoAutenticacion = :metodoAutenticacion)
                          AND (r.validoDesdeUtc IS NULL OR r.validoDesdeUtc <= :nowUtc)
                          AND (r.validoHastaUtc IS NULL OR r.validoHastaUtc >= :nowUtc)
                        ORDER BY
                          r.prioridad DESC,
                          (CASE WHEN r.idDispositivo IS NULL THEN 0 ELSE 1 END
                         + CASE WHEN r.direccionPaso IS NULL THEN 0 ELSE 1 END
                         + CASE WHEN r.metodoAutenticacion IS NULL THEN 0 ELSE 1 END
                         + CASE WHEN r.desdeHoraLocal IS NULL THEN 0 ELSE 1 END
                         + CASE WHEN r.hastaHoraLocal IS NULL THEN 0 ELSE 1 END
                         + CASE WHEN r.validoDesdeUtc IS NULL THEN 0 ELSE 1 END
                         + CASE WHEN r.validoHastaUtc IS NULL THEN 0 ELSE 1 END) DESC,
                          r.actualizadoEnUtc DESC
                        """;

        TypedQuery<ReglaAcceso> q = em.createQuery(jpql, ReglaAcceso.class);
        q.setParameter("orgId", orgId);
        q.setParameter("estado", EstadoReglaAcceso.ACTIVA);
        q.setParameter("idArea", idArea);
        q.setParameter("tipoSujeto", tipoSujeto);
        q.setParameter("idDispositivo", idDispositivo);
        q.setParameter("direccionPaso", direccionPaso);
        q.setParameter("metodoAutenticacion", metodoAutenticacion);
        q.setParameter("nowUtc", nowUtc);

        return q.getResultList();
    }

    public List<ReglaAcceso> findActiveRulesBase(UUID orgId, UUID idArea,
            TipoSujetoAcceso tipoSujeto) {
        String jpql = """
                    SELECT r FROM ReglaAcceso r
                    WHERE r.idOrganizacion = :orgId
                      AND r.estado = :estado
                      AND r.idArea = :idArea
                      AND r.tipoSujeto = :tipoSujeto
                    ORDER BY
                      r.prioridad DESC,
                      r.actualizadoEnUtc DESC
                """;

        return em.createQuery(jpql, ReglaAcceso.class).setParameter("orgId", orgId)
                .setParameter("estado", EstadoReglaAcceso.ACTIVA).setParameter("idArea", idArea)
                .setParameter("tipoSujeto", tipoSujeto).getResultList();
    }

}
