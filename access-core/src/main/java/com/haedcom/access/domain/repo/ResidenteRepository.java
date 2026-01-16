package com.haedcom.access.domain.repo;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import com.haedcom.access.domain.enums.EstadoResidente;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import com.haedcom.access.domain.model.Residente;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;

/**
 * Repositorio de acceso a datos para la entidad {@link Residente}.
 *
 * <p>
 * Encapsula toda la lógica de persistencia y consultas relacionadas con residentes, aplicando
 * siempre el aislamiento por tenant mediante {@code idOrganizacion}.
 * </p>
 *
 * <p>
 * Nota: las consultas de existencia por documento no reemplazan el {@code UNIQUE} en base de datos
 * (pueden existir carreras concurrentes). El servicio debe seguir traduciendo la violación real del
 * constraint a un error de negocio (p.ej. 409).
 * </p>
 */
@ApplicationScoped
public class ResidenteRepository extends BaseRepository<Residente, UUID> {

        /**
         * Constructor del repositorio.
         */
        public ResidenteRepository() {
                super(Residente.class);
        }

        /**
         * Lista los residentes de una organización de forma paginada, ordenados por
         * {@code actualizadoEnUtc DESC}.
         *
         * <p>
         * Este método no aplica filtros adicionales (para filtros y ordenamiento configurable, usar
         * {@link #searchByOrganizacion(UUID, String, TipoDocumentoIdentidad, String, EstadoResidente, String, String, int, int)}).
         * </p>
         *
         * @param orgId identificador de la organización (tenant)
         * @param page número de página (base 0). Si es menor que 0, se ajusta a 0.
         * @param size tamaño de página. Si es menor o igual que 0, se ajusta a 20.
         * @return lista de residentes de la página solicitada
         */
        public List<Residente> listByOrganizacion(UUID orgId, int page, int size) {
                if (page < 0)
                        page = 0;
                if (size <= 0)
                        size = 20;

                return em.createQuery(
                                "select r from Residente r " + "where r.idOrganizacion = :orgId "
                                                + "order by r.actualizadoEnUtc desc",
                                Residente.class).setParameter("orgId", orgId)
                                .setFirstResult(page * size).setMaxResults(size).getResultList();
        }

        /**
         * Busca un residente por su id dentro del contexto de una organización.
         *
         * <p>
         * Este método es la forma segura de obtener un residente en un entorno multi-tenant, porque
         * aplica la restricción por {@code orgId}.
         * </p>
         *
         * @param residenteId identificador del residente
         * @param orgId identificador de la organización (tenant)
         * @return {@link Optional} con el residente si existe y pertenece a la organización; vacío
         *         en caso contrario
         */
        public Optional<Residente> findByIdAndOrganizacion(UUID residenteId, UUID orgId) {
                return em.createQuery("select r from Residente r "
                                + "where r.idResidente = :id and r.idOrganizacion = :orgId",
                                Residente.class).setParameter("id", residenteId)
                                .setParameter("orgId", orgId).setMaxResults(1).getResultStream()
                                .findFirst();
        }

        /**
         * Verifica si existe un residente con el mismo documento dentro de una organización.
         *
         * <p>
         * Pensado para validación previa en creación. La verificación final sigue estando
         * garantizada por el {@code UNIQUE} en base de datos.
         * </p>
         *
         * @param orgId identificador de la organización (tenant)
         * @param tipo tipo de documento
         * @param numero número de documento (se normaliza con {@code trim()})
         * @return {@code true} si existe al menos un residente con ese documento; {@code false} en
         *         caso contrario
         * @throws IllegalArgumentException si {@code numero} es {@code null} o vacío
         */
        public boolean existsByDocumento(UUID orgId, TipoDocumentoIdentidad tipo, String numero) {
                if (numero == null || numero.isBlank()) {
                        throw new IllegalArgumentException("numero requerido");
                }

                return !em.createQuery("select 1 from Residente r "
                                + "where r.idOrganizacion = :orgId "
                                + "and r.tipoDocumento = :tipo " + "and r.numeroDocumento = :num",
                                Integer.class).setParameter("orgId", orgId)
                                .setParameter("tipo", tipo).setParameter("num", numero.trim())
                                .setMaxResults(1).getResultList().isEmpty();
        }

        /**
         * Verifica si existe un residente con el mismo documento dentro de una organización,
         * excluyendo un residente específico.
         *
         * <p>
         * Pensado para validación previa en actualización (evita conflicto con “otro” residente).
         * La verificación final sigue estando garantizada por el {@code UNIQUE} en base de datos.
         * </p>
         *
         * @param orgId identificador de la organización (tenant)
         * @param tipo tipo de documento
         * @param numero número de documento (se normaliza con {@code trim()})
         * @param excludeResidenteId identificador del residente a excluir del chequeo
         * @return {@code true} si existe otro residente con ese documento; {@code false} en caso
         *         contrario
         * @throws IllegalArgumentException si {@code numero} es {@code null} o vacío
         */
        public boolean existsByDocumentoExcludingId(UUID orgId, TipoDocumentoIdentidad tipo,
                        String numero, UUID excludeResidenteId) {
                if (numero == null || numero.isBlank()) {
                        throw new IllegalArgumentException("numero requerido");
                }

                return !em.createQuery("select 1 from Residente r "
                                + "where r.idOrganizacion = :orgId "
                                + "and r.tipoDocumento = :tipo " + "and r.numeroDocumento = :num "
                                + "and r.idResidente <> :excludeId", Integer.class)
                                .setParameter("orgId", orgId).setParameter("tipo", tipo)
                                .setParameter("num", numero.trim())
                                .setParameter("excludeId", excludeResidenteId).setMaxResults(1)
                                .getResultList().isEmpty();
        }

        /**
         * Cuenta los residentes de una organización (sin filtros adicionales).
         *
         * @param orgId identificador de la organización (tenant)
         * @return total de residentes en la organización
         */
        public long countByOrganizacion(UUID orgId) {
                Long total = em.createQuery(
                                "select count(r) from Residente r where r.idOrganizacion = :orgId",
                                Long.class).setParameter("orgId", orgId).getSingleResult();

                return total == null ? 0L : total;
        }

        /**
         * Busca residentes de una organización aplicando filtros opcionales y ordenamiento.
         *
         * <p>
         * Filtros soportados (todos opcionales):
         * <ul>
         * <li>{@code q}: búsqueda por texto. Aplica a {@code nombre}, {@code numeroDocumento},
         * {@code correo} y {@code telefono}. La búsqueda por texto es parcial (LIKE).</li>
         * <li>{@code tipoDocumento}: coincidencia exacta.</li>
         * <li>{@code numeroDocumento}: coincidencia exacta (útil para búsquedas directas por
         * documento).</li>
         * <li>{@code estado}: coincidencia exacta.</li>
         * </ul>
         * </p>
         *
         * <p>
         * Ordenamiento (controlado por whitelist para evitar inyección en {@code ORDER BY}):
         * <ul>
         * <li>{@code sort}: {@code nombre} | {@code creadoEnUtc} | {@code actualizadoEnUtc} |
         * {@code numeroDocumento}</li>
         * <li>{@code dir}: {@code asc} | {@code desc} (por defecto: {@code desc})</li>
         * </ul>
         * Si {@code sort} es inválido o {@code null}, se usa {@code actualizadoEnUtc}.
         * </p>
         *
         * @param orgId identificador de la organización (tenant)
         * @param q término de búsqueda libre (puede ser {@code null} o blank)
         * @param tipoDocumento filtro por tipo de documento (opcional)
         * @param numeroDocumento filtro por documento exacto (opcional; se normaliza con
         *        {@code trim()})
         * @param estado filtro por estado (opcional)
         * @param sort campo de ordenamiento (opcional; whitelist)
         * @param dir dirección del ordenamiento (opcional; {@code asc} o {@code desc})
         * @param page número de página (base 0). Si es menor que 0, se ajusta a 0.
         * @param size tamaño de página. Si es menor o igual que 0, se ajusta a 20.
         * @return lista de residentes que cumplen el criterio, paginada y ordenada
         */
        public List<Residente> searchByOrganizacion(UUID orgId, String q,
                        TipoDocumentoIdentidad tipoDocumento, String numeroDocumento,
                        EstadoResidente estado, String sort, String dir, int page, int size) {
                if (page < 0)
                        page = 0;
                if (size <= 0)
                        size = 20;

                StringBuilder jpql = new StringBuilder(
                                "select r from Residente r where r.idOrganizacion = :orgId");
                var params = new java.util.HashMap<String, Object>();
                params.put("orgId", orgId);

                // --- filtros ---
                String qq = (q == null) ? null : q.trim();
                if (qq != null && !qq.isBlank()) {
                        jpql.append(" and (").append(" lower(r.nombre) like :q")
                                        .append(" or r.numeroDocumento like :qExactOrLike")
                                        .append(" or lower(r.correo) like :q")
                                        .append(" or r.telefono like :qExactOrLike").append(" )");

                        params.put("q", "%" + qq.toLowerCase(Locale.ROOT) + "%");
                        params.put("qExactOrLike", "%" + qq + "%");
                }

                if (tipoDocumento != null) {
                        jpql.append(" and r.tipoDocumento = :tipoDocumento");
                        params.put("tipoDocumento", tipoDocumento);
                }

                String num = (numeroDocumento == null) ? null : numeroDocumento.trim();
                if (num != null && !num.isBlank()) {
                        jpql.append(" and r.numeroDocumento = :numeroDocumento");
                        params.put("numeroDocumento", num);
                }

                if (estado != null) {
                        jpql.append(" and r.estado = :estado");
                        params.put("estado", estado);
                }

                // --- order by (whitelist) ---
                String orderBy = resolveOrderBy(sort);
                String direction = resolveDirection(dir);
                jpql.append(" order by ").append(orderBy).append(" ").append(direction);

                TypedQuery<Residente> query = em.createQuery(jpql.toString(), Residente.class);
                params.forEach(query::setParameter);

                return query.setFirstResult(page * size).setMaxResults(size).getResultList();
        }

        /**
         * Cuenta residentes aplicando los mismos filtros que
         * {@link #searchByOrganizacion(UUID, String, TipoDocumentoIdentidad, String, EstadoResidente, String, String, int, int)}.
         *
         * <p>
         * Este método se usa para construir metadatos de paginación ({@code total}) en endpoints
         * paginados.
         * </p>
         *
         * @param orgId identificador de la organización (tenant)
         * @param q término de búsqueda libre (opcional)
         * @param tipoDocumento filtro por tipo de documento (opcional)
         * @param numeroDocumento filtro por documento exacto (opcional; se normaliza con
         *        {@code trim()})
         * @param estado filtro por estado (opcional)
         * @return total de residentes que cumplen el criterio
         */
        public long countSearchByOrganizacion(UUID orgId, String q,
                        TipoDocumentoIdentidad tipoDocumento, String numeroDocumento,
                        EstadoResidente estado) {
                StringBuilder jpql = new StringBuilder(
                                "select count(r) from Residente r where r.idOrganizacion = :orgId");
                var params = new java.util.HashMap<String, Object>();
                params.put("orgId", orgId);

                String qq = (q == null) ? null : q.trim();
                if (qq != null && !qq.isBlank()) {
                        jpql.append(" and (").append(" lower(r.nombre) like :q")
                                        .append(" or r.numeroDocumento like :qExactOrLike")
                                        .append(" or lower(r.correo) like :q")
                                        .append(" or r.telefono like :qExactOrLike").append(" )");
                        params.put("q", "%" + qq.toLowerCase(Locale.ROOT) + "%");
                        params.put("qExactOrLike", "%" + qq + "%");
                }

                if (tipoDocumento != null) {
                        jpql.append(" and r.tipoDocumento = :tipoDocumento");
                        params.put("tipoDocumento", tipoDocumento);
                }

                String num = (numeroDocumento == null) ? null : numeroDocumento.trim();
                if (num != null && !num.isBlank()) {
                        jpql.append(" and r.numeroDocumento = :numeroDocumento");
                        params.put("numeroDocumento", num);
                }

                if (estado != null) {
                        jpql.append(" and r.estado = :estado");
                        params.put("estado", estado);
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
         * Se devuelve siempre una expresión JPQL segura (no se concatena el input directamente).
         * </p>
         */
        private String resolveOrderBy(String sort) {
                String s = (sort == null) ? "" : sort.trim();
                return switch (s) {
                        case "nombre" -> "r.nombre";
                        case "creadoEnUtc" -> "r.creadoEnUtc";
                        case "actualizadoEnUtc", "" -> "r.actualizadoEnUtc";
                        case "numeroDocumento" -> "r.numeroDocumento";
                        default -> "r.actualizadoEnUtc";
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
