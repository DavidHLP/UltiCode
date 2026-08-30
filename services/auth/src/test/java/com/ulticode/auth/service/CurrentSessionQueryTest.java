package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountQueryPort;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.permission.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrentSessionQueryTest {

    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 8, 6, 12, 0);

    private AuthAccountQueryPort accountQueryPort;
    private PermissionService permissionService;
    private DefaultCurrentSessionQuery query;

    @BeforeEach
    void setUp() {
        accountQueryPort = mock(AuthAccountQueryPort.class);
        permissionService = mock(PermissionService.class);
        query = new DefaultCurrentSessionQuery(accountQueryPort, permissionService);
    }

    @Test
    void currentUserReturnsSafeProjection() {
        when(accountQueryPort.findById("user-1")).thenReturn(Optional.of(account()));

        CurrentSessionQuery.CurrentUser currentUser = query.currentUser("user-1");

        assertThat(currentUser.accountId()).isEqualTo("user-1");
        assertThat(currentUser.username()).isEqualTo("alice");
        assertThat(currentUser.email()).isEqualTo("alice@example.com");
        assertThat(currentUser.role()).isEqualTo("USER");
        assertThat(currentUser.active()).isTrue();
        assertThat(currentUser.banned()).isFalse();
        assertThat(currentUser.joinedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    void currentUserFailsWithAuthNotFound() {
        when(accountQueryPort.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.currentUser("missing"))
                .isInstanceOf(AuthBusinessException.class)
                .satisfies(exception -> assertThat(((AuthBusinessException) exception).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_USER_NOT_FOUND));
    }

    @Test
    void permissionsDelegatesToPermissionService() {
        List<String> permissions = List.of("READ:PROBLEM", "SUBMIT:PROBLEM");
        when(permissionService.getUserPermissionStrings("user-1")).thenReturn(permissions);

        assertThat(query.permissions("user-1")).containsExactlyElementsOf(permissions);
        verify(permissionService).getUserPermissionStrings("user-1");
    }

    private AuthAccountDTO account() {
        return new AuthAccountDTO(
                "user-1",
                "alice",
                "alice@example.com",
                "USER",
                true,
                false,
                null,
                null,
                JOINED_AT,
                null,
                7L
        );
    }
}
