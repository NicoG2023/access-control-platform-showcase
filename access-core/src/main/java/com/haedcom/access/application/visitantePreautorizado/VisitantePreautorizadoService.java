package com.haedcom.access.application.visitantePreautorizado;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.visitante.dto.VisitantePreautorizadoResponse;
import com.haedcom.access.api.visitante.dto.VisitantePreautorizadoUpsertRequest;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import com.haedcom.access.domain.model.Organizacion;
import com.haedcom.access.domain.model.Residente;
import com.haedcom.access.domain.model.VisitantePreautorizado;
import com.haedcom.access.domain.repo.OrganizacionRepository;
import com.haedcom.access.domain.repo.ResidenteRepository;
import com.haedcom.access.domain.repo.VisitantePreautorizadoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Servicio de aplicación que orquesta los casos de uso para la gestión de
 * {@link VisitantePreautorizado} dentro de una organización (multi-tenant por
 * {@code idOrganizacion}).
 *
 * <p>
 * Responsabilidades principales:
 * <ul>
 * <li>Aplicar aislamiento por tenant (todas las operaciones se ejecutan en el contexto de un
 * {@code orgId}).</li>
 * <li>Coordinar repositorios y entidades de dominio.</li>
 * <li>Aplicar reglas que requieren acceso a base de datos (p.ej. validación previa de
 * unicidad).</li>
 * <li>Traducir violaciones de integridad (p.ej. UNIQUE) a errores HTTP consistentes (p.ej.
 * 409).</li>
 * <li>Mapear entidades a DTOs de salida.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Nota: validaciones intrínsecas del modelo (campos obligatorios, longitudes, etc.) se delegan al
 * dominio y/o a Bean Validation en los DTOs.
 * </p>
 */
@ApplicationScoped
public class VisitantePreautorizadoService {

    /**
     * Nombre del constraint UNIQUE en base de datos para (id_organizacion, tipo_documento,
     * numero_documento). Usado para detectar y traducir a 409 conflictos de documento.
     */
    private static final String UK_VISITANTE_DOC = "ux_visitante_doc";

    private final VisitantePreautorizadoRepository visitanteRepo;
    private final OrganizacionRepository orgRepo;
    private final ResidenteRepository residenteRepo;

    /**
     * Constructor del servicio de visitantes preautorizados.
     *
     * @param visitanteRepo repositorio de visitantes preautorizados
     * @param orgRepo repositorio de organizaciones
     * @param residenteRepo repositorio de residentes (para validar asociación opcional)
     */
    public VisitantePreautorizadoService(VisitantePreautorizadoRepository visitanteRepo,
            OrganizacionRepository orgRepo, ResidenteRepository residenteRepo) {
        this.visitanteRepo = Objects.requireNonNull(visitanteRepo, "visitanteRepo es obligatorio");
        this.orgRepo = Objects.requireNonNull(orgRepo, "orgRepo es obligatorio");
        this.residenteRepo = Objects.requireNonNull(residenteRepo, "residenteRepo es obligatorio");
    }

    /**
     * Lista los visitantes preautorizados de una organización de forma paginada, aplicando filtros
     * y ordenamiento.
     *
     * <p>
     * Este método soporta:
     * <ul>
     * <li><b>Filtros</b>: por {@code residenteId}, búsqueda libre {@code q}, {@code tipoDocumento}
     * y {@code numeroDocumento} exacto.</li>
     * <li><b>Ordenamiento</b>: {@code sort} y {@code dir} (aplicado mediante whitelist en el
     * repositorio).</li>
     * <li><b>Metadatos</b>: retorna {@link PageResponse} con {@code total}, {@code totalPages},
     * {@code hasNext}/{@code hasPrev}.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Nota: el {@code total} se calcula con una consulta adicional de conteo con los mismos
     * filtros.
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId filtro por residente asociado (opcional)
     * @param q término de búsqueda libre (opcional). Si se envía, se aplica búsqueda parcial
     *        (LIKE).
     * @param tipoDocumento filtro por tipo de documento (opcional)
     * @param numeroDocumento filtro por número de documento exacto (opcional)
     * @param sort campo de ordenamiento (opcional; whitelist en repositorio)
     * @param dir dirección del ordenamiento (opcional; {@code asc} o {@code desc})
     * @param page número de página (base 0)
     * @param size tamaño de página
     * @return respuesta paginada con visitantes y metadatos
     */
    @Transactional
    public PageResponse<VisitantePreautorizadoResponse> list(UUID orgId, UUID residenteId, String q,
            TipoDocumentoIdentidad tipoDocumento, String numeroDocumento, String sort, String dir,
            int page, int size) {

        List<VisitantePreautorizadoResponse> items = visitanteRepo.searchByOrganizacion(orgId,
                residenteId, q, tipoDocumento, numeroDocumento, sort, dir, page, size).stream()
                .map(v -> toResponse(v)).toList();

        long total = visitanteRepo.countSearchByOrganizacion(orgId, residenteId, q, tipoDocumento,
                numeroDocumento);

        return PageResponse.of(items, page, size, total);
    }

    /**
     * Obtiene un visitante preautorizado específico dentro de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param visitanteId identificador del visitante
     * @return DTO con la información del visitante
     * @throws NotFoundException si el visitante no existe o no pertenece a la organización
     */
    @Transactional
    public VisitantePreautorizadoResponse get(UUID orgId, UUID visitanteId) {
        return toResponse(getVisitanteOrThrow(orgId, visitanteId));
    }

    /**
     * Crea un nuevo visitante preautorizado dentro de una organización.
     *
     * <p>
     * Reglas aplicadas:
     * <ul>
     * <li>La organización debe existir.</li>
     * <li>Validación previa de unicidad de documento dentro de la organización.</li>
     * <li>Si se envía {@code idResidente}, este debe existir y pertenecer al tenant.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Concurrencia: la validación previa de unicidad mejora la experiencia, pero no evita carreras.
     * Por eso se fuerza {@code flush()} para detectar la violación del {@code UNIQUE} dentro del
     * método y traducirla consistentemente a {@code 409 Conflict}.
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param req datos de creación del visitante (no {@code null})
     * @return visitante creado
     * @throws NotFoundException si la organización no existe
     * @throws WebApplicationException con estado 409 si el documento ya existe
     */
    @Transactional
    public VisitantePreautorizadoResponse create(UUID orgId,
            VisitantePreautorizadoUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Organizacion org = getOrganizacionOrThrow(orgId);

        ensureDocumentoUnicoOnCreate(orgId, req.tipoDocumento(), req.numeroDocumento());

        Residente residente = resolveResidenteOrNull(orgId, req.idResidente());

        VisitantePreautorizado v = new VisitantePreautorizado();
        v.setIdVisitante(UUID.randomUUID());
        v.setOrganizacionTenant(org);
        v.setResidente(residente);
        v.setNombre(normalize(req.nombre()));
        v.setTipoDocumento(req.tipoDocumento());
        v.setNumeroDocumento(normalize(req.numeroDocumento()));
        v.setCorreo(normalizeOrNull(req.correo()));
        v.setTelefono(normalizeOrNull(req.telefono()));

        try {
            visitanteRepo.persist(v);
            visitanteRepo.flush();
            return toResponse(v);
        } catch (RuntimeException e) {
            if (isUniqueDocumentoViolation(e)) {
                throw conflictDocumento();
            }
            throw e;
        }
    }

    /**
     * Actualiza la información de un visitante preautorizado existente dentro del tenant.
     *
     * <p>
     * Reglas aplicadas:
     * <ul>
     * <li>El visitante debe existir y pertenecer a la organización.</li>
     * <li>Validación previa de unicidad de documento excluyendo al visitante actual.</li>
     * <li>Si se envía {@code idResidente}, este debe existir y pertenecer al tenant.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Concurrencia: se fuerza {@code flush()} para detectar la violación del {@code UNIQUE} dentro
     * del método y traducirla consistentemente a {@code 409 Conflict}.
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param visitanteId identificador del visitante a actualizar
     * @param req nuevos datos del visitante (no {@code null})
     * @return visitante actualizado
     * @throws NotFoundException si el visitante no existe o no pertenece a la organización
     * @throws WebApplicationException con estado 409 si el documento entra en conflicto
     */
    @Transactional
    public VisitantePreautorizadoResponse update(UUID orgId, UUID visitanteId,
            VisitantePreautorizadoUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        VisitantePreautorizado v = getVisitanteOrThrow(orgId, visitanteId);

        ensureDocumentoUnicoOnUpdate(orgId, req.tipoDocumento(), req.numeroDocumento(),
                visitanteId);

        Residente residente = resolveResidenteOrNull(orgId, req.idResidente());

        v.setResidente(residente);
        v.setNombre(normalize(req.nombre()));
        v.setTipoDocumento(req.tipoDocumento());
        v.setNumeroDocumento(normalize(req.numeroDocumento()));
        v.setCorreo(normalizeOrNull(req.correo()));
        v.setTelefono(normalizeOrNull(req.telefono()));

        try {
            visitanteRepo.flush();
            return toResponse(v);
        } catch (RuntimeException e) {
            if (isUniqueDocumentoViolation(e)) {
                throw conflictDocumento();
            }
            throw e;
        }
    }

    /**
     * Elimina un visitante preautorizado de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param visitanteId identificador del visitante
     * @throws NotFoundException si el visitante no existe o no pertenece a la organización
     */
    @Transactional
    public void delete(UUID orgId, UUID visitanteId) {
        VisitantePreautorizado v = getVisitanteOrThrow(orgId, visitanteId);
        visitanteRepo.delete(v);
    }

    // -------------------------
    // Helpers privados
    // -------------------------

    /**
     * Obtiene una organización o lanza excepción si no existe.
     *
     * @param orgId identificador de la organización
     * @return organización existente
     * @throws NotFoundException si no se encuentra la organización
     */
    private Organizacion getOrganizacionOrThrow(UUID orgId) {
        return orgRepo.findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organización no encontrada"));
    }

    /**
     * Obtiene un visitante por id y organización o lanza excepción si no existe.
     *
     * <p>
     * Este método garantiza el aislamiento por tenant: si el visitante no pertenece a la
     * organización, se considera como no encontrado.
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param visitanteId identificador del visitante
     * @return visitante existente y perteneciente al tenant
     * @throws NotFoundException si no se encuentra el visitante para ese tenant
     */
    private VisitantePreautorizado getVisitanteOrThrow(UUID orgId, UUID visitanteId) {
        return visitanteRepo.findByIdAndOrganizacion(visitanteId, orgId)
                .orElseThrow(() -> new NotFoundException("Visitante preautorizado no encontrado"));
    }

    /**
     * Resuelve el residente asociado a partir del {@code residenteId}.
     *
     * <p>
     * Si {@code residenteId} es {@code null}, retorna {@code null} (asociación opcional). Si no es
     * {@code null}, valida que exista y pertenezca al mismo tenant; si no, lanza 404.
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId identificador del residente (opcional)
     * @return entidad {@link Residente} si aplica; {@code null} si no se envía residente
     * @throws NotFoundException si se envía {@code residenteId} y no existe dentro del tenant
     */
    private Residente resolveResidenteOrNull(UUID orgId, UUID residenteId) {
        if (residenteId == null) {
            return null;
        }
        return residenteRepo.findByIdAndOrganizacion(residenteId, orgId).orElseThrow(
                () -> new NotFoundException("Residente no encontrado para la organización"));
    }

    /**
     * Verifica unicidad de documento al crear (validación previa).
     *
     * @param orgId identificador de la organización (tenant)
     * @param tipo tipo de documento
     * @param numero número de documento
     * @throws WebApplicationException 409 si ya existe un visitante con ese documento en la
     *         organización
     */
    private void ensureDocumentoUnicoOnCreate(UUID orgId, TipoDocumentoIdentidad tipo,
            String numero) {
        if (visitanteRepo.existsByDocumento(orgId, tipo, numero)) {
            throw conflictDocumento();
        }
    }

    /**
     * Verifica unicidad de documento al actualizar, excluyendo al visitante actual (validación
     * previa).
     *
     * @param orgId identificador de la organización (tenant)
     * @param tipo tipo de documento
     * @param numero número de documento
     * @param visitanteId id del visitante que se excluye de la verificación
     * @throws WebApplicationException 409 si existe otro visitante con el mismo documento
     */
    private void ensureDocumentoUnicoOnUpdate(UUID orgId, TipoDocumentoIdentidad tipo,
            String numero, UUID visitanteId) {
        if (visitanteRepo.existsByDocumentoExcludingId(orgId, tipo, numero, visitanteId)) {
            throw conflictDocumento();
        }
    }

    /**
     * Construye una excepción estándar de conflicto por documento duplicado.
     *
     * @return excepción HTTP 409 (Conflict)
     */
    private WebApplicationException conflictDocumento() {
        return new WebApplicationException(
                "Ya existe un visitante preautorizado con ese documento en la organización",
                Response.Status.CONFLICT);
    }

    /**
     * Determina si la excepción (o alguna de sus causas) corresponde a una violación de unicidad de
     * documento en Postgres.
     *
     * <p>
     * Estrategia:
     * <ul>
     * <li>Detectar el nombre del constraint {@code ux_visitante_doc} en el mensaje.</li>
     * <li>O detectar {@code SQLState 23505} (unique_violation) si aparece una
     * {@link java.sql.SQLException} en la causa.</li>
     * </ul>
     * </p>
     *
     * @param e excepción capturada
     * @return {@code true} si corresponde (probablemente) a conflicto de documento; {@code false}
     *         en caso contrario
     */
    private boolean isUniqueDocumentoViolation(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains(UK_VISITANTE_DOC)) {
                return true;
            }
            if (cur instanceof java.sql.SQLException sqlEx) {
                if ("23505".equals(sqlEx.getSQLState())) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * Normaliza un string requerido (trim).
     *
     * @param s valor de entrada
     * @return valor normalizado (no null)
     * @throws IllegalArgumentException si {@code s} es null o blank
     */
    private String normalize(String s) {
        String v = (s == null) ? null : s.trim();
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Valor requerido");
        }
        return v;
    }

    /**
     * Normaliza un string opcional (trim), retornando {@code null} si queda blank.
     *
     * @param s valor de entrada (puede ser null)
     * @return valor normalizado o {@code null}
     */
    private String normalizeOrNull(String s) {
        String v = (s == null) ? null : s.trim();
        return (v == null || v.isBlank()) ? null : v;
    }

    /**
     * Mapea una entidad {@link VisitantePreautorizado} a su DTO de respuesta.
     *
     * <p>
     * Se usa {@code v.getIdOrganizacion()} (FK) para evitar navegación de relaciones LAZY en
     * lecturas. El {@code residenteId} se expone como UUID para no forzar inicialización de la
     * relación.
     * </p>
     *
     * @param v entidad visitante preautorizado
     * @return DTO de respuesta
     */
    private VisitantePreautorizadoResponse toResponse(VisitantePreautorizado v) {
        UUID residenteId = (v.getResidente() == null) ? null : v.getResidente().getIdResidente();

        return new VisitantePreautorizadoResponse(v.getIdVisitante(), v.getIdOrganizacion(),
                residenteId, v.getNombre(), v.getTipoDocumento(), v.getNumeroDocumento(),
                v.getCorreo(), v.getTelefono(), v.getCreadoEnUtc(), v.getActualizadoEnUtc());
    }
}
