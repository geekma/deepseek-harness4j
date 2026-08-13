package com.deepseek.harness4j.example;

import com.deepseek.harness4j.RunResult;
import com.deepseek.harness4j.spring.DeepSeekHarnessTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Spring MVC controller demonstrating how the harness template is called from a web layer.
 *
 * <p>The SDK itself is framework-free; this controller is the thin adapter that turns an HTTP
 * request into one {@link com.deepseek.harness4j.Session#run} interval (the port of the
 * Python SDK's programmatic use from an application workflow).
 */
@RestController
@RequestMapping("/api/harness")
public class HarnessController {

    private final DeepSeekHarnessTemplate template;

    public HarnessController(DeepSeekHarnessTemplate template) {
        this.template = template;
    }

    /** Run one agent turn and return the result fields of the Python {@code RunResult}. */
    @PostMapping("/run")
    public RunResult run(@RequestBody RunRequest request) {
        return template.run(request.input(), request.sessionId(), null);
    }

    /** Request body mirroring the Python {@code harness.run(input, session_id=...)} call. */
    public record RunRequest(String input, String sessionId) {
    }
}
