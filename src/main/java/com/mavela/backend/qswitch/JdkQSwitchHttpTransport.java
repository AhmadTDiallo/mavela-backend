package com.mavela.backend.qswitch;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Java HTTP transport with no request/response logging. */
final class JdkQSwitchHttpTransport implements QSwitchHttpTransport {

    private final HttpClient httpClient;

    JdkQSwitchHttpTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public QSwitchHttpResponse execute(QSwitchHttpRequest request)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(Duration.ofSeconds(30));

        request.headers().forEach(builder::header);

        if ("GET".equals(request.method())) {
            builder.GET();
        } else {
            builder.method(
                    request.method(),
                    HttpRequest.BodyPublishers.ofString(
                            request.body() == null ? "" : request.body(),
                            StandardCharsets.UTF_8
                    )
            );
        }

        HttpResponse<String> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        return new QSwitchHttpResponse(
                response.statusCode(),
                response.headers().map(),
                response.body()
        );
    }
}
