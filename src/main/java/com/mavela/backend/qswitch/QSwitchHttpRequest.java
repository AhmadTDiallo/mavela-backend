package com.mavela.backend.qswitch;

import java.net.URI;
import java.util.Map;

/**
 * Internal transport request. Its string form is deliberately redacted so a
 * future accidental log statement cannot expose credentials or bearer tokens.
 */
public record QSwitchHttpRequest(
        String method,
        URI uri,
        Map<String, String> headers,
        String body
) {

    public QSwitchHttpRequest {
        headers = Map.copyOf(headers);
    }

    @Override
    public String toString() {
        return "QSwitchHttpRequest[redacted]";
    }
}
