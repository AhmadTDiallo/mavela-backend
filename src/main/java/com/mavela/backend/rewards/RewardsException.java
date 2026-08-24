package com.mavela.backend.rewards;

import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public class RewardsException extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;

    public RewardsException(ApiErrorCode code, HttpStatus status) {
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
