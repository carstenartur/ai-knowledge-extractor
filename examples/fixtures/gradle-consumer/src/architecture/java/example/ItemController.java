package example;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class ItemController {
    @GetMapping("/api/items")
    public String items() { return "items"; }
}
