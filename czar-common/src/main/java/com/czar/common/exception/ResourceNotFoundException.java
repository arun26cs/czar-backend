package com.czar.common.exception;

/**
 * Thrown when a requested resource (plan, note, folder, user) is not found
 * or does not belong to the requesting user.
 * Maps to HTTP 404 via {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resourceType, String id) {
        return new ResourceNotFoundException(resourceType + " not found: " + id);
    }
}
