package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Review 2026-08-25 FINAL P1 regression tests: infrastructure failure must
 * surface as UPSTREAM_UNAVAILABLE (503), never as empty business data; only
 * App-owned display fields may degrade partially.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserEnricher failure semantics")
class AdminUserEnricherTest {

    @Mock private IdentityQueryService identityQueryService;
    @Mock private UserProfileQueryService userProfileQueryService;
    @Mock private AccountQueryService accountQueryService;

    private AdminUserEnricher enricher() {
        AdminUserEnricher enricher = new AdminUserEnricher();
        org.springframework.test.util.ReflectionTestUtils.setField(enricher, "identityQueryService", identityQueryService);
        org.springframework.test.util.ReflectionTestUtils.setField(enricher, "userProfileQueryService", userProfileQueryService);
        org.springframework.test.util.ReflectionTestUtils.setField(enricher, "accountQueryService", accountQueryService);
        return enricher;
    }

    private UserIdentityDTO identity(String accountId) {
        return new UserIdentityDTO(accountId, "user-" + accountId, "USER", true, false);
    }

    private AuthAccountDTO account(String accountId) {
        return new AuthAccountDTO(accountId, "user-" + accountId, "u@example.com", "USER",
                true, false, null, null, null, null, 1L);
    }

    @Nested
    @DisplayName("enrich()")
    class Enrich {

        @Test
        @DisplayName("throws UPSTREAM_UNAVAILABLE when the identity provider throws")
        void identityDownThrows() {
            when(identityQueryService.batchGetIdentity(any())).thenThrow(new RuntimeException("provider down"));

            assertThatThrownBy(() -> enricher().enrich(Set.of("u1")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("unavailable");
        }

        @Test
        @DisplayName("throws UPSTREAM_UNAVAILABLE when the identity RPC reports failure")
        void identityFailureThrows() {
            when(identityQueryService.batchGetIdentity(any()))
                    .thenReturn(RpcResult.failure(AdminErrorCode.USER_NOT_FOUND, "t-1"));

            assertThatThrownBy(() -> enricher().enrich(Set.of("u1")))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("returns partial summaries (no name/avatar) when the profile provider throws")
        void profileDownIsPartial() {
            when(identityQueryService.batchGetIdentity(any()))
                    .thenReturn(RpcResult.success(List.of(identity("u1")), "t-1"));
            when(userProfileQueryService.getProfilesByAccountIds(any())).thenThrow(new RuntimeException("provider down"));

            var result = enricher().enrich(Set.of("u1"));

            assertThat(result).containsKey("u1");
            assertThat(result.get("u1").username()).isEqualTo("user-u1");
            assertThat(result.get("u1").name()).isNull();
            assertThat(result.get("u1").avatar()).isNull();
        }
    }

    @Nested
    @DisplayName("enrichOne()")
    class EnrichOne {

        @Test
        @DisplayName("throws UPSTREAM_UNAVAILABLE when AccountQueryService throws")
        void accountDownThrows() {
            when(accountQueryService.getAccountById("u1")).thenThrow(new RuntimeException("provider down"));

            assertThatThrownBy(() -> enricher().enrichOne("u1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("unavailable");
        }

        @Test
        @DisplayName("returns null only for a genuine business absence")
        void absentAccountReturnsNull() {
            when(accountQueryService.getAccountById("ghost"))
                    .thenReturn(RpcResult.failure(AdminErrorCode.USER_NOT_FOUND, "t-1"));

            assertThat(enricher().enrichOne("ghost")).isNull();
        }

        @Test
        @DisplayName("keeps identity fields and degrades profile fields partially")
        void profileFailureIsPartial() {
            when(accountQueryService.getAccountById("u1")).thenReturn(RpcResult.success(account("u1"), "t-1"));
            when(userProfileQueryService.getProfileByAccountId("u1")).thenThrow(new RuntimeException("provider down"));

            AdminUserSummary summary = enricher().enrichOne("u1");

            assertThat(summary.username()).isEqualTo("user-u1");
            assertThat(summary.email()).isEqualTo("u@example.com");
            assertThat(summary.name()).isNull();
            assertThat(summary.avatar()).isNull();
        }
    }
}
