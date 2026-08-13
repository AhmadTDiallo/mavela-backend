package com.mavela.backend.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.mavela.backend.error.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

@Component
public class AdminProblemDetailWriter {

    /*
     * Security failures occur before MVC controller advice. Do not depend on
     * MVC's optional ObjectMapper auto-configuration merely to return a small
     * RFC 9457 response from the fail-closed admin filter chain.
     */
    private final ObjectWriter problemDetailWriter = new ObjectMapper()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .writer();

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String title,
            ApiErrorCode code
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(status),
                code.defaultMessage()
        );
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.name());

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        problemDetailWriter.writeValue(response.getOutputStream(), problem);
    }
}
