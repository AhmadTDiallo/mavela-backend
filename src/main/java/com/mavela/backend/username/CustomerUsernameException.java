package com.mavela.backend.username;

import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public class CustomerUsernameException
        extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;

    public CustomerUsernameException(
            ApiErrorCode code,
            HttpStatus status
    ) {
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