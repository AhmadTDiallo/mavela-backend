package com.mavela.backend.services.catalogue;

import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public class ServicesCatalogueException extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;

    public ServicesCatalogueException(ApiErrorCode code, HttpStatus status) {
        super(code.defaultMessage());
        this.code = code;
        this.status = status;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
