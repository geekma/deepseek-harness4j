package com.deepseek.harness4j.error;

/**
 * Base exception for SDK and runtime failures.
 *
 * <p>Python: {@code class HarnessError(Exception)}.
 */
public class HarnessException extends RuntimeException {

    public HarnessException(String message) {
        super(message);
    }

    public HarnessException(String message, Throwable cause) {
        super(message, cause);
    }

    public HarnessException(Throwable cause) {
        super(cause);
    }
}
