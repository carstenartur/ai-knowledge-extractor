package example;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
@Path("/api")
public class Resource {
  @GET @Path("/items") public String endpoint0() { return "ok"; }
}
