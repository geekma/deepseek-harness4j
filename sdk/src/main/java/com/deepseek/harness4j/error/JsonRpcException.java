package com.deepseek.harness4j.error;

/**
 * Raised when the runtime returns a JSON-RPC error response.
 *
 * <p>Python:
 * <pre>{@code
 * class JsonRpcError(HarnessError):
 *     def __init__(self, code: int | None, message: str, data: object | None = None) -> None:
 *         super().__init__(message)
 *         self.code = code
 *         self.message = message
 *         self.data = data
 * }</pre>
 */
public class JsonRpcException extends HarnessException {

    private final Integer code;
    private final String message;
    private final Object data;

    public JsonRpcException(Integer code, String message, Object data) {
        super(message);
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** @return the JSON-RPC error code, or {@code null} when the wire omitted it. */
    public Integer code() {
        return code;
    }

    /** @return the JSON-RPC error message. */
    public String message() {
        return message;
    }

    /** @return the JSON-RPC error data payload, or {@code null} when absent. */
    public Object data() {
        return data;
    }
}
