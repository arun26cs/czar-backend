package com.czar.common.exception;

/**
 * Thrown when a create/update would violate a uniqueness constraint
 * (e.g. duplicate tag name per user, duplicate email).
 * Maps to HTTP 409 via {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public static ConflictException of(String resourceType, String field, String value) {
        return new ConflictException(resourceType + " with " + field + " '" + value + "' already exists");
    }
}
