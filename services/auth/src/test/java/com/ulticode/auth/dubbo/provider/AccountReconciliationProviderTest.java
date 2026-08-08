package com.ulticode.auth.dubbo.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.auth.api.dto.AuthReconciliationOrphanCounts;
import com.ulticode.auth.reconciliation.ReconciliationQueryMapper;
import com.ulticode.common.rpc.RpcResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AccountReconciliationProvider}.
 */
@ExtendWith(MockitoExtension.class)
class AccountReconciliationProviderTest {

    @Mock
    private ReconciliationQueryMapper reconciliationQueryMapper;

    @InjectMocks
    private AccountReconciliationProvider provider;

    @Test
    @DisplayName("countActiveUsers delegates to mapper and wraps in success")
    void countActiveUsersDelegates() {
        when(reconciliationQueryMapper.countActiveUsers()).thenReturn(42L);
        RpcResult<Long> result = provider.countActiveUsers();
        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(42L);
    }

    @Test
    @DisplayName("existingUserIds returns empty success for null/empty input without DB access")
    void existingUserIdsEmptyInput() {
        assertThat(provider.existingUserIds(null).data()).isEmpty();
        assertThat(provider.existingUserIds(Set.of()).data()).isEmpty();
        assertThat(provider.existingUserIds(Set.of("  ")).data()).isEmpty();
        verify(reconciliationQueryMapper, never()).selectExistingIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("existingUserIds passes deduped non-blank ids and returns found subset")
    void existingUserIdsDelegates() {
        when(reconciliationQueryMapper.selectExistingIds(Set.of("u-1", "u-2", "u-3")))
                .thenReturn(Set.of("u-1", "u-2"));
        RpcResult<Set<String>> result = provider.existingUserIds(List.of("u-1", "u-2", "u-3"));
        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsExactlyInAnyOrder("u-1", "u-2");
    }

    @Test
    @DisplayName("countAuthOrphans maps all four mapper counts into the record")
    void countAuthOrphansMapsAllFour() {
        when(reconciliationQueryMapper.countOrphanRefreshTokens()).thenReturn(1L);
        when(reconciliationQueryMapper.countOrphanPasswordResets()).thenReturn(2L);
        when(reconciliationQueryMapper.countOrphanOauthProviderIdentities()).thenReturn(3L);
        when(reconciliationQueryMapper.countOrphanUserPermissions()).thenReturn(4L);

        RpcResult<AuthReconciliationOrphanCounts> result = provider.countAuthOrphans();

        assertThat(result.success()).isTrue();
        assertThat(result.data().refreshTokens()).isEqualTo(1L);
        assertThat(result.data().passwordResets()).isEqualTo(2L);
        assertThat(result.data().oauthProviderIdentities()).isEqualTo(3L);
        assertThat(result.data().userPermissions()).isEqualTo(4L);
    }
}
