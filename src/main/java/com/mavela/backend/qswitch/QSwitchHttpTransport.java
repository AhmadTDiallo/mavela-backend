package com.mavela.backend.qswitch;

import java.io.IOException;

interface QSwitchHttpTransport {

    QSwitchHttpResponse execute(QSwitchHttpRequest request)
            throws IOException, InterruptedException;
}
