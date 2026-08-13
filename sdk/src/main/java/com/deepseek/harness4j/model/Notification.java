package com.deepseek.harness4j.model;

import java.util.Map;
import java.util.Objects;

/**
 * A JSON-RPC notification received from the runtime.
 *
 * <p>Python (dataclass with {@code slots=True}):
 * <pre>{@code
 * @dataclass(slots=True)
 * class Notification:
 *     method: str
 *     payload: JsonObject
 * }</pre>
 */
public record Notification(String method, Map<String, Object> payload) {

    public Notification {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(payload, "payload");
    }
}
