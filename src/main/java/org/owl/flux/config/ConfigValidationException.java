package org.owl.flux.config;

public final class ConfigValidationException extends RuntimeException {
    public ConfigValidationException(String message) {
        super(message);
    }

    public ConfigValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
