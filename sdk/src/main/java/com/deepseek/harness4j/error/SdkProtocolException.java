package com.deepseek.harness4j.error;

/**
 * Raised when the runtime sends data outside the SDK protocol.
 *
 * <p>Python: {@code class SdkProtocolError(HarnessError)}.
 */
public class SdkProtocolException extends HarnessException {

    public SdkProtocolException(String message) {
        super(message);
    }

    public SdkProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
