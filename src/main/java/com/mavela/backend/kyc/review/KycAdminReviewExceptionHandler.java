package com.mavela.backend.kyc.review;

import com.mavela.backend.error.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice(assignableTypes = KycAdminReviewController.class)
public class KycAdminReviewExceptionHandler {

    @ExceptionHandler(KycAdminReviewException.class)
    public ResponseEntity<ProblemDetail> handleReviewException(
            KycAdminReviewException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = problem(
                exception.getStatus(),
                "KYC administrator review request failed",
                exception.getCode(),
                request
        );
        return ResponseEntity.status(exception.getStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "KYC administrator review request is invalid",
                ApiErrorCode.VALIDATION_FAILED,
                request
        );
        List<Map<String, String>> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::fieldError)
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.unprocessableContent().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.unprocessableContent().body(problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "KYC administrator review request is invalid",
                ApiErrorCode.MALFORMED_JSON,
                request
        ));
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            ApiErrorCode code,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                code.defaultMessage()
        );
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.name());
        return problem;
    }

    private Map<String, String> fieldError(FieldError error) {
        ApiErrorCode code = ApiErrorCode.fromValidationMessage(
                error.getDefaultMessage()
        );
        return Map.of(
                "field", error.getField(),
                "code", code.name(),
                "message", code.defaultMessage()
        );
    }
}
