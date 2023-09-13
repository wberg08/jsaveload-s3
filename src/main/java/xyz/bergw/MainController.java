package xyz.bergw;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class MainController {

    @RequestMapping("/")
    void handleFoo(HttpServletResponse response) throws IOException {
        response.sendRedirect("http://static.bergw.xyz");
    }

    @RequestMapping("/{thing}")
    String hello(@PathVariable String thing) {
        return "test message: " + thing;
    }

}