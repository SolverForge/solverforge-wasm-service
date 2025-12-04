package ai.timefold.wasm.service;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/health")
public class HealthResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public HealthStatus health() {
        return new HealthStatus("UP");
    }

    @GET
    @Path("/ready")
    @Produces(MediaType.APPLICATION_JSON)
    public HealthStatus ready() {
        return new HealthStatus("UP");
    }

    @GET
    @Path("/live")
    @Produces(MediaType.APPLICATION_JSON)
    public HealthStatus live() {
        return new HealthStatus("UP");
    }

    public record HealthStatus(String status) {}
}
