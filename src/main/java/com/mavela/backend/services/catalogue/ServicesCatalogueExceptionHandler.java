package com.mavela.backend.services.catalogue;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(assignableTypes = ServicesCatalogueController.class)
public class ServicesCatalogueExceptionHandler {

    @ExceptionHandler(ServicesCatalogueException.class)
    public ResponseEntity<ProblemDetail> handleServicesCatalogueException(
            ServicesCatalogueException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.getStatus(),
                exception.getCode().defaultMessage()
        );
        problem.setTitle("Services catalogue request failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", exception.getCode().name());

        return ResponseEntity.status(exception.getStatus()).body(problem);
    }
}
