package com.mavela.backend.admin.services;

import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

class ServicesCatalogueAdminException extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;

    ServicesCatalogueAdminException(ApiErrorCode code, HttpStatus status) {
        super(code.defaultMessage());
        this.code = code;
        this.status = status;
    }

    ApiErrorCode getCode() { return code; }
    HttpStatus getStatus() { return status; }
}
