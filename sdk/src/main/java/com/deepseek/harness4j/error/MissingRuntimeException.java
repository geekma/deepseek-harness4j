package com.deepseek.harness4j.error;

/**
 * Raised when the bundled DeepSeek Harness SDK runtime cannot be located.
 *
 * <p>Python raises {@code FileNotFoundError} (an {@code OSError}); the message
 * names the acquisition routes. Python exceptions are unchecked, so the port
 * uses an unchecked runtime exception instead of the checked
 * {@link java.io.FileNotFoundException}.
 */
public class MissingRuntimeException extends HarnessException {

    public MissingRuntimeException(String message) {
        super(message);
    }

    public MissingRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
