package xyz.bergw;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @RequestMapping("/{thing}")
    String hello(@PathVariable String thing) {
        return "test message: " + thing;
    }

}