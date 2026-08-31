package com.mavela.backend.admin.services;

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

@RestControllerAdvice(assignableTypes = ServicesCatalogueAdminController.class)
class ServicesCatalogueAdminExceptionHandler {

    @ExceptionHandler(ServicesCatalogueAdminException.class)
    ResponseEntity<ProblemDetail> handleServicesException(
            ServicesCatalogueAdminException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(exception.getStatus()).body(problem(
                exception.getStatus(), exception.getCode(), request
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.VALIDATION_FAILED,
                request
        );
        List<Map<String, String>> errors = exception.getBindingResult()
                .getFieldErrors().stream().map(this::fieldError).toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.unprocessableContent().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.unprocessableContent().body(problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.MALFORMED_JSON,
                request
        ));
    }

    private ProblemDetail problem(
            HttpStatus status,
            ApiErrorCode code,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, code.defaultMessage()
        );
        problem.setTitle("Services catalogue administrator request failed");
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
