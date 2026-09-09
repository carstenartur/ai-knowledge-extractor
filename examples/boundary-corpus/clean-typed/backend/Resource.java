package example;
import org.springframework.web.bind.annotation.GetMapping;
public class Resource {
  @GetMapping("/api/view") public String endpoint0() { return "ok"; }
}
