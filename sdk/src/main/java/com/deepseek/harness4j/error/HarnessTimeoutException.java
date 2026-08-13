package com.deepseek.harness4j.error;

/**
 * Raised when a request times out waiting for the DeepSeek Harness runtime.
 *
 * <p>Python raises the built-in {@code TimeoutError}. Java has no equivalent
 * unchecked built-in (the {@link java.util.concurrent.TimeoutException} is
 * checked), so the port introduces this dedicated runtime exception; the
 * message carries the same runtime diagnostics suffix.
 */
public class HarnessTimeoutException extends HarnessException {

    public HarnessTimeoutException(String message) {
        super(message);
    }
}
