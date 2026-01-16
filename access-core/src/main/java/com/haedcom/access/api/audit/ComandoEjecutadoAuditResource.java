package com.haedcom.access.api.audit;

import java.util.UUID;
import com.haedcom.access.application.audit.AuditIngestService;
import com.haedcom.access.domain.events.ComandoDispositivoEjecutado;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/internal/organizaciones/{orgId}/audit/comandos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ComandoEjecutadoAuditResource {

    @Inject
    AuditIngestService ingest;

    @POST
    @Path("/{idComando}/ejecutado")
    public Response auditComandoEjecutado(@PathParam("orgId") UUID orgId,
            @PathParam("idComando") UUID idComando, @Valid ComandoDispositivoEjecutado body) {

        /*
         * Defensa: - orgId e idComando se toman exclusivamente del path. - eventId se genera aquí
         * para deduplicación robusta. - El resto del payload se acepta del body.
         */
        ComandoDispositivoEjecutado ev = new ComandoDispositivoEjecutado(UUID.randomUUID(), // eventId
                orgId, // orgId (path)
                idComando, // idComando (path)
                body.idIntento(), body.idDispositivo(), body.estadoFinal(), body.ejecutadoEnUtc(),
                body.codigoError(), body.detalleError(), body.idEjecucionExterna());

        ingest.ingest(ev);
        return Response.accepted().build();
    }
}
