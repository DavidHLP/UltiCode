package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AuthAccountQueryPort;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountQueryProviderTest {

    private AuthAccountQueryPort queryPort;
    private AccountQueryProvider provider;

    private AuthAccountDTO sampleDto;

    @BeforeEach
    void setUp() {
        queryPort = mock(AuthAccountQueryPort.class);
        provider = new AccountQueryProvider(queryPort);

        sampleDto = new AuthAccountDTO(
                "user-100", "alice", "alice@example.com", "USER",
                true, false, null, null,
                LocalDateTime.now(), LocalDateTime.now(), 1L);
    }

    @Test
    @DisplayName("getAccountById returns success result when account exists")
    void getAccountByIdSuccess() {
        when(queryPort.findById("user-100")).thenReturn(Optional.of(sampleDto));

        RpcResult<AuthAccountDTO> result = provider.getAccountById("user-100");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().accountId()).isEqualTo("user-100");
        assertThat(result.data().username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("getAccountById returns ACCOUNT_NOT_FOUND error when account does not exist")
    void getAccountByIdNotFound() {
        when(queryPort.findById("user-999")).thenReturn(Optional.empty());

        RpcResult<AuthAccountDTO> result = provider.getAccountById("user-999");

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_FOUND.code());
    }

    @Test
    @DisplayName("getAccountByUsername returns success when username matches")
    void getAccountByUsernameSuccess() {
        when(queryPort.findByUsername("alice")).thenReturn(Optional.of(sampleDto));

        RpcResult<AuthAccountDTO> result = provider.getAccountByUsername("alice");

        assertThat(result.success()).isTrue();
        assertThat(result.data().username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("getAccountByEmail returns success when email matches")
    void getAccountByEmailSuccess() {
        when(queryPort.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleDto));

        RpcResult<AuthAccountDTO> result = provider.getAccountByEmail("alice@example.com");

        assertThat(result.success()).isTrue();
        assertThat(result.data().email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("queryAccounts returns empty page when total count is 0")
    void queryAccountsEmptyPage() {
        AccountQueryDTO query = new AccountQueryDTO("nonexistent", null, null, null, 1, 10, "joinedAt", "desc");
        when(queryPort.countAccounts(any())).thenReturn(0L);

        RpcResult<AuthAccountDTO> result = provider.queryAccounts(query);

        assertThat(result.success()).isTrue();
        assertThat(result.page()).isNotNull();
        assertThat(result.page().total()).isEqualTo(0L);
        assertThat(result.page().items()).isEmpty();
    }

    @Test
    @DisplayName("queryAccounts returns paginated items when matches exist")
    void queryAccountsSuccess() {
        AccountQueryDTO query = new AccountQueryDTO(null, "USER", true, false, 1, 10, "joinedAt", "desc");
        when(queryPort.countAccounts(any())).thenReturn(1L);
        when(queryPort.queryAccounts(any(), anyInt(), anyInt())).thenReturn(List.of(sampleDto));

        RpcResult<AuthAccountDTO> result = provider.queryAccounts(query);

        assertThat(result.success()).isTrue();
        assertThat(result.page()).isNotNull();
        assertThat(result.page().total()).isEqualTo(1L);
        assertThat(result.page().items()).hasSize(1);
    }

    @Test
    void countAccountsByIdsExcludingUsernameMatchDelegatesToOwnerPredicate() {
        Set<String> accountIds = Set.of("user-100", "user-200");
        when(queryPort.countByIdsExcludingUsernameMatch(accountIds, "e")).thenReturn(1L);

        RpcResult<Long> result = provider.countAccountsByIdsExcludingUsernameMatch(accountIds, "e");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(1L);
    }

    @Test
    void countAccountsByIdsExcludingUsernameMatchRejectsBlankQuery() {
        RpcResult<Long> result = provider.countAccountsByIdsExcludingUsernameMatch(Set.of("user-100"), " ");

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.INVALID_ACCOUNT_REQUEST.code());
    }

    @Test
    void countAccountsByIdsExcludingUsernameMatchRejectsOversizedBatch() {
        Set<String> accountIds = IntStream.rangeClosed(1, 101)
                .mapToObj(index -> "user-" + index)
                .collect(Collectors.toSet());

        RpcResult<Long> result = provider.countAccountsByIdsExcludingUsernameMatch(accountIds, "e");

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.INVALID_ACCOUNT_REQUEST.code());
    }

    @Test
    void countAccountsByIdsExcludingUsernameMatchRejectsBlankAccountId() {
        RpcResult<Long> result = provider.countAccountsByIdsExcludingUsernameMatch(
                Set.of("user-100", " "), "e");

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.INVALID_ACCOUNT_REQUEST.code());
    }
}
