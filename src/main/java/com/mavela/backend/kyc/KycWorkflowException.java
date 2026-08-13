package com.mavela.backend.kyc;

import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public class KycWorkflowException extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;
    private final String step;

    public KycWorkflowException(ApiErrorCode code, HttpStatus status) {
        this(code, status, null);
    }

    public KycWorkflowException(
            ApiErrorCode code,
            HttpStatus status,
            String step
    ) {
        super(code.defaultMessage());
        this.code = code;
        this.status = status;
        this.step = step;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getStep() {
        return step;
    }
}
