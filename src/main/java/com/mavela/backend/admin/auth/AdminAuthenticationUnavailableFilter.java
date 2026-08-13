package com.mavela.backend.admin.auth;

import com.mavela.backend.error.ApiErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Makes a missing Cognito configuration visibly unavailable instead of ever
 * falling through to the customer resource-server chain.
 */
public final class AdminAuthenticationUnavailableFilter
        extends OncePerRequestFilter {

    private final AdminProblemDetailWriter problemDetailWriter;

    public AdminAuthenticationUnavailableFilter(
            AdminProblemDetailWriter problemDetailWriter
    ) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        problemDetailWriter.write(
                request,
                response,
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Administrator authentication unavailable",
                ApiErrorCode.ADMIN_AUTHENTICATION_UNAVAILABLE
        );
    }
}
