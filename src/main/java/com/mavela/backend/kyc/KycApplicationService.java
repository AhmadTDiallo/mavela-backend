package com.mavela.backend.kyc;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.error.ApiErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class KycApplicationService {

    private final CustomerRepository customerRepository;
    private final KycApplicationRepository applicationRepository;

    public KycApplicationService(
            CustomerRepository customerRepository,
            KycApplicationRepository applicationRepository
    ) {
        this.customerRepository = customerRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public KycApplicationResponse startApplication(
            UUID authenticatedCustomerId
    ) {
        Customer customer = customerRepository
                .findByIdForUpdate(authenticatedCustomerId)
                .orElseThrow(this::authenticatedCustomerNotFound);

        if (!customer.isProfileComplete()) {
            throw workflowException(ApiErrorCode.KYC_PROFILE_INCOMPLETE);
        }

        if (applicationRepository.existsByCustomer_Id(
                authenticatedCustomerId
        )) {
            throw workflowException(
                    ApiErrorCode.KYC_APPLICATION_ALREADY_EXISTS
            );
        }

        if (customer.getKycStatus() != KycStatus.NOT_STARTED) {
            throw workflowException(ApiErrorCode.KYC_START_NOT_ALLOWED);
        }

        KycApplication application = new KycApplication(customer, 1);
        customer.startKycApplication();

        try {
            KycApplication savedApplication = applicationRepository
                    .saveAndFlush(application);
            return KycApplicationResponse.from(savedApplication);
        } catch (DataIntegrityViolationException exception) {
            /*
             * The unique database constraints guard the application
             * creation path if a concurrent request wins the race.
             */
            throw workflowException(
                    ApiErrorCode.KYC_APPLICATION_ALREADY_EXISTS
            );
        }
    }

    @Transactional(readOnly = true)
    public KycApplicationResponse getCurrentApplication(
            UUID authenticatedCustomerId
    ) {
        customerRepository.findById(authenticatedCustomerId)
                .orElseThrow(this::authenticatedCustomerNotFound);

        KycApplication application = applicationRepository
                .findFirstByCustomer_IdOrderByAttemptNumberDesc(
                        authenticatedCustomerId
                )
                .orElseThrow(() -> workflowException(
                        ApiErrorCode.KYC_APPLICATION_NOT_FOUND
                ));

        return KycApplicationResponse.from(application);
    }

    private ResponseStatusException authenticatedCustomerNotFound() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATED_CUSTOMER_NOT_FOUND"
        );
    }

    private KycWorkflowException workflowException(ApiErrorCode code) {
        return switch (code) {
            case KYC_APPLICATION_NOT_FOUND -> new KycWorkflowException(
                    code,
                    HttpStatus.NOT_FOUND
            );
            case KYC_PROFILE_INCOMPLETE,
                 KYC_APPLICATION_ALREADY_EXISTS,
                 KYC_START_NOT_ALLOWED -> new KycWorkflowException(
                    code,
                    HttpStatus.CONFLICT
            );
            default -> throw new IllegalArgumentException(
                    "Unsupported KYC workflow error code."
            );
        };
    }
}
