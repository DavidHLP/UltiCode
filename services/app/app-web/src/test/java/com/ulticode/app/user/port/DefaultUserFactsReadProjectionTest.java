package com.ulticode.app.user.port;

import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultUserFactsReadProjectionTest {

    @Mock private UserProfileReadMapper profileReadMapper;
    @Mock private AccountQueryService accountQueryService;

    private DefaultUserFactsReadProjection projection;
    private DefaultUserDirectoryReadProjection directory;

    @BeforeEach
    void setUp() {
        projection = new DefaultUserFactsReadProjection(profileReadMapper);
        projection.setAccountQueryService(accountQueryService);
        directory = new DefaultUserDirectoryReadProjection(profileReadMapper);
        directory.setAccountQueryService(accountQueryService);
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
    void findByIdsReturnsRequestedOrderWhenAuthReturnsAnotherOrder() {
        Set<String> ids = new LinkedHashSet<>(List.of("u-2", "u-1"));
        when(accountQueryService.getAccountsByIds(ids)).thenReturn(RpcResult.success(List.of(
                account("u-1", "alice"), account("u-2", "bob")), "t-order"));
        when(profileReadMapper.findSearchRowsByAccountIds(ids)).thenReturn(List.of());

        assertThat(projection.findByIds(ids).keySet()).containsExactly("u-2", "u-1");
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothProjectionsTreatOnlyAuthAccountNotFoundAsMissing(boolean directoryRead) {
        when(accountQueryService.getAccountById("missing"))
                .thenReturn(RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-missing"));

        assertThat(readById(directoryRead, "missing")).isNull();
        verifyNoInteractions(profileReadMapper);

        when(accountQueryService.getAccountById("missing")).thenReturn(RpcResult.failure(
                new RpcResult.ErrorPayload("other-owner", AuthErrorCode.ACCOUNT_NOT_FOUND.code(), "missing"),
                "t-other-owner"));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> readById(directoryRead, "missing"))
                .satisfies(exception -> assertThat(exception.getErrorCode()).isEqualTo(BaseErrorCode.UNKNOWN_ERROR))
                .withMessage("Auth account query unavailable");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothProjectionsRejectNullResponsesAndRpcAuthorizationFailures(boolean directoryRead) {
        when(accountQueryService.getAccountById("u-1")).thenReturn(null,
                RpcResult.failure(BaseErrorCode.UNAUTHORIZED, "t-unauthorized"),
                RpcResult.failure(BaseErrorCode.FORBIDDEN, "t-forbidden"));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatExceptionOfType(BusinessException.class)
                    .isThrownBy(() -> readById(directoryRead, "u-1"))
                    .satisfies(exception -> assertThat(exception.getErrorCode()).isEqualTo(BaseErrorCode.UNKNOWN_ERROR))
                    .withMessage("Auth account query unavailable");
        }
        verifyNoInteractions(profileReadMapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothProjectionsPreserveThrownBusinessFailuresAndTraceIds(boolean directoryRead) {
        for (BaseErrorCode code : List.of(BaseErrorCode.UNAUTHORIZED, BaseErrorCode.FORBIDDEN)) {
            BusinessException failure = new BusinessException(code, "owner denied", "t-owner-denied");
            doThrow(failure).when(accountQueryService).getAccountById("u-1");

            assertThatThrownBy(() -> readById(directoryRead, "u-1")).isSameAs(failure);
        }
        verifyNoInteractions(profileReadMapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothProjectionsTranslateTransportFailuresToUnavailable(boolean directoryRead) {
        when(accountQueryService.getAccountById("u-1"))
                .thenThrow(new IllegalStateException("transport failed"));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> readById(directoryRead, "u-1"))
                .satisfies(exception -> assertThat(exception.getErrorCode()).isEqualTo(BaseErrorCode.UNKNOWN_ERROR))
                .withMessage("Auth account query unavailable");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothProjectionsRejectMissingBatchData(boolean directoryRead) {
        when(accountQueryService.getAccountsByIds(Set.of("u-1")))
                .thenReturn(new RpcResult<>(true, null, null, null, "t-null-data", null, null));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> {
                    if (directoryRead) {
                        directory.selectByIds(Set.of("u-1"));
                    } else {
                        projection.findByIds(Set.of("u-1"));
                    }
                })
                .satisfies(exception -> assertThat(exception.getErrorCode()).isEqualTo(BaseErrorCode.UNKNOWN_ERROR))
                .withMessage("Auth account query unavailable");
        verifyNoInteractions(profileReadMapper);
    }

    @Test
    void publicReadSeamsRemainNarrow() {
        assertThat(UserFactsProjection.class.isAssignableFrom(DefaultUserFactsReadProjection.class))
                .isTrue();
        assertThat(UserDirectoryProjection.class.isAssignableFrom(DefaultUserFactsReadProjection.class))
                .isFalse();
        assertThat(UserDirectoryProjection.class.isAssignableFrom(DefaultUserDirectoryReadProjection.class))
                .isTrue();
        assertThat(List.of(UserFactsProjection.class.getDeclaredMethods()))
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("findById", "findByIds", "compose");
        assertThat(List.of(UserDirectoryProjection.class.getDeclaredMethods()))
                .extracting(Method::getName)
                .containsExactlyInAnyOrder(
                        "selectById", "selectByUsername", "selectByEmail",
                        "selectByIds", "selectActiveUsers", "countActiveUsers", "countById");
    }

    private Object readById(boolean directoryRead, String id) {
        return directoryRead ? directory.selectById(id) : projection.findById(id);
    }

    private AuthAccountDTO account(String id, String username) {
        return new AuthAccountDTO(
                id, username, username + "@example.test", "USER", true, false,
                null, null, LocalDateTime.parse("2026-08-19T00:00:00"), null, 3L);
    }
}
