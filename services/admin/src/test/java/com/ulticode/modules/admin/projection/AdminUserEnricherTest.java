package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.admin.error.AdminErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserEnricher")
class AdminUserEnricherTest {

    @Mock private IdentityQueryService identityQueryService;
    @Mock private UserProfileQueryService userProfileQueryService;
    @Mock private AccountQueryService accountQueryService;

    private AdminUserEnricher enricher;

    private UserIdentityDTO identity(String id) {
        return new UserIdentityDTO(id, "user-" + id, "ADMIN", true, false);
    }

    private UserProfileDTO profile(String id) {
        return new UserProfileDTO(id, "Display " + id, "https://avatar/" + id,
                "bio", "Acme", "github", "Beijing", "twitter", "website", "zh-CN");
    }

    @BeforeEach
    void setUp() {
        enricher = new AdminUserEnricher();
        ReflectionTestUtils.setField(enricher, "identityQueryService", identityQueryService);
        ReflectionTestUtils.setField(enricher, "userProfileQueryService", userProfileQueryService);
        ReflectionTestUtils.setField(enricher, "accountQueryService", accountQueryService);
    }

    @Nested
    @DisplayName("owner account aggregation")
    class OwnerAccountAggregation {

        private AuthAccountDTO account(String id) {
            return new AuthAccountDTO(id, "user-" + id, id + "@example.com", "ADMIN",
                    true, false, null, null, LocalDateTime.now(), LocalDateTime.now(), 1L);
        }

        @Test
        void pageMergesProfilesAndReportsStatus() {
            when(accountQueryService.queryAccounts(any(AccountQueryDTO.class))).thenReturn(
                    RpcResult.page(List.of(account("u1")), 1L, 1, 10, "t"));
            when(userProfileQueryService.getProfilesByAccountIds(any())).thenReturn(
                    RpcResult.success(List.of(profile("u1")), "t"));

            AdminUserEnricher.AccountPage result =
                    enricher.queryAccountsWithProfiles(new AccountQueryDTO(
                            null, null, null, null, 1, 10, null, null));

            assertThat(result.status()).isEqualTo(DegradationStatus.OK);
            assertThat(result.total()).isEqualTo(1L);
            assertThat(result.profiles().get("u1").name()).isEqualTo("Display u1");
        }

        @Test
        void authoritativeNotFoundRemainsBusinessNotFound() {
            when(accountQueryService.getAccountById("missing")).thenReturn(
                    RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t"));

            assertThat(enricher.findAccountWithProfile("missing")).isNull();
        }
    }

    @Nested
    @DisplayName("enrichWithStatus()")
    class EnrichWithStatus {

        @Test
        @DisplayName("both providers healthy -> OK and merged data")
        void healthyPathIsOk() {
            when(identityQueryService.batchGetIdentity(any())).thenReturn(
                    RpcResult.success(List.of(identity("u1")), "t"));
            when(userProfileQueryService.getProfilesByAccountIds(any())).thenReturn(
                    RpcResult.success(List.of(profile("u1")), "t"));

            AdminUserEnricher.EnrichedUsers result = enricher.enrichWithStatus(Set.of("u1"));

            assertThat(result.status()).isEqualTo(DegradationStatus.OK);
            assertThat(result.users()).containsKey("u1");
            assertThat(result.users().get("u1").username()).isEqualTo("user-u1");
            assertThat(result.users().get("u1").name()).isEqualTo("Display u1");
        }

        @Test
        @DisplayName("empty input -> OK with empty map")
        void emptyInputIsOk() {
            var result = enricher.enrichWithStatus(Set.of());

            assertThat(result.status()).isEqualTo(DegradationStatus.OK);
            assertThat(result.users()).isEmpty();
        }

        @Test
        @DisplayName("profile provider down -> PARTIAL with identities kept")
        void profileDownIsPartial() {
            when(identityQueryService.batchGetIdentity(any())).thenReturn(
                    RpcResult.success(List.of(identity("u1")), "t"));
            when(userProfileQueryService.getProfilesByAccountIds(any()))
                    .thenThrow(new RuntimeException("app provider down"));

            AdminUserEnricher.EnrichedUsers result = enricher.enrichWithStatus(Set.of("u1"));

            assertThat(result.status()).isEqualTo(DegradationStatus.PARTIAL);
            assertThat(result.users().get("u1").username()).isEqualTo("user-u1");
            assertThat(result.users().get("u1").name()).isNull();
        }

        @Test
        @DisplayName("profile provider failure result -> PARTIAL")
        void profileFailureResultIsPartial() {
            when(identityQueryService.batchGetIdentity(any())).thenReturn(
                    RpcResult.success(List.of(identity("u1")), "t"));
            when(userProfileQueryService.getProfilesByAccountIds(any())).thenReturn(
                    RpcResult.failure(AuthErrorCode.INVALID_ACCOUNT_REQUEST, "t"));

            AdminUserEnricher.EnrichedUsers result = enricher.enrichWithStatus(Set.of("u1"));

            assertThat(result.status()).isEqualTo(DegradationStatus.PARTIAL);
            assertThat(result.users().get("u1").username()).isEqualTo("user-u1");
        }

        @Test
        @DisplayName("identity provider down -> PARTIAL with profiles kept")
        void identityDownIsPartial() {
            when(identityQueryService.batchGetIdentity(any()))
                    .thenThrow(new RuntimeException("auth provider down"));
            when(userProfileQueryService.getProfilesByAccountIds(any())).thenReturn(
                    RpcResult.success(List.of(profile("u1")), "t"));

            AdminUserEnricher.EnrichedUsers result = enricher.enrichWithStatus(Set.of("u1"));

            assertThat(result.status()).isEqualTo(DegradationStatus.PARTIAL);
            assertThat(result.users().get("u1").name()).isEqualTo("Display u1");
            assertThat(result.users().get("u1").username()).isNull();
        }

        @Test
        @DisplayName("both providers down -> UNAVAILABLE, not a silently empty OK")
        void bothDownIsUnavailable() {
            when(identityQueryService.batchGetIdentity(any()))
                    .thenThrow(new RuntimeException("auth provider down"));
            when(userProfileQueryService.getProfilesByAccountIds(any()))
                    .thenThrow(new RuntimeException("app provider down"));

            AdminUserEnricher.EnrichedUsers result = enricher.enrichWithStatus(Set.of("u1"));

            assertThat(result.status()).isEqualTo(DegradationStatus.UNAVAILABLE);
            assertThat(result.users()).isEmpty();
        }
    }

    @Nested
    @DisplayName("enrich()")
    class Enrich {

        @Test
        @DisplayName("healthy path unchanged: merged best-effort map")
        void healthyPathUnchanged() {
            when(identityQueryService.batchGetIdentity(any())).thenReturn(
                    RpcResult.success(List.of(identity("u1")), "t"));
            when(userProfileQueryService.getProfilesByAccountIds(any())).thenReturn(
                    RpcResult.success(List.of(profile("u1")), "t"));

            Map<String, AdminUserSummary> users = enricher.enrich(Set.of("u1"));

            assertThat(users).containsKey("u1");
            assertThat(users.get("u1").role()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("both providers down -> typed 503 OWNER_QUERY_UNAVAILABLE instead of empty map")
        void totalOutageThrows() {
            when(identityQueryService.batchGetIdentity(any()))
                    .thenThrow(new RuntimeException("auth provider down"));
            when(userProfileQueryService.getProfilesByAccountIds(any()))
                    .thenThrow(new RuntimeException("app provider down"));

            assertThatThrownBy(() -> enricher.enrich(Set.of("u1")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
        }
    }

    @Nested
    @DisplayName("enrichOne()")
    class EnrichOne {

        private AuthAccountDTO account(String id) {
            return new AuthAccountDTO(id, "user-" + id, id + "@example.com", "ADMIN",
                    true, false, null, null, LocalDateTime.now(), LocalDateTime.now(), 1L);
        }

        @Test
        @DisplayName("account found, profile provider down -> summary without profile fields")
        void accountFoundProfileDown() {
            when(accountQueryService.getAccountById("u1")).thenReturn(
                    RpcResult.success(account("u1"), "t"));
            when(userProfileQueryService.getProfileByAccountId("u1"))
                    .thenThrow(new RuntimeException("app provider down"));

            AdminUserSummary summary = enricher.enrichOne("u1");

            assertThat(summary).isNotNull();
            assertThat(summary.username()).isEqualTo("user-u1");
            assertThat(summary.email()).isEqualTo("u1@example.com");
            assertThat(summary.name()).isNull();
        }

        @Test
        @DisplayName("unknown account with healthy providers -> null (business not-found)")
        void unknownAccountIsNull() {
            when(accountQueryService.getAccountById("missing")).thenReturn(
                    RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t"));
            when(identityQueryService.batchGetIdentity(any())).thenReturn(
                    RpcResult.success(List.of(), "t"));
            when(userProfileQueryService.getProfilesByAccountIds(any())).thenReturn(
                    RpcResult.success(List.of(), "t"));

            assertThat(enricher.enrichOne("missing")).isNull();
        }

        @Test
        @DisplayName("all sources down -> typed 503 instead of disguised USER_NOT_FOUND")
        void totalOutageThrows() {
            when(accountQueryService.getAccountById("u1"))
                    .thenThrow(new RuntimeException("auth provider down"));
            when(identityQueryService.batchGetIdentity(any()))
                    .thenThrow(new RuntimeException("auth provider down"));
            when(userProfileQueryService.getProfilesByAccountIds(any()))
                    .thenThrow(new RuntimeException("app provider down"));

            assertThatThrownBy(() -> enricher.enrichOne("u1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
        }
    }
}
