package com.mavela.backend.admin.staff;

import com.mavela.backend.error.ApiErrorCode;

public class StaffUserAccessException extends RuntimeException {

    private final ApiErrorCode code;

    public StaffUserAccessException(ApiErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public ApiErrorCode getCode() {
        return code;
    }
}
