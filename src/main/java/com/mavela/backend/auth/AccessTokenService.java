package com.mavela.backend.auth;

import com.mavela.backend.customer.Customer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.mavela.backend.config.JwtConfig.AUDIENCE;
import static com.mavela.backend.config.JwtConfig.ISSUER;

@Component
public class AccessTokenService {

    private static final Duration ACCESS_TOKEN_TTL =
            Duration.ofMinutes(10);

    private final JwtEncoder jwtEncoder;

    public AccessTokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public IssuedAccessToken issue(
            Customer customer,
            Instant now
    ) {
        Instant expiresAt = now.plus(ACCESS_TOKEN_TTL);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(customer.getId().toString())
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        String token = jwtEncoder
                .encode(
                        JwtEncoderParameters.from(
                                header,
                                claims
                        )
                )
                .getTokenValue();

        return new IssuedAccessToken(
                token,
                expiresAt,
                ACCESS_TOKEN_TTL.toSeconds()
        );
    }

    public record IssuedAccessToken(
            String value,
            Instant expiresAt,
            long expiresInSeconds
    ) {
    }
}