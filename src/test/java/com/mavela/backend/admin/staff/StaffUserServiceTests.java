package com.mavela.backend.admin.staff;

import com.mavela.backend.error.ApiErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StaffUserServiceTests {

    private final StaffUserRepository repository = mock(StaffUserRepository.class);
    private final StaffUserService service = new StaffUserService(repository);

    @BeforeEach
    void authenticateDefaultSubject() {
        authenticate("cognito-subject");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsActivePreProvisionedStaffUser() {
        StaffUser staffUser = staffUser(StaffUserStatus.ACTIVE);
        when(repository.findByExternalSubject("cognito-subject"))
                .thenReturn(Optional.of(staffUser));

        assertThat(service.requireActiveStaff("cognito-subject"))
                .isSameAs(staffUser);
    }

    @Test
    void rejectsUnprovisionedStaffSubject() {
        authenticate("unknown-subject");
        when(repository.findByExternalSubject("unknown-subject"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveStaff("unknown-subject"))
                .isInstanceOf(StaffUserAccessException.class)
                .extracting(exception -> ((StaffUserAccessException) exception)
                        .getCode())
                .isEqualTo(ApiErrorCode.ADMIN_STAFF_NOT_PROVISIONED);
    }

    @Test
    void rejectsDisabledStaffUser() {
        when(repository.findByExternalSubject("cognito-subject"))
                .thenReturn(Optional.of(staffUser(StaffUserStatus.DISABLED)));

        assertThatThrownBy(() -> service.requireActiveStaff("cognito-subject"))
                .isInstanceOf(StaffUserAccessException.class)
                .extracting(exception -> ((StaffUserAccessException) exception)
                        .getCode())
                .isEqualTo(ApiErrorCode.ADMIN_STAFF_ACCOUNT_INACTIVE);
    }

    @Test
    void rejectsACallerSuppliedSubjectThatDoesNotMatchTheAuthenticatedActor() {
        authenticate("authenticated-reviewer");

        assertThatThrownBy(() -> service.requireActiveStaff("other-reviewer"))
                .isInstanceOf(StaffUserAccessException.class)
                .extracting(exception -> ((StaffUserAccessException) exception)
                        .getCode())
                .isEqualTo(ApiErrorCode.ADMIN_PERMISSION_DENIED);
        verifyNoInteractions(repository);
    }

    private void authenticate(String subject) {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(subject, "not-a-token");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private StaffUser staffUser(StaffUserStatus status) {
        return new StaffUser(
                "cognito-subject",
                "reviewer@example.test",
                "KYC Reviewer",
                status
        );
    }
}
