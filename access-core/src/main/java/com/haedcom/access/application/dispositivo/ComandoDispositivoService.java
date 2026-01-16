package com.haedcom.access.application.dispositivo;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.jboss.logging.Logger;
import com.haedcom.access.application.dispositivo.dto.ResultadoComandoRequest;
import com.haedcom.access.domain.enums.EstadoComandoDispositivo;
import com.haedcom.access.domain.events.ComandoDispositivoEjecutado;
import com.haedcom.access.domain.events.DomainEventPublisher;
import com.haedcom.access.domain.model.ComandoDispositivo;
import com.haedcom.access.domain.repo.ComandoDispositivoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class ComandoDispositivoService {

        private static final Logger LOG = Logger.getLogger(ComandoDispositivoService.class);

        private final ComandoDispositivoRepository comandoRepo;
        private final DomainEventPublisher eventPublisher;
        private final Clock clock;

        public ComandoDispositivoService(ComandoDispositivoRepository comandoRepo,
                        DomainEventPublisher eventPublisher, Clock clock) {

                this.comandoRepo =
                                Objects.requireNonNull(comandoRepo, "comandoRepo es obligatorio");
                this.eventPublisher = Objects.requireNonNull(eventPublisher,
                                "eventPublisher es obligatorio");
                this.clock = (clock != null) ? clock : Clock.systemUTC();
        }

        @Transactional
        public void confirmarOFallar(UUID orgId, UUID idComando, ResultadoComandoRequest req) {
                Objects.requireNonNull(orgId, "orgId es obligatorio");
                Objects.requireNonNull(idComando, "idComando es obligatorio");
                Objects.requireNonNull(req, "req es obligatorio");
                Objects.requireNonNull(req.estado(), "req.estado es obligatorio");

                ComandoDispositivo cmd = comandoRepo.findByIdAndOrgWithIntento(orgId, idComando)
                                .orElseThrow(() -> new NotFoundException(
                                                "Comando no encontrado para la organización"));

                EstadoComandoDispositivo actual = cmd.getEstado();
                EstadoComandoDispositivo entrante = req.estado();

                if (isFinal(actual)) {
                        if (actual == entrante) {
                                LOG.debugf("Resultado idempotente ignorado orgId=%s idComando=%s estado=%s",
                                                orgId, idComando, actual);
                        } else {
                                LOG.warnf("Resultado tardío ignorado orgId=%s idComando=%s estadoActual=%s estadoEntrante=%s",
                                                orgId, idComando, actual, entrante);
                        }
                        return;
                }

                OffsetDateTime when = (req.ocurridoEnUtc() != null) ? req.ocurridoEnUtc()
                                : OffsetDateTime.now(clock);

                cmd.setEstado(entrante);
                cmd.setConfirmadoEnUtc(when);
                cmd.setCodigoError(req.codigoError());
                cmd.setDetalleError(req.detalleError());

                String externalId = normalize(req.idEjecucionExterna());
                if (externalId != null && isBlank(cmd.getIdEjecucionExterna())) {
                        cmd.setIdEjecucionExterna(externalId);
                }

                comandoRepo.persist(cmd);
                comandoRepo.flush();

                UUID intentoId = cmd.getIntento().getIdIntento();

                eventPublisher.publish(new ComandoDispositivoEjecutado(UUID.randomUUID(), orgId,
                                cmd.getIdComando(), intentoId, cmd.getIdDispositivo(),
                                cmd.getEstado(), when, cmd.getCodigoError(), cmd.getDetalleError(),
                                cmd.getIdEjecucionExterna()));
        }

        private static boolean isFinal(EstadoComandoDispositivo estado) {
                return estado == EstadoComandoDispositivo.EJECUTADO_OK
                                || estado == EstadoComandoDispositivo.EJECUTADO_ERROR
                                || estado == EstadoComandoDispositivo.TIMEOUT;
        }

        private static String normalize(String s) {
                if (s == null)
                        return null;
                String v = s.trim();
                return v.isBlank() ? null : v;
        }

        private static boolean isBlank(String s) {
                return s == null || s.trim().isBlank();
        }
}
