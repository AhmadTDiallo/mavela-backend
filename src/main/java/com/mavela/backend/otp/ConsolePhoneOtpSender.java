package com.mavela.backend.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConsolePhoneOtpSender implements PhoneOtpSender {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ConsolePhoneOtpSender.class);

    @Override
    public void send(
            String phoneNumber,
            String code,
            String preferredLocale
    ) {
        LOGGER.warn(
                "[MAVELA DEVELOPMENT OTP] phone={} code={} locale={}",
                phoneNumber,
                code,
                preferredLocale
        );
    }
}