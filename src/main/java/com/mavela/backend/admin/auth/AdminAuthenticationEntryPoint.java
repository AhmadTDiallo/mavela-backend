package com.mavela.backend.admin.auth;

import com.mavela.backend.error.ApiErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AdminAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AdminProblemDetailWriter problemDetailWriter;

    public AdminAuthenticationEntryPoint(
            AdminProblemDetailWriter problemDetailWriter
    ) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        problemDetailWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED.value(),
                "Administrator authentication required",
                ApiErrorCode.ADMIN_AUTHENTICATION_REQUIRED
        );
    }
}
