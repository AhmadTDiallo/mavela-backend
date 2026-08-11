package com.mavela.backend.customer;

import com.mavela.backend.error.ApiErrorCode;

public class DuplicateCustomerException extends RuntimeException {

    private final ApiErrorCode code;

    public DuplicateCustomerException(ApiErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public ApiErrorCode getCode() {
        return code;
    }
}