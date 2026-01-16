package com.haedcom.access.domain.repo;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import com.haedcom.access.domain.model.VisitantePreautorizado;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;

/**
 * Repositorio de acceso a datos para la entidad {@link VisitantePreautorizado}.
 *
 * <p>
 * Encapsula la lógica de persistencia y consultas relacionadas con visitantes
 * preautorizados,
 * aplicando siempre el aislamiento por tenant mediante {@code idOrganizacion}.
 * </p>
 *
 * <p>
 * Nota: las consultas de existencia por documento no reemplazan el
 * {@code UNIQUE} en base de datos
 * (pueden existir carreras concurrentes). El servicio debe traducir la
 * violación real del constraint
 * {@code ux_visitante_doc} a un error de negocio (p.ej. 409).
 * </p>
 */
@ApplicationScoped
public class VisitantePreautorizadoRepository extends BaseRepository<VisitantePreautorizado, UUID> {

    /**
     * Constructor del repositorio.
     */
    public VisitantePreautorizadoRepository() {
        super(VisitantePreautorizado.class);
    }

    /**
     * Lista los visitantes preautorizados de una organización de forma paginada,
     * ordenados por
     * {@code actualizadoEnUtc DESC}.
     *
     * <p>
     * Este método no aplica filtros adicionales (para filtros y ordenamiento
     * configurable, usar
     * {@link #searchByOrganizacion(UUID, UUID, String, TipoDocumentoIdentidad, String, String, String, int, int)}).
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param page  número de página (base 0). Si es menor que 0, se ajusta a 0.
     * @param size  tamaño de página. Si es menor o igual que 0, se ajusta a 20.
     * @return lista de visitantes preautorizados de la página solicitada
     */
    public List<VisitantePreautorizado> listByOrganizacion(UUID orgId, int page, int size) {
        if (page < 0)
            page = 0;
        if (size <= 0)
            size = 20;

        return em.createQuery(
                "select v from VisitantePreautorizado v "
                        + "where v.idOrganizacion = :orgId "
                        + "order by v.actualizadoEnUtc desc",
                VisitantePreautorizado.class)
                .setParameter("orgId", orgId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    /**
     * Busca un visitante preautorizado por su id dentro del contexto de una
     * organización.
     *
     * <p>
     * Este método es la forma segura de obtener una entidad en un entorno
     * multi-tenant, porque
     * aplica la restricción por {@code orgId}.
     * </p>
     *
     * @param visitanteId identificador del visitante
     * @param orgId       identificador de la organización (tenant)
     * @return {@link Optional} con el visitante si existe y pertenece a la
     *         organización; vacío en caso contrario
     */
    public Optional<VisitantePreautorizado> findByIdAndOrganizacion(UUID visitanteId, UUID orgId) {
        return em.createQuery(
                "select v from VisitantePreautorizado v "
                        + "where v.idVisitante = :id and v.idOrganizacion = :orgId",
                VisitantePreautorizado.class)
                .setParameter("id", visitanteId)
                .setParameter("orgId", orgId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /**
     * Busca un visitante preautorizado por documento dentro del contexto de una
     * organización.
     *
     * <p>
     * Útil para validar preautorizaciones por documento (p.ej. en el flujo de
     * ingreso).
     * </p>
     *
     * @param orgId  identificador de la organización (tenant)
     * @param tipo   tipo de documento
     * @param numero número de documento (se normaliza con {@code trim()})
     * @return {@link Optional} con el visitante si existe; vacío en caso contrario
     * @throws IllegalArgumentException si {@code numero} es {@code null} o blank
     */
    public Optional<VisitantePreautorizado> findByDocumento(UUID orgId, TipoDocumentoIdentidad tipo, String numero) {
        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("numero requerido");
        }

        return em.createQuery(
                "select v from VisitantePreautorizado v "
                        + "where v.idOrganizacion = :orgId "
                        + "and v.tipoDocumento = :tipo "
                        + "and v.numeroDocumento = :num",
                VisitantePreautorizado.class)
                .setParameter("orgId", orgId)
                .setParameter("tipo", tipo)
                .setParameter("num", numero.trim())
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /**
     * Verifica si existe un visitante preautorizado con el mismo documento dentro
     * de una organización.
     *
     * <p>
     * Pensado para validación previa en creación. La verificación final sigue
     * estando garantizada por
     * el {@code UNIQUE} en base de datos.
     * </p>
     *
     * @param orgId  identificador de la organización (tenant)
     * @param tipo   tipo de documento
     * @param numero número de documento (se normaliza con {@code trim()})
     * @return {@code true} si existe al menos un visitante con ese documento;
     *         {@code false} en caso contrario
     * @throws IllegalArgumentException si {@code numero} es {@code null} o blank
     */
    public boolean existsByDocumento(UUID orgId, TipoDocumentoIdentidad tipo, String numero) {
        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("numero requerido");
        }

        return !em.createQuery(
                "select 1 from VisitantePreautorizado v "
                        + "where v.idOrganizacion = :orgId "
                        + "and v.tipoDocumento = :tipo "
                        + "and v.numeroDocumento = :num",
                Integer.class)
                .setParameter("orgId", orgId)
                .setParameter("tipo", tipo)
                .setParameter("num", numero.trim())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    /**
     * Verifica si existe un visitante preautorizado con el mismo documento dentro
     * de una organización,
     * excluyendo un visitante específico.
     *
     * <p>
     * Pensado para validación previa en actualización (evita conflicto con “otro”
     * visitante).
     * La verificación final sigue estando garantizada por el {@code UNIQUE} en base
     * de datos.
     * </p>
     *
     * @param orgId              identificador de la organización (tenant)
     * @param tipo               tipo de documento
     * @param numero             número de documento (se normaliza con
     *                           {@code trim()})
     * @param excludeVisitanteId identificador del visitante a excluir del chequeo
     * @return {@code true} si existe otro visitante con ese documento;
     *         {@code false} en caso contrario
     * @throws IllegalArgumentException si {@code numero} es {@code null} o blank
     */
    public boolean existsByDocumentoExcludingId(
            UUID orgId,
            TipoDocumentoIdentidad tipo,
            String numero,
            UUID excludeVisitanteId) {

        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("numero requerido");
        }

        return !em.createQuery(
                "select 1 from VisitantePreautorizado v "
                        + "where v.idOrganizacion = :orgId "
                        + "and v.tipoDocumento = :tipo "
                        + "and v.numeroDocumento = :num "
                        + "and v.idVisitante <> :excludeId",
                Integer.class)
                .setParameter("orgId", orgId)
                .setParameter("tipo", tipo)
                .setParameter("num", numero.trim())
                .setParameter("excludeId", excludeVisitanteId)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    /**
     * Cuenta los visitantes preautorizados de una organización (sin filtros
     * adicionales).
     *
     * @param orgId identificador de la organización (tenant)
     * @return total de visitantes preautorizados en la organización
     */
    public long countByOrganizacion(UUID orgId) {
        Long total = em.createQuery(
                "select count(v) from VisitantePreautorizado v where v.idOrganizacion = :orgId",
                Long.class)
                .setParameter("orgId", orgId)
                .getSingleResult();

        return total == null ? 0L : total;
    }

    /**
     * Lista visitantes preautorizados aplicando filtros opcionales y ordenamiento.
     *
     * <p>
     * Filtros soportados (todos opcionales):
     * <ul>
     * <li>{@code residenteId}: filtra por residente asociado.</li>
     * <li>{@code q}: búsqueda por texto parcial (LIKE) sobre {@code nombre},
     * {@code numeroDocumento},
     * {@code correo} y {@code telefono}.</li>
     * <li>{@code tipoDocumento}: coincidencia exacta.</li>
     * <li>{@code numeroDocumento}: coincidencia exacta (útil para búsquedas
     * directas por documento).</li>
     * </ul>
     * </p>
     *
     * <p>
     * Ordenamiento (controlado por whitelist para evitar inyección en
     * {@code ORDER BY}):
     * <ul>
     * <li>{@code sort}: {@code nombre} | {@code creadoEnUtc} |
     * {@code actualizadoEnUtc} |
     * {@code numeroDocumento}</li>
     * <li>{@code dir}: {@code asc} | {@code desc} (por defecto: {@code desc})</li>
     * </ul>
     * Si {@code sort} es inválido o {@code null}, se usa {@code actualizadoEnUtc}.
     * </p>
     *
     * @param orgId           identificador de la organización (tenant)
     * @param residenteId     identificador del residente (opcional)
     * @param q               término de búsqueda libre (puede ser {@code null} o
     *                        blank)
     * @param tipoDocumento   filtro por tipo de documento (opcional)
     * @param numeroDocumento filtro por documento exacto (opcional; se normaliza
     *                        con {@code trim()})
     * @param sort            campo de ordenamiento (opcional; whitelist)
     * @param dir             dirección del ordenamiento (opcional; {@code asc} o
     *                        {@code desc})
     * @param page            número de página (base 0). Si es menor que 0, se
     *                        ajusta a 0.
     * @param size            tamaño de página. Si es menor o igual que 0, se ajusta
     *                        a 20.
     * @return lista paginada y ordenada de visitantes que cumplen el criterio
     */
    public List<VisitantePreautorizado> searchByOrganizacion(
            UUID orgId,
            UUID residenteId,
            String q,
            TipoDocumentoIdentidad tipoDocumento,
            String numeroDocumento,
            String sort,
            String dir,
            int page,
            int size) {

        if (page < 0)
            page = 0;
        if (size <= 0)
            size = 20;

        StringBuilder jpql = new StringBuilder(
                "select v from VisitantePreautorizado v where v.idOrganizacion = :orgId");

        var params = new java.util.HashMap<String, Object>();
        params.put("orgId", orgId);

        // --- filtros ---
        if (residenteId != null) {
            jpql.append(" and v.residente.idResidente = :residenteId");
            params.put("residenteId", residenteId);
        }

        String qq = (q == null) ? null : q.trim();
        if (qq != null && !qq.isBlank()) {
            jpql.append(" and (")
                    .append(" lower(v.nombre) like :q")
                    .append(" or v.numeroDocumento like :qExactOrLike")
                    .append(" or lower(v.correo) like :q")
                    .append(" or v.telefono like :qExactOrLike")
                    .append(" )");

            params.put("q", "%" + qq.toLowerCase(Locale.ROOT) + "%");
            params.put("qExactOrLike", "%" + qq + "%");
        }

        if (tipoDocumento != null) {
            jpql.append(" and v.tipoDocumento = :tipoDocumento");
            params.put("tipoDocumento", tipoDocumento);
        }

        String num = (numeroDocumento == null) ? null : numeroDocumento.trim();
        if (num != null && !num.isBlank()) {
            jpql.append(" and v.numeroDocumento = :numeroDocumento");
            params.put("numeroDocumento", num);
        }

        // --- order by (whitelist) ---
        String orderBy = resolveOrderBy(sort);
        String direction = resolveDirection(dir);
        jpql.append(" order by ").append(orderBy).append(" ").append(direction);

        TypedQuery<VisitantePreautorizado> query = em.createQuery(jpql.toString(), VisitantePreautorizado.class);
        params.forEach(query::setParameter);

        return query.setFirstResult(page * size).setMaxResults(size).getResultList();
    }

    /**
     * Cuenta visitantes preautorizados aplicando los mismos filtros que
     * {@link #searchByOrganizacion(UUID, UUID, String, TipoDocumentoIdentidad, String, String, String, int, int)}.
     *
     * <p>
     * Este método se usa para construir metadatos de paginación ({@code total}) en
     * endpoints paginados.
     * </p>
     *
     * @param orgId           identificador de la organización (tenant)
     * @param residenteId     identificador del residente (opcional)
     * @param q               término de búsqueda libre (opcional)
     * @param tipoDocumento   filtro por tipo de documento (opcional)
     * @param numeroDocumento filtro por documento exacto (opcional; se normaliza
     *                        con {@code trim()})
     * @return total de visitantes que cumplen el criterio
     */
    public long countSearchByOrganizacion(
            UUID orgId,
            UUID residenteId,
            String q,
            TipoDocumentoIdentidad tipoDocumento,
            String numeroDocumento) {

        StringBuilder jpql = new StringBuilder(
                "select count(v) from VisitantePreautorizado v where v.idOrganizacion = :orgId");

        var params = new java.util.HashMap<String, Object>();
        params.put("orgId", orgId);

        if (residenteId != null) {
            jpql.append(" and v.residente.idResidente = :residenteId");
            params.put("residenteId", residenteId);
        }

        String qq = (q == null) ? null : q.trim();
        if (qq != null && !qq.isBlank()) {
            jpql.append(" and (")
                    .append(" lower(v.nombre) like :q")
                    .append(" or v.numeroDocumento like :qExactOrLike")
                    .append(" or lower(v.correo) like :q")
                    .append(" or v.telefono like :qExactOrLike")
                    .append(" )");

            params.put("q", "%" + qq.toLowerCase(Locale.ROOT) + "%");
            params.put("qExactOrLike", "%" + qq + "%");
        }

        if (tipoDocumento != null) {
            jpql.append(" and v.tipoDocumento = :tipoDocumento");
            params.put("tipoDocumento", tipoDocumento);
        }

        String num = (numeroDocumento == null) ? null : numeroDocumento.trim();
        if (num != null && !num.isBlank()) {
            jpql.append(" and v.numeroDocumento = :numeroDocumento");
            params.put("numeroDocumento", num);
        }

        var query = em.createQuery(jpql.toString(), Long.class);
        params.forEach(query::setParameter);

        Long total = query.getSingleResult();
        return total == null ? 0L : total;
    }

    /**
     * Resuelve el campo a usar en {@code ORDER BY} a partir de una whitelist.
     *
     * <p>
     * Se devuelve siempre una expresión JPQL segura (no se concatena el input
     * directamente).
     * </p>
     */
    private String resolveOrderBy(String sort) {
        String s = (sort == null) ? "" : sort.trim();
        return switch (s) {
            case "nombre" -> "v.nombre";
            case "creadoEnUtc" -> "v.creadoEnUtc";
            case "actualizadoEnUtc", "" -> "v.actualizadoEnUtc";
            case "numeroDocumento" -> "v.numeroDocumento";
            default -> "v.actualizadoEnUtc";
        };
    }

    /**
     * Resuelve la dirección del ordenamiento.
     *
     * <p>
     * Por defecto devuelve {@code desc}. Solo se acepta {@code asc} explícitamente.
     * </p>
     */
    private String resolveDirection(String dir) {
        String d = (dir == null) ? "" : dir.trim().toLowerCase(Locale.ROOT);
        return ("asc".equals(d)) ? "asc" : "desc";
    }
}
