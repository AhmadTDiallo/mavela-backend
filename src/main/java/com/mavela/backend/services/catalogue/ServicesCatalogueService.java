package com.mavela.backend.services.catalogue;

import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Authenticated, read-only customer access to non-live service metadata. */
@Service
public class ServicesCatalogueService {

    private final CustomerRepository customerRepository;
    private final ServiceCatalogueRepository serviceCatalogueRepository;

    public ServicesCatalogueService(
            CustomerRepository customerRepository,
            ServiceCatalogueRepository serviceCatalogueRepository
    ) {
        this.customerRepository = customerRepository;
        this.serviceCatalogueRepository = serviceCatalogueRepository;
    }

    @Transactional(readOnly = true)
    public List<ServiceCatalogueResponse> getCatalogue(
            UUID authenticatedCustomerId
    ) {
        requireAuthenticatedCustomer(authenticatedCustomerId);
        return serviceCatalogueRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .filter(this::isCustomerVisible)
                .map(ServiceCatalogueResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceCatalogueResponse getService(
            UUID authenticatedCustomerId,
            String serviceCode
    ) {
        requireAuthenticatedCustomer(authenticatedCustomerId);
        return serviceCatalogueRepository.findByServiceCode(serviceCode)
                .filter(this::isCustomerVisible)
                .map(ServiceCatalogueResponse::from)
                .orElseThrow(() -> new ServicesCatalogueException(
                        ApiErrorCode.SERVICES_CATALOGUE_ITEM_NOT_FOUND,
                        HttpStatus.NOT_FOUND
                ));
    }

    private void requireAuthenticatedCustomer(UUID authenticatedCustomerId) {
        if (!customerRepository.existsById(authenticatedCustomerId)) {
            throw new ServicesCatalogueException(
                    ApiErrorCode.SERVICES_CUSTOMER_NOT_FOUND,
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    private boolean isCustomerVisible(ServiceCatalogueEntry entry) {
        return entry.getAvailability() != ServiceAvailability.UNAVAILABLE;
    }
}
