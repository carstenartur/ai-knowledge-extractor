package example;
import org.springframework.web.bind.annotation.GetMapping;
public class Resource {
  @GetMapping("/api/user") public String endpoint0() { return "ok"; }
  @GetMapping("/api/items") public String endpoint1() { return "ok"; }
  @GetMapping("/api/actions") public String endpoint2() { return "ok"; }
}
