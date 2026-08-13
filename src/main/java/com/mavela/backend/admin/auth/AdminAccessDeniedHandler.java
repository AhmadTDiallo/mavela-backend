package com.mavela.backend.admin.auth;

import com.mavela.backend.error.ApiErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AdminAccessDeniedHandler implements AccessDeniedHandler {

    private final AdminProblemDetailWriter problemDetailWriter;

    public AdminAccessDeniedHandler(AdminProblemDetailWriter problemDetailWriter) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        problemDetailWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN.value(),
                "Administrator access denied",
                ApiErrorCode.ADMIN_PERMISSION_DENIED
        );
    }
}
