package com.mavela.backend.error;

public enum ApiErrorCode {

    CUSTOMER_PHONE_ALREADY_EXISTS(
            "An account with this phone number already exists."
    ),

    CUSTOMER_EMAIL_ALREADY_EXISTS(
            "An account with this email address already exists."
    ),

    CUSTOMER_ALREADY_EXISTS(
            "An account with these details already exists."
    ),

    CUSTOMER_NOT_FOUND(
            "The customer could not be found."
    ),

    KYC_PROFILE_INCOMPLETE(
            "Complete your customer profile before starting KYC."
    ),

    KYC_APPLICATION_ALREADY_EXISTS(
            "A KYC application already exists for this customer."
    ),

    KYC_START_NOT_ALLOWED(
            "KYC cannot be started in the customer's current state."
    ),

    KYC_APPLICATION_NOT_FOUND(
            "No KYC application was found for this customer."
    ),

    PHONE_ALREADY_VERIFIED(
            "This phone number has already been verified."
    ),

    VALIDATION_FAILED(
            "One or more fields are invalid."
    ),

    MALFORMED_JSON(
            "The request body is missing or contains invalid JSON."
    ),

    PHONE_NUMBER_REQUIRED(
            "Phone number is required."
    ),

    PHONE_NUMBER_INVALID_FORMAT(
            "Phone number must use international E.164 format."
    ),

    EMAIL_INVALID_FORMAT(
            "Email address is invalid."
    ),

    EMAIL_TOO_LONG(
            "Email address is too long."
    ),

    FIRST_NAME_REQUIRED(
            "First name is required."
    ),

    FIRST_NAME_TOO_LONG(
            "First name is too long."
    ),

    LAST_NAME_REQUIRED(
            "Last name is required."
    ),

    LAST_NAME_TOO_LONG(
            "Last name is too long."
    ),

    PREFERRED_LOCALE_REQUIRED(
            "Preferred language is required."
    ),

    PREFERRED_LOCALE_UNSUPPORTED(
            "Preferred language must be en or fr-CD."
    ),

    USERNAME_REQUIRED(
            "Username is required."
    ),

    USERNAME_INVALID_FORMAT(
            "Username must contain 3 to 20 lowercase letters or numbers."
    ),

    USERNAME_ALREADY_TAKEN(
            "This username is already taken."
    ),

    USERNAME_SETUP_NOT_ALLOWED(
            "Username selection is not allowed for this customer."
    ),

    USERNAME_REQUIRED_BEFORE_PIN_SETUP(
            "Choose a username before creating a PIN."
    ),

    OTP_CHALLENGE_ID_REQUIRED(
            "OTP challenge ID is required."
    ),

    OTP_CODE_REQUIRED(
            "OTP code is required."
    ),

    OTP_CODE_INVALID_FORMAT(
            "OTP code must contain exactly six digits."
    ),

    OTP_RESEND_TOO_SOON(
            "Please wait before requesting another OTP."
    ),

    OTP_CHALLENGE_NOT_FOUND(
            "No active OTP challenge was found."
    ),

    OTP_CHALLENGE_NOT_ACTIVE(
            "This OTP challenge is no longer active."
    ),

    OTP_EXPIRED(
            "The OTP code has expired."
    ),

    OTP_INVALID_CODE(
            "The OTP code is incorrect."
    ),

    OTP_ATTEMPTS_EXCEEDED(
            "The maximum number of OTP attempts has been reached."
    ),

    FIELD_INVALID(
            "The field value is invalid."
    ),

    PHONE_NOT_VERIFIED(
            "The customer's phone number must be verified first."
    ),

    PIN_SETUP_NOT_ALLOWED(
            "PIN setup is not allowed for this customer."
    ),

    PIN_SETUP_TOKEN_REQUIRED(
            "PIN setup token is required."
    ),

    PIN_SETUP_TOKEN_INVALID_FORMAT(
            "PIN setup token has an invalid format."
    ),

    PIN_SETUP_TOKEN_INVALID(
            "The PIN setup token is invalid."
    ),

    PIN_SETUP_TOKEN_EXPIRED(
            "The PIN setup token has expired."
    ),

    PIN_REQUIRED(
            "PIN is required."
    ),

    PIN_INVALID_FORMAT(
            "PIN must contain exactly four digits."
    ),

    PIN_TOO_WEAK(
            "Choose a less predictable PIN."
    ),

    PIN_ALREADY_SET(
            "A PIN has already been created for this customer."
    ),

    INVALID_CREDENTIALS(
            "Phone number or PIN is incorrect."
    ),

    PIN_AUTHENTICATION_LOCKED(
            "PIN authentication is temporarily locked."
    ),

    CUSTOMER_AUTHENTICATION_NOT_ALLOWED(
            "This account cannot sign in."
    ),

    REFRESH_TOKEN_REQUIRED(
            "Refresh token is required."
    ),

    REFRESH_TOKEN_INVALID_FORMAT(
            "Refresh token format is invalid."
    ),

    INVALID_REFRESH_TOKEN(
            "Refresh token is invalid or expired."
    ),

    REFRESH_TOKEN_REUSE_DETECTED(
            "Refresh token reuse was detected. Sign in again."
    );

    private final String defaultMessage;

    ApiErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public static ApiErrorCode fromValidationMessage(
            String message
    ) {
        if (message == null) {
            return FIELD_INVALID;
        }

        try {
            return ApiErrorCode.valueOf(message);
        } catch (IllegalArgumentException exception) {
            return FIELD_INVALID;
        }
    }
}
