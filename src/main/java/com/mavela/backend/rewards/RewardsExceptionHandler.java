package com.mavela.backend.rewards;

import com.mavela.backend.error.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(assignableTypes = RewardsController.class)
public class RewardsExceptionHandler {

    @ExceptionHandler(RewardsException.class)
    public ResponseEntity<ProblemDetail> handleRewardsException(
            RewardsException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.getStatus(),
                exception.getCode().defaultMessage()
        );
        problem.setTitle("Rewards request failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", exception.getCode().name());

        return ResponseEntity.status(exception.getStatus()).body(problem);
    }

    @ExceptionHandler(org.springframework.web.method.annotation
            .HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleRequestValidation(
            org.springframework.web.method.annotation
                    .HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REWARDS_INVALID_PAGINATION.defaultMessage()
        );
        problem.setTitle("Rewards request failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ApiErrorCode.REWARDS_INVALID_PAGINATION.name());
        return ResponseEntity.badRequest().body(problem);
    }
}
