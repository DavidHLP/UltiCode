package com.ulticode.modules.reconciliation.port;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.auth.api.service.ReconciliationQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAppReconciliationReadPort} — the
 * App-side owner facts for the reconciliation aggregator.
 */
@ExtendWith(MockitoExtension.class)
class DefaultAppReconciliationReadPortTest {

    @Mock private AppReconciliationReadMapper mapper;
    @Mock private ReconciliationQueryService authQueryService;

    private DefaultAppReconciliationReadPort port;

    @BeforeEach
    void setUp() {
        port = new DefaultAppReconciliationReadPort(mapper);
        port.setAuthQueryService(authQueryService);
        lenient().when(mapper.existingChildTables()).thenReturn(List.of(
                "submissions", "solutions", "forum_posts", "notifications",
                "user_profiles", "contest_participants"));
    }

    @Test
    @DisplayName("countUserProfiles delegates to mapper")
    void countUserProfilesDelegates() {
        when(mapper.countUserProfiles()).thenReturn(7L);
        assertThat(port.countUserProfiles()).isEqualTo(7L);
    }

    @Test
    @DisplayName("countOrphans batches Auth existence and preserves grouped child-row counts")
    void countOrphansUsesAuthOwnerExistence() {
        when(mapper.submissionUserCounts("", 500)).thenReturn(List.of(
                reference("missing", 2L), reference("present", 1L)));
        when(mapper.userProfileAccountCounts("", 500)).thenReturn(List.of(
                reference("soft-deleted", 1L)));
        when(authQueryService.existingUserIds(Set.of("missing", "present")))
                .thenReturn(RpcResult.success(Set.of("present"), "t-1"));
        when(authQueryService.existingUserIds(Set.of("soft-deleted")))
                .thenReturn(RpcResult.success(Set.of("soft-deleted"), "t-1"));

        ReconciliationOrphanCounts counts = port.countOrphans();

        assertThat(counts.submissions()).isEqualTo(2L);
        assertThat(counts.solutions()).isZero();
        assertThat(counts.userProfiles()).isZero();
    }

    @Test
    @DisplayName("countOrphans fails closed when Auth owner is unavailable")
    void countOrphansFailsClosed() {
        when(mapper.submissionUserCounts("", 500)).thenReturn(List.of(reference("u-1", 1L)));
        when(authQueryService.existingUserIds(Set.of("u-1"))).thenReturn(null);

        assertThatThrownBy(port::countOrphans).isInstanceOf(BusinessException.class);
    }

    private static UserReferenceCount reference(String accountId, long rowCount) {
        UserReferenceCount reference = new UserReferenceCount();
        reference.setAccountId(accountId);
        reference.setRowCount(rowCount);
        return reference;
    }
}
