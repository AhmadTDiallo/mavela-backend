package com.mavela.backend.kyc;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(assignableTypes = KycApplicationController.class)
public class KycWorkflowExceptionHandler {

    @ExceptionHandler(KycWorkflowException.class)
    public ResponseEntity<ProblemDetail> handleKycWorkflowException(
            KycWorkflowException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.getStatus(),
                exception.getMessage()
        );

        problem.setTitle("KYC application request failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", exception.getCode().name());

        return ResponseEntity.status(exception.getStatus()).body(problem);
    }
}
