package com.mavela.backend.auth;

import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class AuthException extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;
    private final Map<String, Object> properties;

    public AuthException(
            ApiErrorCode code,
            HttpStatus status
    ) {
        this(code, status, Map.of());
    }

    public AuthException(
            ApiErrorCode code,
            HttpStatus status,
            Map<String, Object> properties
    ) {
        super(code.defaultMessage());
        this.code = code;
        this.status = status;
        this.properties = Map.copyOf(properties);
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}