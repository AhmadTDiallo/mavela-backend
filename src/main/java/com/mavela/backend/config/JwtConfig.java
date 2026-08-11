package com.mavela.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    public static final String ISSUER = "mavela-backend";
    public static final String AUDIENCE = "mavela-mobile";

    @Bean
    SecretKey mavelaJwtSigningKey(
            @Value("${MAVELA_JWT_SECRET:}")
            String configuredSecret
    ) {
        if (configuredSecret == null
                || configuredSecret.isBlank()) {
            throw new IllegalStateException(
                    "MAVELA_JWT_SECRET is required."
            );
        }

        final byte[] decodedSecret;

        try {
            decodedSecret = Base64
                    .getDecoder()
                    .decode(configuredSecret.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "MAVELA_JWT_SECRET must be valid Base64.",
                    exception
            );
        }

        if (decodedSecret.length < 32) {
            throw new IllegalStateException(
                    "MAVELA_JWT_SECRET must decode to at least 32 bytes."
            );
        }

        return new SecretKeySpec(
                decodedSecret,
                "HmacSHA256"
        );
    }

    @Bean
    JwtEncoder jwtEncoder(
            SecretKey mavelaJwtSigningKey
    ) {
        return NimbusJwtEncoder
                .withSecretKey(mavelaJwtSigningKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey mavelaJwtSigningKey
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(mavelaJwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> validator =
                JwtValidators.createDefaultWithValidators(
                        new JwtIssuerValidator(ISSUER),
                        new JwtAudienceValidator(AUDIENCE)
                );

        decoder.setJwtValidator(validator);

        return decoder;
    }
}