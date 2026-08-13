package com.mavela.backend.admin.staff;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class StaffUserExceptionHandler {

    @ExceptionHandler(StaffUserAccessException.class)
    public ResponseEntity<ProblemDetail> handleStaffUserAccess(
            StaffUserAccessException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage()
        );
        problem.setTitle("Administrator access denied");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", exception.getCode().name());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }
}
