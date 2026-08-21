package com.ulticode.app.user.port;

import com.ulticode.auth.api.dto.AuthAccountDTO;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultUserFactsReadProjectionTest {

    @Mock private UserProfileReadMapper profileReadMapper;
    @Mock private AccountQueryService accountQueryService;

    private DefaultUserFactsReadProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultUserFactsReadProjection(profileReadMapper);
        projection.setAccountQueryService(accountQueryService);
    }

    @Test
    void composeUsesOneAuthBatchAndOneProfileBatch() {
        Set<String> ids = Set.of("u-1");
        AuthAccountDTO account = account("u-1", "alice");
        UserProfileReadRow profile = new UserProfileReadRow();
        profile.setAccountId("u-1");
        profile.setName("Alice");
        profile.setAvatar("/alice.png");
        profile.setUpdatedAt(LocalDateTime.parse("2026-08-20T00:00:00"));
        when(accountQueryService.getAccountsByIds(ids))
                .thenReturn(RpcResult.success(List.of(account), "t-facts"));
        when(profileReadMapper.findSearchRowsByAccountIds(ids)).thenReturn(List.of(profile));

        UserFactView fact = projection.findByIds(List.of(" u-1 ")).get("u-1");

        assertThat(fact.username()).isEqualTo("alice");
        assertThat(fact.name()).isEqualTo("Alice");
        assertThat(fact.profileUpdatedAt()).isEqualTo(profile.getUpdatedAt());
        verify(accountQueryService, times(1)).getAccountsByIds(ids);
        verify(profileReadMapper, times(1)).findSearchRowsByAccountIds(ids);
    }

    @Test
    void composePreservesInputFactsOrderAndNullableMissingProfiles() {
        when(profileReadMapper.findSearchRowsByAccountIds(any())).thenReturn(List.of());

        var result = projection.compose(List.of(
                new UserAccountFact("u-2", "bob", null, null, null, true, false),
                new UserAccountFact("u-1", "alice", null, null, null, true, false)));

        assertThat(result.keySet()).containsExactly("u-2", "u-1");
        assertThat(result.get("u-1").name()).isNull();
    }

    @Test
    void unavailableAuthFailsClosed() {
        projection.setAccountQueryService(null);

        assertThatThrownBy(() -> projection.findByIds(Set.of("u-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account query unavailable");
    }

    @Test
    void unavailableProfileOwnerFailsClosed() {
        Set<String> ids = Set.of("u-1");
        when(accountQueryService.getAccountsByIds(ids))
                .thenReturn(RpcResult.success(List.of(account("u-1", "alice")), "t-profile"));
        when(profileReadMapper.findSearchRowsByAccountIds(ids))
                .thenThrow(new IllegalStateException("profile store unavailable"));

        assertThatThrownBy(() -> projection.findByIds(ids))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account query unavailable");
    }

    @Test
    void unavailableProfileOwnerFailsClosedForSingleFact() {
        when(accountQueryService.getAccountById("u-1"))
                .thenReturn(RpcResult.success(account("u-1", "alice"), "t-profile"));
        when(profileReadMapper.findSearchRowsByAccountIds(Set.of("u-1")))
                .thenThrow(new IllegalStateException("profile store unavailable"));

        assertThatThrownBy(() -> projection.findById("u-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account query unavailable");
    }

    private AuthAccountDTO account(String id, String username) {
        return new AuthAccountDTO(
                id, username, username + "@example.test", "USER", true, false,
                null, null, LocalDateTime.parse("2026-08-19T00:00:00"), null, 3L);
    }
}
