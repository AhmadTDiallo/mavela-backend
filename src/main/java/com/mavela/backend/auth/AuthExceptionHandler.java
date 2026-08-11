package com.mavela.backend.auth;

import com.mavela.backend.error.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
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

@RestControllerAdvice(
        assignableTypes = AuthController.class
)
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ProblemDetail> handleAuthException(
            AuthException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.getStatus(),
                exception.getMessage()
        );

        problem.setTitle("Authentication failed");
        problem.setInstance(
                URI.create(request.getRequestURI())
        );
        problem.setProperty(
                "code",
                exception.getCode().name()
        );

        exception.getProperties().forEach(
                problem::setProperty
        );

        HttpHeaders headers = new HttpHeaders();

        Object retryAfterSeconds = exception
                .getProperties()
                .get("retryAfterSeconds");

        if (retryAfterSeconds != null) {
            headers.set(
                    HttpHeaders.RETRY_AFTER,
                    retryAfterSeconds.toString()
            );
        }

        return new ResponseEntity<>(
                problem,
                headers,
                exception.getStatus()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED.defaultMessage()
        );

        problem.setTitle("Login request is invalid");
        problem.setInstance(
                URI.create(request.getRequestURI())
        );
        problem.setProperty(
                "code",
                ApiErrorCode.VALIDATION_FAILED.name()
        );

        List<Map<String, String>> errors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::createFieldError)
                .toList();

        problem.setProperty("errors", errors);

        return ResponseEntity
                .badRequest()
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.MALFORMED_JSON.defaultMessage()
        );

        problem.setTitle("Login request is invalid");
        problem.setInstance(
                URI.create(request.getRequestURI())
        );
        problem.setProperty(
                "code",
                ApiErrorCode.MALFORMED_JSON.name()
        );

        return ResponseEntity
                .badRequest()
                .body(problem);
    }

    private Map<String, String> createFieldError(
            FieldError fieldError
    ) {
        ApiErrorCode code =
                ApiErrorCode.fromValidationMessage(
                        fieldError.getDefaultMessage()
                );

        return Map.of(
                "field",
                fieldError.getField(),
                "code",
                code.name(),
                "message",
                code.defaultMessage()
        );
    }
}