package com.haedcom.access.application.residente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
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
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;

@ExtendWith(MockitoExtension.class)
class ResidenteServiceTest {

        private ResidenteRepository residenteRepo;
        private OrganizacionRepository orgRepo;
        private ResidenteService service;

        @BeforeEach
        void setup() {
                residenteRepo = mock(ResidenteRepository.class);
                orgRepo = mock(OrganizacionRepository.class);
                service = new ResidenteService(residenteRepo, orgRepo);
        }

        private Organizacion org(UUID id) {
                return Organizacion.crear(id, "Hospital Central", "ACTIVO");
        }

        private ResidenteUpsertRequest reqValida() {
                return new ResidenteUpsertRequest("Juan Pérez", TipoDocumentoIdentidad.CC, "123",
                                "juan@mail.com", "3000000000", "Apto 101");
        }

        // -------------------------
        // list
        // -------------------------

        @Test
        void list_deberiaMapearResultados_yRetornarPaginacion() {
                UUID orgId = UUID.randomUUID();
                Organizacion org = org(orgId);

                Residente r1 = Residente.crear(org, "A", TipoDocumentoIdentidad.CC, "1", null, null,
                                null);
                Residente r2 = Residente.crear(org, "B", TipoDocumentoIdentidad.CE, "2", null, null,
                                null);

                String q = "a";
                TipoDocumentoIdentidad tipo = null;
                String numero = null;
                EstadoResidente estado = null;
                String sort = "nombre";
                String dir = "asc";
                int page = 0;
                int size = 10;

                when(residenteRepo.searchByOrganizacion(orgId, q, tipo, numero, estado, sort, dir,
                                page, size)).thenReturn(List.of(r1, r2));
                when(residenteRepo.countSearchByOrganizacion(orgId, q, tipo, numero, estado))
                                .thenReturn(2L);

                PageResponse<ResidenteResponse> out =
                                service.list(orgId, q, tipo, numero, estado, sort, dir, page, size);

                assertThat(out.items()).hasSize(2);
                assertThat(out.total()).isEqualTo(2);
                assertThat(out.page()).isEqualTo(0);
                assertThat(out.size()).isEqualTo(10);

                assertThat(out.items().get(0).idOrganizacion()).isEqualTo(orgId);
                assertThat(out.items().get(1).idOrganizacion()).isEqualTo(orgId);

                verify(residenteRepo).searchByOrganizacion(orgId, q, tipo, numero, estado, sort,
                                dir, page, size);
                verify(residenteRepo).countSearchByOrganizacion(orgId, q, tipo, numero, estado);
                verifyNoMoreInteractions(residenteRepo);
                verifyNoInteractions(orgRepo);
        }

        // -------------------------
        // get
        // -------------------------

        @Test
        void get_deberiaLanzar404_siNoExiste() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                when(residenteRepo.findByIdAndOrganizacion(residenteId, orgId))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.get(orgId, residenteId))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessageContaining("Residente no encontrado");

                verify(residenteRepo).findByIdAndOrganizacion(residenteId, orgId);
                verifyNoMoreInteractions(residenteRepo);
                verifyNoInteractions(orgRepo);
        }

        @Test
        void get_deberiaRetornarResidente_siExiste() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();
                Organizacion org = org(orgId);

                Residente existente = Residente.crear(org, "X", TipoDocumentoIdentidad.CC, "1",
                                null, null, null);

                when(residenteRepo.findByIdAndOrganizacion(residenteId, orgId))
                                .thenReturn(Optional.of(existente));

                ResidenteResponse out = service.get(orgId, residenteId);

                assertThat(out.idOrganizacion()).isEqualTo(orgId);
                assertThat(out.nombre()).isEqualTo("X");

                verify(residenteRepo).findByIdAndOrganizacion(residenteId, orgId);
                verifyNoMoreInteractions(residenteRepo);
                verifyNoInteractions(orgRepo);
        }

        // -------------------------
        // create
        // -------------------------

        @Test
        void create_deberiaLanzar404_siOrganizacionNoExiste() {
                UUID orgId = UUID.randomUUID();

                when(orgRepo.findById(orgId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.create(orgId, reqValida()))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessageContaining("Organización no encontrada");

                verify(orgRepo).findById(orgId);
                verifyNoInteractions(residenteRepo);
                verifyNoMoreInteractions(orgRepo);
        }

        @Test
        void create_deberiaLanzar409_siDocumentoYaExiste_porValidacionPrevia() {
                UUID orgId = UUID.randomUUID();
                Organizacion org = org(orgId);
                ResidenteUpsertRequest req = reqValida();

                when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));
                when(residenteRepo.existsByDocumento(orgId, req.tipoDocumento(),
                                req.numeroDocumento())).thenReturn(true);

                assertThatThrownBy(() -> service.create(orgId, req))
                                .isInstanceOf(WebApplicationException.class).satisfies(ex -> {
                                        WebApplicationException w = (WebApplicationException) ex;
                                        assertThat(w.getResponse().getStatus()).isEqualTo(409);
                                });

                verify(orgRepo).findById(orgId);
                verify(residenteRepo).existsByDocumento(orgId, req.tipoDocumento(),
                                req.numeroDocumento());
                verify(residenteRepo, never()).persist(any());
                verify(residenteRepo, never()).flush();
                verifyNoMoreInteractions(residenteRepo, orgRepo);
        }

        @Test
        void create_deberiaPersistir_yFlush_siTodoOk() {
                UUID orgId = UUID.randomUUID();
                Organizacion org = org(orgId);
                ResidenteUpsertRequest req = reqValida();

                when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));
                when(residenteRepo.existsByDocumento(orgId, req.tipoDocumento(),
                                req.numeroDocumento())).thenReturn(false);

                ResidenteResponse out = service.create(orgId, req);

                assertThat(out.idOrganizacion()).isEqualTo(orgId);
                assertThat(out.nombre()).isEqualTo("Juan Pérez");
                assertThat(out.tipoDocumento()).isEqualTo(TipoDocumentoIdentidad.CC);
                assertThat(out.numeroDocumento()).isEqualTo("123");
                assertThat(out.idResidente()).isNotNull();

                ArgumentCaptor<Residente> captor = ArgumentCaptor.forClass(Residente.class);
                verify(residenteRepo).persist(captor.capture());
                verify(residenteRepo).flush();

                Residente persisted = captor.getValue();
                assertThat(persisted.getIdOrganizacion()).isEqualTo(orgId);
                assertThat(persisted.getNombre()).isEqualTo("Juan Pérez");
                assertThat(persisted.getNumeroDocumento()).isEqualTo("123");

                verify(orgRepo).findById(orgId);
                verify(residenteRepo).existsByDocumento(orgId, req.tipoDocumento(),
                                req.numeroDocumento());
                verifyNoMoreInteractions(residenteRepo, orgRepo);
        }

        @Test
        void create_deberiaTraducir409_siFlushFalla_porNombreConstraint() {
                UUID orgId = UUID.randomUUID();
                Organizacion org = org(orgId);
                ResidenteUpsertRequest req = reqValida();

                when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));
                when(residenteRepo.existsByDocumento(orgId, req.tipoDocumento(),
                                req.numeroDocumento())).thenReturn(false);

                doThrow(new RuntimeException("violates constraint ux_residente_doc"))
                                .when(residenteRepo).flush();

                assertThatThrownBy(() -> service.create(orgId, req))
                                .isInstanceOf(WebApplicationException.class).satisfies(ex -> {
                                        WebApplicationException w = (WebApplicationException) ex;
                                        assertThat(w.getResponse().getStatus()).isEqualTo(409);
                                });

                verify(residenteRepo).persist(any(Residente.class));
                verify(residenteRepo).flush();
        }

        @Test
        void create_deberiaTraducir409_siFlushFalla_porSqlState23505() {
                UUID orgId = UUID.randomUUID();
                Organizacion org = org(orgId);
                ResidenteUpsertRequest req = reqValida();

                when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));
                when(residenteRepo.existsByDocumento(orgId, req.tipoDocumento(),
                                req.numeroDocumento())).thenReturn(false);

                SQLException sql = new SQLException("dup", "23505");
                RuntimeException root = new RuntimeException(sql);
                doThrow(root).when(residenteRepo).flush();

                assertThatThrownBy(() -> service.create(orgId, req))
                                .isInstanceOf(WebApplicationException.class).satisfies(ex -> {
                                        WebApplicationException w = (WebApplicationException) ex;
                                        assertThat(w.getResponse().getStatus()).isEqualTo(409);
                                });

                verify(residenteRepo).persist(any(Residente.class));
                verify(residenteRepo).flush();
        }

        // -------------------------
        // update
        // -------------------------

        @Test
        void update_deberiaLanzar404_siResidenteNoExiste() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                when(residenteRepo.findByIdAndOrganizacion(residenteId, orgId))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.update(orgId, residenteId, reqValida()))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessageContaining("Residente no encontrado");

                verify(residenteRepo).findByIdAndOrganizacion(residenteId, orgId);
                verifyNoMoreInteractions(residenteRepo);
                verifyNoInteractions(orgRepo);
        }

        @Test
        void update_deberiaLanzar409_siDocumentoEnConflicto_porValidacionPrevia() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();
                Organizacion org = org(orgId);

                Residente existente = Residente.crear(org, "Viejo", TipoDocumentoIdentidad.CC, "1",
                                null, null, null);
                ResidenteUpsertRequest req = reqValida();

                when(residenteRepo.findByIdAndOrganizacion(residenteId, orgId))
                                .thenReturn(Optional.of(existente));
                when(residenteRepo.existsByDocumentoExcludingId(orgId, req.tipoDocumento(),
                                req.numeroDocumento(), residenteId)).thenReturn(true);

                assertThatThrownBy(() -> service.update(orgId, residenteId, req))
                                .isInstanceOf(WebApplicationException.class).satisfies(ex -> {
                                        WebApplicationException w = (WebApplicationException) ex;
                                        assertThat(w.getResponse().getStatus()).isEqualTo(409);
                                });

                verify(residenteRepo).findByIdAndOrganizacion(residenteId, orgId);
                verify(residenteRepo).existsByDocumentoExcludingId(orgId, req.tipoDocumento(),
                                req.numeroDocumento(), residenteId);
                verify(residenteRepo, never()).flush();
                verifyNoMoreInteractions(residenteRepo);
                verifyNoInteractions(orgRepo);
        }

        @Test
        void update_deberiaActualizarDatos_yFlush_siTodoOk() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();
                Organizacion org = org(orgId);

                Residente existente = Residente.crear(org, "Viejo", TipoDocumentoIdentidad.CC, "1",
                                null, null, null);

                ResidenteUpsertRequest req = reqValida();

                when(residenteRepo.findByIdAndOrganizacion(residenteId, orgId))
                                .thenReturn(Optional.of(existente));
                when(residenteRepo.existsByDocumentoExcludingId(orgId, req.tipoDocumento(),
                                req.numeroDocumento(), residenteId)).thenReturn(false);

                ResidenteResponse out = service.update(orgId, residenteId, req);

                assertThat(out.nombre()).isEqualTo("Juan Pérez");
                assertThat(existente.getNombre()).isEqualTo("Juan Pérez");
                assertThat(existente.getNumeroDocumento()).isEqualTo("123");

                verify(residenteRepo).flush();
                verify(residenteRepo, never()).persist(any());
                verify(orgRepo, never()).findById(any());
        }

        @Test
        void update_deberiaTraducir409_siFlushFalla_porConstraint() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();
                Organizacion org = org(orgId);

                Residente existente = Residente.crear(org, "Viejo", TipoDocumentoIdentidad.CC, "1",
                                null, null, null);

                ResidenteUpsertRequest req = reqValida();

                when(residenteRepo.findByIdAndOrganizacion(residenteId, orgId))
                                .thenReturn(Optional.of(existente));
                when(residenteRepo.existsByDocumentoExcludingId(orgId, req.tipoDocumento(),
                                req.numeroDocumento(), residenteId)).thenReturn(false);

                doThrow(new RuntimeException("ux_residente_doc")).when(residenteRepo).flush();

                assertThatThrownBy(() -> service.update(orgId, residenteId, req))
                                .isInstanceOf(WebApplicationException.class).satisfies(ex -> {
                                        WebApplicationException w = (WebApplicationException) ex;
                                        assertThat(w.getResponse().getStatus()).isEqualTo(409);
                                });

                verify(residenteRepo).flush();
        }

        // -------------------------
        // delete
        // -------------------------

        @Test
        void delete_deberiaLanzar404_siNoExiste() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                when(residenteRepo.findByIdAndOrganizacion(residenteId, orgId))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.delete(orgId, residenteId))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessageContaining("Residente no encontrado");

                verify(residenteRepo).findByIdAndOrganizacion(residenteId, orgId);
                verify(residenteRepo, never()).delete(any());
                verifyNoMoreInteractions(residenteRepo);
                verifyNoInteractions(orgRepo);
        }

        @Test
        void delete_deberiaEliminar_siExiste() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();
                Organizacion org = org(orgId);

                Residente existente = Residente.crear(org, "X", TipoDocumentoIdentidad.CC, "1",
                                null, null, null);

                when(residenteRepo.findByIdAndOrganizacion(residenteId, orgId))
                                .thenReturn(Optional.of(existente));

                service.delete(orgId, residenteId);

                verify(residenteRepo).delete(existente);
                verifyNoMoreInteractions(residenteRepo);
                verifyNoInteractions(orgRepo);
        }

        // -------------------------
        // updateEstado
        // -------------------------

        @Test
        void updateEstado_deberiaLanzar404_siNoExiste() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();

                when(residenteRepo.findByIdAndOrganizacion(residenteId, orgId))
                                .thenReturn(Optional.empty());

                ResidenteEstadoRequest req = new ResidenteEstadoRequest(EstadoResidente.INACTIVO);

                assertThatThrownBy(() -> service.updateEstado(orgId, residenteId, req))
                                .isInstanceOf(NotFoundException.class)
                                .hasMessageContaining("Residente no encontrado");

                verify(residenteRepo).findByIdAndOrganizacion(residenteId, orgId);
                verify(residenteRepo, never()).flush();
        }

        @Test
        void updateEstado_deberiaActualizarEstado_yFlush() {
                UUID orgId = UUID.randomUUID();
                UUID residenteId = UUID.randomUUID();
                Organizacion org = org(orgId);

                Residente existente = Residente.crear(org, "X", TipoDocumentoIdentidad.CC, "1",
                                null, null, null);

                when(residenteRepo.findByIdAndOrganizacion(residenteId, orgId))
                                .thenReturn(Optional.of(existente));

                ResidenteEstadoRequest req = new ResidenteEstadoRequest(EstadoResidente.INACTIVO);

                ResidenteResponse out = service.updateEstado(orgId, residenteId, req);

                assertThat(existente.getEstado()).isEqualTo(EstadoResidente.INACTIVO);
                assertThat(out.estado()).isEqualTo(EstadoResidente.INACTIVO);

                verify(residenteRepo).flush();
                verifyNoMoreInteractions(residenteRepo);
                verifyNoInteractions(orgRepo);
        }
}
