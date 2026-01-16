package com.haedcom.access.application.residente;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.residente.dto.ResidenteEstadoRequest;
import com.haedcom.access.api.residente.dto.ResidenteResponse;
import com.haedcom.access.api.residente.dto.ResidenteUpsertRequest;
import com.haedcom.access.domain.enums.EstadoResidente;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import com.haedcom.access.domain.model.Organizacion;
import com.haedcom.access.domain.model.Residente;
import com.haedcom.access.domain.repo.OrganizacionRepository;
import com.haedcom.access.domain.repo.ResidenteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Servicio de aplicación que orquesta los casos de uso para la gestión de {@link Residente} dentro
 * de una organización (multi-tenant por {@code idOrganizacion}).
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
 * dominio ({@link Residente}) y/o a Bean Validation en los DTOs.
 * </p>
 */
@ApplicationScoped
public class ResidenteService {

    /**
     * Nombre del constraint UNIQUE en base de datos para (id_organizacion, tipo_documento,
     * numero_documento). Usado para detectar y traducir a 409 conflictos de documento.
     */
    private static final String UK_RESIDENTE_DOC = "ux_residente_doc";

    private final ResidenteRepository residenteRepo;
    private final OrganizacionRepository orgRepo;

    /**
     * Constructor del servicio de residentes.
     *
     * @param residenteRepo repositorio de residentes
     * @param orgRepo repositorio de organizaciones
     */
    public ResidenteService(ResidenteRepository residenteRepo, OrganizacionRepository orgRepo) {
        this.residenteRepo = Objects.requireNonNull(residenteRepo, "residenteRepo es obligatorio");
        this.orgRepo = Objects.requireNonNull(orgRepo, "orgRepo es obligatorio");
    }

    /**
     * Lista los residentes de una organización de forma paginada, aplicando filtros y ordenamiento.
     *
     * <p>
     * Este método soporta:
     * <ul>
     * <li><b>Filtros</b>: búsqueda libre {@code q}, {@code tipoDocumento}, {@code numeroDocumento}
     * exacto y {@code estado}.</li>
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
     * @param q término de búsqueda libre (opcional). Si se envía, se aplica búsqueda parcial
     *        (LIKE).
     * @param tipoDocumento filtro por tipo de documento (opcional)
     * @param numeroDocumento filtro por número de documento exacto (opcional)
     * @param estado filtro por estado del residente (opcional)
     * @param sort campo de ordenamiento (opcional; whitelist en repositorio)
     * @param dir dirección del ordenamiento (opcional; {@code asc} o {@code desc})
     * @param page número de página (base 0)
     * @param size tamaño de página
     * @return respuesta paginada con residentes y metadatos
     */
    @Transactional
    public PageResponse<ResidenteResponse> list(UUID orgId, String q,
            TipoDocumentoIdentidad tipoDocumento, String numeroDocumento, EstadoResidente estado,
            String sort, String dir, int page, int size) {
        List<ResidenteResponse> items =
                residenteRepo.searchByOrganizacion(orgId, q, tipoDocumento, numeroDocumento, estado,
                        sort, dir, page, size).stream().map(this::toResponse).toList();

        long total = residenteRepo.countSearchByOrganizacion(orgId, q, tipoDocumento,
                numeroDocumento, estado);

        return PageResponse.of(items, page, size, total);
    }

    /**
     * Obtiene un residente específico dentro de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId identificador del residente
     * @return DTO con la información del residente
     * @throws NotFoundException si el residente no existe o no pertenece a la organización
     */
    @Transactional
    public ResidenteResponse get(UUID orgId, UUID residenteId) {
        return toResponse(getResidenteOrThrow(orgId, residenteId));
    }

    /**
     * Crea un nuevo residente dentro de una organización.
     *
     * <p>
     * Reglas aplicadas:
     * <ul>
     * <li>La organización debe existir.</li>
     * <li>Validación previa de unicidad de documento dentro de la organización.</li>
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
     * @param req datos de creación del residente (no {@code null})
     * @return residente creado
     * @throws NotFoundException si la organización no existe
     * @throws WebApplicationException con estado 409 si el documento ya existe (por validación
     *         previa o por UNIQUE en DB)
     */
    @Transactional
    public ResidenteResponse create(UUID orgId, ResidenteUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Organizacion org = getOrganizacionOrThrow(orgId);

        ensureDocumentoUnicoOnCreate(orgId, req.tipoDocumento(), req.numeroDocumento());

        Residente r = Residente.crear(org, req.nombre(), req.tipoDocumento(), req.numeroDocumento(),
                req.correo(), req.telefono(), req.domicilio());

        try {
            residenteRepo.persist(r);
            residenteRepo.flush();
            return toResponse(r);
        } catch (RuntimeException e) {
            if (isUniqueDocumentoViolation(e)) {
                throw conflictDocumento();
            }
            throw e;
        }
    }

    /**
     * Actualiza la información de un residente existente dentro del tenant.
     *
     * <p>
     * Reglas aplicadas:
     * <ul>
     * <li>El residente debe existir y pertenecer a la organización.</li>
     * <li>Validación previa de unicidad de documento excluyendo al residente actual.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Concurrencia: se fuerza {@code flush()} para detectar la violación del {@code UNIQUE} dentro
     * del método y traducirla consistentemente a {@code 409 Conflict}.
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId identificador del residente a actualizar
     * @param req nuevos datos del residente (no {@code null})
     * @return residente actualizado
     * @throws NotFoundException si el residente no existe o no pertenece a la organización
     * @throws WebApplicationException con estado 409 si el documento entra en conflicto
     */
    @Transactional
    public ResidenteResponse update(UUID orgId, UUID residenteId, ResidenteUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Residente r = getResidenteOrThrow(orgId, residenteId);

        ensureDocumentoUnicoOnUpdate(orgId, req.tipoDocumento(), req.numeroDocumento(),
                residenteId);

        r.actualizarDatos(req.nombre(), req.tipoDocumento(), req.numeroDocumento(), req.correo(),
                req.telefono(), req.domicilio());

        try {
            residenteRepo.flush();
            return toResponse(r);
        } catch (RuntimeException e) {
            if (isUniqueDocumentoViolation(e)) {
                throw conflictDocumento();
            }
            throw e;
        }
    }

    /**
     * Elimina un residente de una organización.
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId identificador del residente
     * @throws NotFoundException si el residente no existe o no pertenece a la organización
     */
    @Transactional
    public void delete(UUID orgId, UUID residenteId) {
        Residente r = getResidenteOrThrow(orgId, residenteId);
        residenteRepo.delete(r);
    }

    /**
     * Actualiza el estado de un residente dentro de una organización.
     *
     * <p>
     * Este método representa un caso de uso explícito de cambio de estado, separado de la
     * actualización de datos generales del residente (upsert).
     * </p>
     *
     * <p>
     * Reglas aplicadas:
     * <ul>
     * <li>El residente debe existir y pertenecer a la organización (aislamiento por tenant).</li>
     * <li>Solo se modifica el campo {@code estado}; no se alteran otros datos del residente.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Nota de diseño:
     * <ul>
     * <li>Separar el cambio de estado del upsert evita modificaciones accidentales del estado.</li>
     * <li>Permite agregar reglas de negocio específicas en el futuro (por ejemplo, validaciones
     * antes de inactivar).</li>
     * </ul>
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId identificador del residente cuyo estado será actualizado
     * @param req request con el nuevo {@link EstadoResidente} (no {@code null})
     * @return DTO del residente con el estado actualizado
     *
     * @throws NotFoundException si el residente no existe o no pertenece a la organización
     */
    @Transactional
    public ResidenteResponse updateEstado(UUID orgId, UUID residenteId,
            ResidenteEstadoRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Residente r = getResidenteOrThrow(orgId, residenteId);
        r.setEstado(req.estado());

        residenteRepo.flush();

        return toResponse(r);
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
     * Obtiene un residente por id y organización o lanza excepción si no existe.
     *
     * <p>
     * Este método garantiza el aislamiento por tenant: si el residente no pertenece a la
     * organización, se considera como no encontrado.
     * </p>
     *
     * @param orgId identificador de la organización (tenant)
     * @param residenteId identificador del residente
     * @return residente existente y perteneciente al tenant
     * @throws NotFoundException si no se encuentra el residente para ese tenant
     */
    private Residente getResidenteOrThrow(UUID orgId, UUID residenteId) {
        return residenteRepo.findByIdAndOrganizacion(residenteId, orgId)
                .orElseThrow(() -> new NotFoundException("Residente no encontrado"));
    }

    /**
     * Verifica unicidad de documento al crear (validación previa).
     *
     * @param orgId identificador de la organización (tenant)
     * @param tipo tipo de documento
     * @param numero número de documento
     * @throws WebApplicationException 409 si ya existe un residente con ese documento en la
     *         organización
     */
    private void ensureDocumentoUnicoOnCreate(UUID orgId, TipoDocumentoIdentidad tipo,
            String numero) {
        if (residenteRepo.existsByDocumento(orgId, tipo, numero)) {
            throw conflictDocumento();
        }
    }

    /**
     * Verifica unicidad de documento al actualizar, excluyendo al residente actual (validación
     * previa).
     *
     * @param orgId identificador de la organización (tenant)
     * @param tipo tipo de documento
     * @param numero número de documento
     * @param residenteId id del residente que se excluye de la verificación
     * @throws WebApplicationException 409 si existe otro residente con el mismo documento
     */
    private void ensureDocumentoUnicoOnUpdate(UUID orgId, TipoDocumentoIdentidad tipo,
            String numero, UUID residenteId) {
        if (residenteRepo.existsByDocumentoExcludingId(orgId, tipo, numero, residenteId)) {
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
                "Ya existe un residente con ese documento en la organización",
                Response.Status.CONFLICT);
    }

    /**
     * Determina si la excepción (o alguna de sus causas) corresponde a una violación de unicidad de
     * documento en Postgres.
     *
     * <p>
     * Estrategia:
     * <ul>
     * <li>Detectar el nombre del constraint {@code ux_residente_doc} en el mensaje.</li>
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
            if (msg != null && msg.contains(UK_RESIDENTE_DOC)) {
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
     * Mapea una entidad {@link Residente} a su DTO de respuesta.
     *
     * <p>
     * Se usa {@code r.getIdOrganizacion()} (FK) para evitar navegación de relaciones LAZY en
     * lecturas.
     * </p>
     *
     * @param r entidad residente
     * @return DTO de respuesta
     */
    private ResidenteResponse toResponse(Residente r) {
        return new ResidenteResponse(r.getIdResidente(), r.getIdOrganizacion(), r.getNombre(),
                r.getTipoDocumento(), r.getNumeroDocumento(), r.getCorreo(), r.getTelefono(),
                r.getDomicilio(), r.getEstado(), r.getCreadoEnUtc(), r.getActualizadoEnUtc());
    }
}
