package com.mavela.backend.username;

import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public class CustomerUsernameTokenExpiredException
        extends CustomerUsernameException {

    public CustomerUsernameTokenExpiredException() {
        super(
                ApiErrorCode.PIN_SETUP_TOKEN_EXPIRED,
                HttpStatus.GONE
        );
    }
}