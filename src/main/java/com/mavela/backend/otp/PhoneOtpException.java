package com.mavela.backend.otp;

import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class PhoneOtpException extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;
    private final Map<String, Object> properties;

    public PhoneOtpException(
            ApiErrorCode code,
            HttpStatus status
    ) {
        this(code, status, Map.of());
    }

    public PhoneOtpException(
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