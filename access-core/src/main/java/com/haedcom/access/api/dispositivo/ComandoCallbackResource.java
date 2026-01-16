// =====================================================
// 4) Resource REST: ComandoCallbackResource
// POST /organizaciones/{orgId}/comandos/{idComando}/resultado
// package: com.haedcom.access.api.dispositivo
// =====================================================
package com.haedcom.access.api.dispositivo;

import java.util.UUID;
import org.jboss.logging.Logger;
import com.haedcom.access.application.dispositivo.ComandoDispositivoService;
import com.haedcom.access.application.dispositivo.dto.ResultadoComandoRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Callback REST para que el microservicio de dispositivos reporte al core el resultado (outcome) de
 * ejecución de un comando.
 *
 * <p>
 * Ruta:
 * </p>
 * <ul>
 * <li>POST {@code /organizaciones/{orgId}/comandos/{idComando}/resultado}</li>
 * </ul>
 *
 * <h2>Idempotencia</h2>
 * <p>
 * Este endpoint es <b>idempotente</b>: si el comando ya está en un estado final, el core responde
 * exitosamente sin reprocesar ni emitir eventos duplicados.
 * </p>
 *
 * <h2>Trazabilidad</h2>
 * <p>
 * Se soporta el header opcional {@code X-Request-Id} para correlación extremo a extremo entre:
 * </p>
 * <ul>
 * <li>core (access-core)</li>
 * <li>microservicio de dispositivos</li>
 * <li>dispositivo físico (si aplica)</li>
 * </ul>
 *
 * <p>
 * Nota: el core no depende de este header para la idempotencia (eso se resuelve por estado del
 * comando y/o claves idempotentes). Es únicamente para observabilidad.
 * </p>
 */
@ApplicationScoped
@Path("/organizaciones/{orgId}/comandos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ComandoCallbackResource {

    private static final Logger LOG = Logger.getLogger(ComandoCallbackResource.class);

    /**
     * Header de correlación recomendado para trazabilidad.
     *
     * <p>
     * Si el cliente (devices-service) lo envía, se puede propagar a logs y/o a auditoría para
     * depurar incidentes.
     * </p>
     */
    public static final String HDR_REQUEST_ID = "X-Request-Id";

    private final ComandoDispositivoService comandoService;

    @Inject
    public ComandoCallbackResource(ComandoDispositivoService comandoService) {
        this.comandoService = comandoService;
    }

    /**
     * Registra el resultado definitivo de un comando reportado por el microservicio de
     * dispositivos.
     *
     * <p>
     * Respuesta sugerida: {@code 204 No Content}. Si prefieres retornar un body para debugging,
     * puedes cambiar a {@code 200 OK} con un DTO de confirmación.
     * </p>
     *
     * @param orgId tenant (organización)
     * @param idComando id del comando emitido por el core
     * @param requestId correlación opcional del request (header {@code X-Request-Id})
     * @param req resultado reportado (validado con Bean Validation)
     * @return {@code 204 No Content}
     */
    @POST
    @Path("/{idComando}/resultado")
    public Response registrarResultado(@PathParam("orgId") UUID orgId,
            @PathParam("idComando") UUID idComando, @HeaderParam(HDR_REQUEST_ID) String requestId,
            @Valid ResultadoComandoRequest req) {

        LOG.debugf("Callback resultado comando orgId=%s idComando=%s requestId=%s", orgId,
                idComando, requestId);

        comandoService.confirmarOFallar(orgId, idComando, req);
        return Response.noContent().build();
    }
}
