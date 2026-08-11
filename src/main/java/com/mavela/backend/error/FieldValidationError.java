package com.mavela.backend.error;

public record FieldValidationError(
        String field,
        String code,
        String message
) {
}