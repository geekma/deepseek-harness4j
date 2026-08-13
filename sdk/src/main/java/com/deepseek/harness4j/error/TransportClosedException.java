package com.deepseek.harness4j.error;

/**
 * Raised when the runtime subprocess exits or closes stdout.
 *
 * <p>Python: {@code class TransportClosedError(HarnessError)}.
 */
public class TransportClosedException extends HarnessException {

    public TransportClosedException(String message) {
        super(message);
    }

    public TransportClosedException(String message, Throwable cause) {
        super(message, cause);
    }
}
