package com.mavela.backend.qswitch;

import java.util.List;
import java.util.Map;

/** Raw response remains inside the QSwitch adapter boundary. */
public record QSwitchHttpResponse(
        int statusCode,
        Map<String, List<String>> headers,
        String body
) {

    public QSwitchHttpResponse {
        headers = Map.copyOf(headers);
    }

    public String firstHeader(String name) {
        return headers.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return "QSwitchHttpResponse[statusCode=" + statusCode + "]";
    }
}
