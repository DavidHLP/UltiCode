package com.ulticode.app.user.port;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerUserReadAdapterTest {

    @Mock private UserProfileReadMapper profileReadMapper;
    @Mock private AccountQueryService accountQueryService;

    private OwnerUserReadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OwnerUserReadAdapter(profileReadMapper);
        adapter.setAccountQueryService(accountQueryService);
    }

    @Test
    void selectByIdComposesAuthAccountAndAppProfile() {
        AuthAccountDTO account = account("u-1", "alice");
        UserProfileDTO profile = new UserProfileDTO(
                "u-1", "Alice", "/avatar.png", "bio", "company",
                "alice", "earth", "alice", "https://example.test", "zh-CN");
        when(accountQueryService.getAccountById("u-1")).thenReturn(RpcResult.success(account, "t-1"));
        when(profileReadMapper.findByAccountId("u-1")).thenReturn(profile);

        UserSummaryView result = adapter.selectById("u-1");

        assertThat(result.id()).isEqualTo("u-1");
        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.role()).isEqualTo("USER");
    }

    @Test
    void selectByIdsUsesOneAuthBatchAndOneProfileBatch() {
        AuthAccountDTO first = account("u-1", "alice");
        AuthAccountDTO second = account("u-2", "bob");
        Set<String> ids = Set.of("u-1", "u-2");
        when(accountQueryService.getAccountsByIds(ids))
                .thenReturn(RpcResult.success(List.of(first, second), "t-1"));
        when(profileReadMapper.findByAccountIds(ids)).thenReturn(
                List.of(UserProfileDTO.empty("u-1"), UserProfileDTO.empty("u-2")));

        assertThat(adapter.selectByIds(List.of("u-1", "u-2")))
                .containsKeys("u-1", "u-2");
        org.mockito.Mockito.verify(accountQueryService).getAccountsByIds(ids);
        org.mockito.Mockito.verify(profileReadMapper).findByAccountIds(ids);
    }

    @Test
    void factsComposeLoadedAccountsWithOneSearchProfileBatch() {
        AuthAccountDTO account = account("u-1", "alice");
        Set<String> ids = Set.of("u-1");
        UserProfileReadRow profile = new UserProfileReadRow();
        profile.setAccountId("u-1");
        profile.setName("Alice");
        profile.setAvatar("/alice.png");
        profile.setUpdatedAt(LocalDateTime.parse("2026-08-20T00:00:00"));
        when(accountQueryService.getAccountsByIds(ids))
                .thenReturn(RpcResult.success(List.of(account), "t-facts"));
        when(profileReadMapper.findSearchRowsByAccountIds(ids)).thenReturn(List.of(profile));

        UserFactView fact = adapter.findByIds(ids).get("u-1");

        assertThat(fact.username()).isEqualTo("alice");
        assertThat(fact.name()).isEqualTo("Alice");
        assertThat(fact.profileUpdatedAt()).isEqualTo(profile.getUpdatedAt());
    }

    @Test
    void selectActiveUsersUsesAuthPageAndBatchProfiles() {
        AuthAccountDTO account = account("u-1", "alice");
        when(accountQueryService.queryAccounts(any())).thenReturn(
                RpcResult.page(List.of(account), 1, 1, 10, "t-1"));
        when(profileReadMapper.findByAccountIds(Set.of("u-1"))).thenReturn(
                List.of(UserProfileDTO.empty("u-1")));

        assertThat(adapter.selectActiveUsers(10, 0))
                .extracting(UserSummaryView::username)
                .containsExactly("alice");
    }

    @Test
    void selectActiveUsersPreservesNonPageAlignedOffset() {
        List<AuthAccountDTO> accounts = IntStream.range(0, 13)
                .mapToObj(i -> account("u-" + i, "user-" + i)).toList();
        when(accountQueryService.queryAccounts(any())).thenReturn(RpcResult.page(accounts, 20, 1, 13, "t-1"));
        when(profileReadMapper.findByAccountIds(any())).thenReturn(
                accounts.stream().map(a -> UserProfileDTO.empty(a.accountId())).toList());
        assertThat(adapter.selectActiveUsers(10, 3)).extracting(UserSummaryView::username)
                .containsExactly("user-3", "user-4", "user-5", "user-6", "user-7", "user-8", "user-9", "user-10", "user-11", "user-12");
    }

    @Test
    void missingAccountReturnsNullButUnavailableOwnerFailsClosed() {
        when(accountQueryService.getAccountById("missing")).thenReturn(
                RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-1"));
        assertThat(adapter.selectById("missing")).isNull();

        adapter.setAccountQueryService(null);
        assertThatThrownBy(() -> adapter.selectById("u-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account query unavailable");
    }

    private AuthAccountDTO account(String id, String username) {
        return new AuthAccountDTO(
                id, username, username + "@example.test", "USER", true, false,
                null, null, LocalDateTime.parse("2026-08-19T00:00:00"), null, 3L);
    }
}
