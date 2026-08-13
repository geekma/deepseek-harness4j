package com.deepseek.harness4j.model;

import java.util.Map;
import java.util.Objects;

/**
 * An incoming JSON-RPC request from the runtime (a request the client must answer).
 *
 * <p>Python (dataclass with {@code slots=True}):
 * <pre>{@code
 * @dataclass(slots=True)
 * class IncomingRequest:
 *     id: str | int
 *     method: str
 *     payload: JsonObject
 * }</pre>
 *
 * <p>The {@code id} is a JSON-RPC request id: a string or an integer, hence
 * {@link Object}. Use {@link #idAsString()} to normalize for map lookups.
 */
public record IncomingRequest(Object id, String method, Map<String, Object> payload) {

    public IncomingRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(payload, "payload");
    }

    /**
     * @return the request id rendered as a string, matching how the Python
     *         client normalizes ids when indexing its response waiters.
     */
    public String idAsString() {
        return String.valueOf(id);
    }
}
