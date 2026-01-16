package com.haedcom.access.api.acceso;

import java.util.UUID;
import com.haedcom.access.application.acceso.AccesoService;
import com.haedcom.access.application.acceso.AccesoService.RegistrarIntentoRequest;
import com.haedcom.access.application.acceso.AccesoService.RegistrarIntentoResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Recurso REST para el registro de intentos de acceso.
 *
 * <p>
 * Este endpoint está diseñado para ser consumido por:
 * <ul>
 * <li>Dispositivos de control de acceso</li>
 * <li>Gateways biométricos</li>
 * <li>Servicios IoT</li>
 * </ul>
 * </p>
 *
 * <p>
 * Es idempotente mediante {@code claveIdempotencia}.
 * </p>
 */
@ApplicationScoped
@Path("/organizaciones/{orgId}/accesos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AccesoResource {

    private final AccesoService accesoService;

    @Inject
    public AccesoResource(AccesoService accesoService) {
        this.accesoService = accesoService;
    }

    /**
     * Registra un intento de acceso y retorna la decisión tomada.
     *
     * @param orgId tenant
     * @param request payload enviado por el dispositivo/gateway
     * @return resultado resumido del acceso
     */
    @POST
    @Path("/intentos")
    public RegistrarIntentoResult registrarIntento(@PathParam("orgId") UUID orgId,
            @Valid RegistrarIntentoRequest request) {

        return accesoService.registrarIntento(orgId, request);
    }
}
