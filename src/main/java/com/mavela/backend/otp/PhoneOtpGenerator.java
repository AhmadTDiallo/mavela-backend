package com.mavela.backend.otp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PhoneOtpGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        int value = secureRandom.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}