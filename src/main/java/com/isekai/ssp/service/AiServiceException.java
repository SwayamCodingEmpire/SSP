package com.isekai.ssp.service;

import lombok.Getter;

/**
 * Exception thrown when an AI service call fails.
 */
@Getter
public class AiServiceException extends RuntimeException {

    private final String provider;
    private final String operation;

    public AiServiceException(String provider, String operation, String message, Throwable cause) {
        super("AI service error [%s/%s]: %s".formatted(provider, operation, message), cause);
        this.provider = provider;
        this.operation = operation;
    }

    public AiServiceException(String provider, String operation, String message) {
        super("AI service error [%s/%s]: %s".formatted(provider, operation, message));
        this.provider = provider;
        this.operation = operation;
    }
}
