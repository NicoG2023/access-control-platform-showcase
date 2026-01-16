package com.haedcom.access.testsupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haedcom.access.domain.enums.EstadoComandoDispositivo;
import com.haedcom.access.domain.enums.TipoComandoDispositivo;
import com.haedcom.access.domain.enums.TipoDireccionPaso;
import com.haedcom.access.domain.enums.TipoDocumentoIdentidad;
import com.haedcom.access.domain.enums.TipoMetodoAutenticacion;
import com.haedcom.access.domain.enums.TipoResultadoDecision;
import com.haedcom.access.domain.enums.TipoRolEnVisita;
import com.haedcom.access.domain.enums.TipoSujetoAcceso;
import com.haedcom.access.domain.model.Area;
import com.haedcom.access.domain.model.AuditLog;
import com.haedcom.access.domain.model.CatalogoMotivoDecision;
import com.haedcom.access.domain.model.ComandoDispositivo;
import com.haedcom.access.domain.model.DecisionAcceso;
import com.haedcom.access.domain.model.Dispositivo;
import com.haedcom.access.domain.model.IntentoAcceso;
import com.haedcom.access.domain.model.Organizacion;
import com.haedcom.access.domain.model.PersonaVisita;
import com.haedcom.access.domain.model.Residente;
import com.haedcom.access.domain.model.Visita;
import com.haedcom.access.domain.model.VisitantePreautorizado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestDataFactory {

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper om;

    public <E extends Enum<E>> E anyEnum(Class<E> enumType) {
        return enumType.getEnumConstants()[0];
    }

    public UUID newUuid() {
        return UUID.randomUUID();
    }

    public OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public JsonNode json(String raw) {
        try {
            return om.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------------
    // Persist helpers
    // -------------------------

    @Transactional
    public Organizacion persistOrg() {
        Organizacion org = Organizacion.crear(UUID.randomUUID(), "Org Test", "ACTIVO");
        em.persist(org);
        em.flush();
        return org;
    }

    @Transactional
    public Area persistArea(Organizacion org, String nombre) {
        Area a = Area.crear(org, nombre, null);
        em.persist(a);
        em.flush();
        return a;
    }

    @Transactional
    public Dispositivo persistDispositivo(Organizacion org, Area area,
            String identificadorExterno) {
        Dispositivo d = new Dispositivo();
        d.setIdDispositivo(UUID.randomUUID());
        d.setOrganizacionTenant(org);
        d.setAreaReferencia(area);
        d.setNombre("Disp Test");
        d.setModelo("M1");
        d.setIdentificadorExterno(identificadorExterno);
        d.setEstadoActivo(true);

        em.persist(d);
        em.flush();
        return d;
    }

    @Transactional
    public Residente persistResidente(Organizacion org, TipoDocumentoIdentidad tipoDoc,
            String numeroDoc) {
        Residente r = Residente.crear(org, "Residente Test", tipoDoc, numeroDoc, null, null, null);
        em.persist(r);
        em.flush();
        return r;
    }

    @Transactional
    public VisitantePreautorizado persistVisitante(Organizacion org, TipoDocumentoIdentidad tipoDoc,
            String numeroDoc, Residente residenteOrNull) {
        VisitantePreautorizado v = new VisitantePreautorizado();
        v.setIdVisitante(UUID.randomUUID());
        v.setOrganizacionTenant(org);
        v.setNombre("Visitante Test");
        v.setTipoDocumento(tipoDoc);
        v.setNumeroDocumento(numeroDoc);
        v.setCorreo("v@test.com");
        v.setTelefono("300");
        v.setResidente(residenteOrNull);

        em.persist(v);
        em.flush();
        return v;
    }

    @Transactional
    public Visita persistVisita(Organizacion org, Area areaDestino) {
        Visita v = new Visita();
        v.setIdVisita(UUID.randomUUID());
        v.setOrganizacionTenant(org);
        v.setAreaDestinoReferencia(areaDestino);
        v.setMotivo("Motivo");

        em.persist(v);
        em.flush();
        return v;
    }

    @Transactional
    public PersonaVisita persistPersonaVisita(Organizacion org, Visita visita) {
        PersonaVisita pv = new PersonaVisita();
        pv.setIdPersonaVisita(UUID.randomUUID());
        pv.setOrganizacionTenant(org);

        pv.setIdVisita(visita.getIdVisita());
        pv.setVisita(visita);

        pv.setRolEnVisita(anyEnum(TipoRolEnVisita.class));
        pv.setNombre("Persona Test");

        em.persist(pv);
        em.flush();
        return pv;
    }

    @Transactional
    public IntentoAcceso persistIntento(Organizacion org, Area area, Dispositivo disp,
            String claveIdempotencia) {
        IntentoAcceso ia = new IntentoAcceso();
        ia.setIdIntento(UUID.randomUUID());
        ia.setOrganizacionTenant(org);

        ia.setIdArea(area.getIdArea());
        ia.setArea(area);

        ia.setIdDispositivo(disp.getIdDispositivo());
        ia.setDispositivo(disp);

        ia.setDireccionPaso(anyEnum(TipoDireccionPaso.class));
        ia.setMetodoAutenticacion(anyEnum(TipoMetodoAutenticacion.class));
        ia.setTipoSujeto(anyEnum(TipoSujetoAcceso.class));

        ia.setReferenciaCredencial("ref-1");
        ia.setCargaCruda(json("{\"raw\":true}"));

        ia.setClaveIdempotencia(claveIdempotencia);
        ia.setIdGatewaySolicitud("gw-1");
        ia.setOcurridoEnUtc(nowUtc().minusSeconds(10));

        em.persist(ia);
        em.flush();
        return ia;
    }

    @Transactional
    public CatalogoMotivoDecision persistMotivo(String codigo) {
        CatalogoMotivoDecision m = new CatalogoMotivoDecision(codigo, "Desc " + codigo);
        em.persist(m);
        em.flush();
        return m;
    }


    @Transactional
    public DecisionAcceso persistDecision(Organizacion org, IntentoAcceso intento,
            CatalogoMotivoDecision motivo) {
        DecisionAcceso d = new DecisionAcceso();
        d.setIdDecision(UUID.randomUUID());
        d.assignTenant(org.getIdOrganizacion());

        d.setIntento(intento);
        d.setDecididoEnUtc(nowUtc());
        d.setResultado(anyEnum(TipoResultadoDecision.class));
        d.setMotivo(motivo);

        em.persist(d);
        em.flush();
        return d;
    }

    @Transactional
    public ComandoDispositivo persistComando(Organizacion org, IntentoAcceso intento,
            Dispositivo disp, String claveIdem) {
        ComandoDispositivo c = new ComandoDispositivo();
        c.setIdComando(UUID.randomUUID());
        c.assignTenant(org.getIdOrganizacion());

        c.setIntento(intento);
        c.setIdDispositivo(disp.getIdDispositivo());
        c.setDispositivo(disp);

        c.setComando(anyEnum(TipoComandoDispositivo.class));
        c.setEstado(anyEnum(EstadoComandoDispositivo.class));
        c.setEnviadoEnUtc(nowUtc());
        c.setClaveIdempotencia(claveIdem);

        em.persist(c);
        em.flush();
        return c;
    }

    @Transactional
    public AuditLog persistAuditLog(UUID orgId, String eventType, String aggregateId,
            OffsetDateTime occurredAtUtc) {
        AuditLog a = AuditLog.crear(orgId, eventType, "Agg", aggregateId, "corr-1", occurredAtUtc,
                "{\"ok\":true}");
        em.persist(a);
        em.flush();
        return a;
    }
}
