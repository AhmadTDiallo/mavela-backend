package com.mavela.backend.otp;

public interface PhoneOtpSender {

    void send(
            String phoneNumber,
            String code,
            String preferredLocale
    );
}