package com.haedcom.access.application.dispositivo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.haedcom.access.api.common.pagination.PageResponse;
import com.haedcom.access.api.dispositivo.dto.DispositivoResponse;
import com.haedcom.access.api.dispositivo.dto.DispositivoUpsertRequest;
import com.haedcom.access.domain.model.Area;
import com.haedcom.access.domain.model.Dispositivo;
import com.haedcom.access.domain.repo.AreaRepository;
import com.haedcom.access.domain.repo.DispositivoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Servicio de aplicación que orquesta los casos de uso para la gestión básica de
 * {@link Dispositivo} dentro de una organización (multi-tenant por {@code idOrganizacion}).
 *
 * <p>
 * Este service cubre el CRUD básico (listar, obtener, crear, actualizar, eliminar). Funcionalidades
 * avanzadas como concurrencia/eventos se delegarán a servicios especializados en el futuro.
 * </p>
 *
 * <h2>Responsabilidades</h2>
 * <ul>
 * <li>Aislamiento por tenant (todas las operaciones ocurren en el contexto de {@code orgId}).</li>
 * <li>Validación de existencia de {@code Area} dentro del tenant.</li>
 * <li>Validación previa de unicidad para {@code identificadorExterno} (campo global unique).</li>
 * <li>Mapeo de entidades a DTOs.</li>
 * <li>Traducción consistente de conflictos a HTTP 409 cuando aplica.</li>
 * </ul>
 */
@ApplicationScoped
public class DispositivoService {

    /**
     * Nombre del constraint UNIQUE (si existe en DB) para {@code identificador_externo}.
     *
     * <p>
     * Si tu schema lo nombra distinto, ajusta esta constante. Se usa para detectar violación de
     * unicidad vía parsing del mensaje. Aun así, también se detecta por SQLState 23505.
     * </p>
     */
    private static final String UK_DISPOSITIVO_IDENTIFICADOR_EXTERNO =
            "dispositivo_identificador_externo_key";

    private final DispositivoRepository dispositivoRepo;
    private final AreaRepository areaRepo;

    /**
     * Constructor del servicio.
     *
     * @param dispositivoRepo repositorio de dispositivos
     * @param areaRepo repositorio de áreas (para validar pertenencia del área al tenant)
     */
    public DispositivoService(DispositivoRepository dispositivoRepo, AreaRepository areaRepo) {
        this.dispositivoRepo =
                Objects.requireNonNull(dispositivoRepo, "dispositivoRepo es obligatorio");
        this.areaRepo = Objects.requireNonNull(areaRepo, "areaRepo es obligatorio");
    }

    /**
     * Lista dispositivos de una organización de forma paginada.
     *
     * <p>
     * Si {@code areaId} se envía, filtra por esa área dentro del tenant.
     * </p>
     *
     * @param orgId identificador del tenant
     * @param areaId filtro por área (opcional)
     * @param page número de página (base 0)
     * @param size tamaño de página
     * @return {@link PageResponse} con items y metadatos
     */
    @Transactional
    public PageResponse<DispositivoResponse> list(UUID orgId, UUID areaId, int page, int size) {
        List<Dispositivo> devices;
        long total;

        if (areaId == null) {
            devices = dispositivoRepo.listByOrganizacion(orgId, page, size);
            total = dispositivoRepo.countByOrganizacion(orgId);
        } else {
            // Validación opcional: asegurar que el área exista en el tenant para evitar filtros
            // “fantasma”.
            // Si prefieres permitir "lista vacía" sin 404, elimina esta validación.
            ensureAreaExistsInTenant(orgId, areaId);

            devices = dispositivoRepo.listByOrganizacionAndArea(orgId, areaId, page, size);
            total = dispositivoRepo.countByOrganizacionAndArea(orgId, areaId);
        }

        List<DispositivoResponse> items = devices.stream().map(this::toResponse).toList();
        return PageResponse.of(items, page, size, total);
    }

    /**
     * Obtiene un dispositivo específico dentro del tenant.
     *
     * @param orgId identificador del tenant
     * @param dispositivoId identificador del dispositivo
     * @return DTO del dispositivo
     * @throws NotFoundException si no existe o no pertenece al tenant
     */
    @Transactional
    public DispositivoResponse get(UUID orgId, UUID dispositivoId) {
        return toResponse(getDispositivoOrThrow(orgId, dispositivoId));
    }

    /**
     * Crea un dispositivo dentro del tenant.
     *
     * <p>
     * Reglas aplicadas:
     * <ul>
     * <li>El área debe existir y pertenecer al tenant.</li>
     * <li>Si se envía {@code identificadorExterno}, debe ser único global.</li>
     * </ul>
     * </p>
     *
     * @param orgId identificador del tenant
     * @param req request de creación (no null)
     * @return dispositivo creado
     */
    @Transactional
    public DispositivoResponse create(UUID orgId, DispositivoUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Area area = getAreaOrThrow(orgId, req.idArea());

        ensureIdentificadorExternoUnicoOnCreate(req.identificadorExterno());

        Dispositivo d = new Dispositivo();
        d.setIdDispositivo(UUID.randomUUID());
        d.assignTenant(orgId);
        d.setAreaReferencia(area);
        d.setNombre(normalize(req.nombre()));
        d.setModelo(normalizeOrNull(req.modelo()));
        d.setIdentificadorExterno(normalizeOrNull(req.identificadorExterno()));
        d.setEstadoActivo(Boolean.TRUE.equals(req.estadoActivo()));

        try {
            dispositivoRepo.persist(d);
            dispositivoRepo.flush();
            return toResponse(d);
        } catch (RuntimeException e) {
            if (isUniqueViolation(e)) {
                throw conflictIdentificadorExterno();
            }
            throw e;
        }
    }

    /**
     * Actualiza un dispositivo existente dentro del tenant.
     *
     * <p>
     * Reglas aplicadas:
     * <ul>
     * <li>El dispositivo debe existir y pertenecer al tenant.</li>
     * <li>El área debe existir y pertenecer al tenant (si se cambia o se reafirma).</li>
     * <li>Si se envía {@code identificadorExterno}, debe ser único global excluyendo el
     * actual.</li>
     * </ul>
     * </p>
     *
     * @param orgId identificador del tenant
     * @param dispositivoId id del dispositivo
     * @param req request de actualización (no null)
     * @return dispositivo actualizado
     */
    @Transactional
    public DispositivoResponse update(UUID orgId, UUID dispositivoId,
            DispositivoUpsertRequest req) {
        Objects.requireNonNull(req, "req es obligatorio");

        Dispositivo d = getDispositivoOrThrow(orgId, dispositivoId);

        Area area = getAreaOrThrow(orgId, req.idArea());

        ensureIdentificadorExternoUnicoOnUpdate(req.identificadorExterno(), dispositivoId);

        d.setAreaReferencia(area);
        d.setNombre(normalize(req.nombre()));
        d.setModelo(normalizeOrNull(req.modelo()));
        d.setIdentificadorExterno(normalizeOrNull(req.identificadorExterno()));
        d.setEstadoActivo(Boolean.TRUE.equals(req.estadoActivo()));

        try {
            dispositivoRepo.flush();
            return toResponse(d);
        } catch (RuntimeException e) {
            if (isUniqueViolation(e)) {
                throw conflictIdentificadorExterno();
            }
            throw e;
        }
    }

    /**
     * Elimina un dispositivo dentro del tenant (delete físico).
     *
     * @param orgId identificador del tenant
     * @param dispositivoId id del dispositivo
     * @throws NotFoundException si no existe o no pertenece al tenant
     */
    @Transactional
    public void delete(UUID orgId, UUID dispositivoId) {
        Dispositivo d = getDispositivoOrThrow(orgId, dispositivoId);
        dispositivoRepo.delete(d);
    }

    // -------------------------
    // Helpers privados
    // -------------------------

    private Dispositivo getDispositivoOrThrow(UUID orgId, UUID dispositivoId) {
        return dispositivoRepo.findByIdAndOrganizacion(dispositivoId, orgId)
                .orElseThrow(() -> new NotFoundException("Dispositivo no encontrado"));
    }

    private Area getAreaOrThrow(UUID orgId, UUID areaId) {
        // Asumo que AreaRepository tiene findByIdAndOrganizacion(areaId, orgId) como el de
        // visitantes/areas.
        return areaRepo.findByIdAndOrganizacion(areaId, orgId).orElseThrow(
                () -> new NotFoundException("Área no encontrada para la organización"));
    }

    private void ensureAreaExistsInTenant(UUID orgId, UUID areaId) {
        if (areaRepo.findByIdAndOrganizacion(areaId, orgId).isEmpty()) {
            throw new NotFoundException("Área no encontrada para la organización");
        }
    }

    private void ensureIdentificadorExternoUnicoOnCreate(String identificadorExterno) {
        String v = normalizeOrNull(identificadorExterno);
        if (v != null && dispositivoRepo.existsByIdentificadorExterno(v)) {
            throw conflictIdentificadorExterno();
        }
    }

    private void ensureIdentificadorExternoUnicoOnUpdate(String identificadorExterno,
            UUID dispositivoId) {
        String v = normalizeOrNull(identificadorExterno);
        if (v != null
                && dispositivoRepo.existsByIdentificadorExternoExcludingId(v, dispositivoId)) {
            throw conflictIdentificadorExterno();
        }
    }

    private WebApplicationException conflictIdentificadorExterno() {
        return new WebApplicationException("Ya existe un dispositivo con ese identificador externo",
                Response.Status.CONFLICT);
    }

    private boolean isUniqueViolation(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains(UK_DISPOSITIVO_IDENTIFICADOR_EXTERNO)) {
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

    private String normalize(String s) {
        String v = (s == null) ? null : s.trim();
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Valor requerido");
        }
        return v;
    }

    private String normalizeOrNull(String s) {
        String v = (s == null) ? null : s.trim();
        return (v == null || v.isBlank()) ? null : v;
    }

    private DispositivoResponse toResponse(Dispositivo d) {
        return new DispositivoResponse(d.getIdDispositivo(), d.getIdOrganizacion(), d.getIdArea(),
                d.getNombre(), d.getModelo(), d.getIdentificadorExterno(), d.isEstadoActivo(),
                d.getCreadoEnUtc(), d.getActualizadoEnUtc());
    }
}
