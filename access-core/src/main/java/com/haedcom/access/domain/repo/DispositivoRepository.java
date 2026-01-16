package com.haedcom.access.domain.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.haedcom.access.domain.model.Dispositivo;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio de acceso a datos para {@link Dispositivo}.
 *
 * <p>
 * Este repositorio encapsula consultas específicas de {@code Dispositivo} respetando el aislamiento
 * multi-tenant por {@code idOrganizacion}.
 * </p>
 *
 * <h2>Responsabilidades</h2>
 * <ul>
 * <li>CRUD y consultas por tenant (org).</li>
 * <li>Búsquedas típicas para listados (paginadas) y conteos.</li>
 * <li>Validaciones de existencia para constraints (p.ej. identificador externo único).</li>
 * </ul>
 *
 * <p>
 * No contiene lógica de negocio (eso vive en la capa de servicio). El control transaccional se
 * realiza en el service.
 * </p>
 */
@ApplicationScoped
public class DispositivoRepository extends BaseRepository<Dispositivo, UUID> {

    /**
     * Crea el repositorio para {@link Dispositivo}.
     */
    public DispositivoRepository() {
        super(Dispositivo.class);
    }

    /**
     * Busca un dispositivo por id dentro del tenant.
     *
     * <p>
     * Este método garantiza aislamiento por tenant: si el id existe pero pertenece a otra
     * organización, retorna vacío.
     * </p>
     *
     * @param dispositivoId id del dispositivo
     * @param orgId id de la organización (tenant)
     * @return {@link Optional} con el dispositivo si existe y pertenece al tenant
     */
    public Optional<Dispositivo> findByIdAndOrganizacion(UUID dispositivoId, UUID orgId) {
        return em.createQuery("""
                select d
                from Dispositivo d
                where d.idDispositivo = :id
                  and d.idOrganizacion = :orgId
                """, Dispositivo.class).setParameter("id", dispositivoId)
                .setParameter("orgId", orgId).getResultStream().findFirst();
    }

    /**
     * Lista dispositivos de una organización de forma paginada.
     *
     * <p>
     * Ordena por {@code nombre} ascendente por defecto para garantizar orden determinista.
     * </p>
     *
     * @param orgId id de la organización (tenant)
     * @param page página (base 0)
     * @param size tamaño de página
     * @return lista de dispositivos del tenant
     */
    public List<Dispositivo> listByOrganizacion(UUID orgId, int page, int size) {
        return em.createQuery("""
                select d
                from Dispositivo d
                where d.idOrganizacion = :orgId
                order by d.nombre asc
                """, Dispositivo.class).setParameter("orgId", orgId).setFirstResult(page * size)
                .setMaxResults(size).getResultList();
    }

    /**
     * Lista dispositivos de una organización filtrando por área (opcional), de forma paginada.
     *
     * <p>
     * Útil para pantallas que listan dispositivos dentro de un área.
     * </p>
     *
     * @param orgId id de la organización (tenant)
     * @param areaId id del área (dentro del mismo tenant)
     * @param page página (base 0)
     * @param size tamaño de página
     * @return lista de dispositivos filtrados por área
     */
    public List<Dispositivo> listByOrganizacionAndArea(UUID orgId, UUID areaId, int page,
            int size) {
        return em.createQuery("""
                select d
                from Dispositivo d
                where d.idOrganizacion = :orgId
                  and d.idArea = :areaId
                order by d.nombre asc
                """, Dispositivo.class).setParameter("orgId", orgId).setParameter("areaId", areaId)
                .setFirstResult(page * size).setMaxResults(size).getResultList();
    }

    /**
     * Cuenta dispositivos de una organización (para paginación).
     *
     * @param orgId id de la organización (tenant)
     * @return total de dispositivos del tenant
     */
    public long countByOrganizacion(UUID orgId) {
        return em.createQuery("""
                select count(d)
                from Dispositivo d
                where d.idOrganizacion = :orgId
                """, Long.class).setParameter("orgId", orgId).getSingleResult();
    }

    /**
     * Cuenta dispositivos de una organización filtrando por área.
     *
     * @param orgId id de la organización (tenant)
     * @param areaId id del área
     * @return total de dispositivos del área dentro del tenant
     */
    public long countByOrganizacionAndArea(UUID orgId, UUID areaId) {
        return em.createQuery("""
                select count(d)
                from Dispositivo d
                where d.idOrganizacion = :orgId
                  and d.idArea = :areaId
                """, Long.class).setParameter("orgId", orgId).setParameter("areaId", areaId)
                .getSingleResult();
    }

    /**
     * Verifica si existe un dispositivo con el mismo {@code identificadorExterno} en cualquier
     * tenant.
     *
     * <p>
     * Nota: en tu entidad el campo está marcado como {@code unique = true} (global), por lo que el
     * chequeo debe ser global también.
     * </p>
     *
     * @param identificadorExterno identificador externo
     * @return {@code true} si ya existe algún dispositivo con ese identificador
     */
    public boolean existsByIdentificadorExterno(String identificadorExterno) {
        if (identificadorExterno == null || identificadorExterno.isBlank()) {
            return false;
        }
        Long c = em.createQuery("""
                select count(d)
                from Dispositivo d
                where d.identificadorExterno = :ext
                """, Long.class).setParameter("ext", identificadorExterno.trim()).getSingleResult();
        return c != null && c > 0;
    }

    /**
     * Verifica si existe otro dispositivo (distinto al actual) con el mismo
     * {@code identificadorExterno}.
     *
     * <p>
     * Útil para validación previa en update, antes de hacer {@code flush()}.
     * </p>
     *
     * @param identificadorExterno identificador externo
     * @param excludingId id del dispositivo que se excluye
     * @return {@code true} si existe otro dispositivo con ese identificador externo
     */
    public boolean existsByIdentificadorExternoExcludingId(String identificadorExterno,
            UUID excludingId) {
        if (identificadorExterno == null || identificadorExterno.isBlank()) {
            return false;
        }
        Long c = em.createQuery("""
                select count(d)
                from Dispositivo d
                where d.identificadorExterno = :ext
                  and d.idDispositivo <> :id
                """, Long.class).setParameter("ext", identificadorExterno.trim())
                .setParameter("id", excludingId).getSingleResult();
        return c != null && c > 0;
    }
}
