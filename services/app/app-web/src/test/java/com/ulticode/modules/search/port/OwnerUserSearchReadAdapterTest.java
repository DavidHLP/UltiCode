package com.ulticode.modules.search.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.user.port.DefaultUserFactsReadProjection;
import com.ulticode.app.user.port.UserProfileReadMapper;
import com.ulticode.app.user.port.UserProfileReadRow;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.search.backfill.SearchBackfillDocument;
import com.ulticode.modules.search.backfill.UserSearchBackfillReadPort;
import com.ulticode.modules.search.backfill.SearchBackfillReadPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerUserSearchReadAdapterTest {

    @Mock
    private UserProfileReadMapper profileReadMapper;

    @Mock
    private AccountQueryService accountQueryService;

    private OwnerUserSearchReadAdapter adapter;
    private DefaultUserFactsReadProjection factsProjection;

    @BeforeEach
    void setUp() {
        factsProjection = new DefaultUserFactsReadProjection(profileReadMapper);
        factsProjection.setAccountQueryService(accountQueryService);
        adapter = new OwnerUserSearchReadAdapter(profileReadMapper, factsProjection, accountQueryService);
    }

    @Test
    void usernameSearchUsesAuthAccountFieldsAndLocalProfileFields() {
        AuthAccountDTO account = account("u-1", "alice", "2026-08-01T00:00:00", null);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(account), 1, 1, 100, "t-search"));
        when(profileReadMapper.findSearchCandidates("ali", 0, 100)).thenReturn(List.of());
        when(profileReadMapper.findSearchRowsByAccountIds(Set.of("u-1")))
                .thenReturn(List.of(profile("u-1", "Alice", "/alice.png", null)));

        List<UserDirectoryRow> rows = adapter.search("ali", 10);

        assertThat(rows).singleElement().satisfies(directoryRow -> {
            UserSearchRow row = directoryRow.row();
            assertThat(row.getId()).isEqualTo("u-1");
            assertThat(row.getUsername()).isEqualTo("alice");
            assertThat(row.getName()).isEqualTo("Alice");
            assertThat(row.getAvatar()).isEqualTo("/alice.png");
        });
    }

    @Test
    void profileNameSearchUnionsAndDeduplicatesDeterministically() {
        AuthAccountDTO alice = account("u-1", "alice", "2026-08-01T00:00:00", null);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(alice), 1, 1, 100, "t-search"));
        when(profileReadMapper.findSearchCandidates("ali", 0, 100))
                .thenReturn(List.of(profile("u-1", "Alice", "/a.png", null),
                        profile("u-2", "Alice Cooper", "/b.png", null)));
        when(accountQueryService.getAccountsByIds(Set.of("u-1", "u-2")))
                .thenReturn(RpcResult.success(List.of(
                        alice, account("u-2", "bob", "2026-08-01T00:00:00", null)), "t-search"));
        when(profileReadMapper.findSearchRowsByAccountIds(any()))
                .thenReturn(List.of(profile("u-1", "Alice", "/a.png", null),
                        profile("u-2", "Alice Cooper", "/b.png", null)));
        List<UserDirectoryRow> rows = adapter.search("ali", 10);

        assertThat(rows).extracting(directoryRow -> directoryRow.row().getId())
                .containsExactly("u-1", "u-2");
        assertThat(rows).extracting(directoryRow -> directoryRow.row().getUsername())
                .containsExactly("alice", "bob");
    }

    @Test
    void pagedSearchMergesOwnerStreamsAndAppliesOffset() {
        AuthAccountDTO alice = account("u-1", "alice", "2026-08-01T00:00:00", null);
        AuthAccountDTO carol = account("u-3", "carol", "2026-08-01T00:00:00", null);
        AuthAccountDTO bob = account("u-2", "bob", "2026-08-01T00:00:00", null);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(alice, carol), 2, 1, 100, "t-search"));
        when(profileReadMapper.findSearchCandidates("a", 0, 100))
                .thenReturn(List.of(profile("u-2", "Alice Bob", null, null)));
        when(accountQueryService.getAccountsByIds(Set.of("u-2")))
                .thenReturn(RpcResult.success(List.of(bob), "t-search"));
        when(profileReadMapper.findSearchRowsByAccountIds(any())).thenReturn(List.of());

        List<UserDirectoryRow> rows = adapter.search("a", 1, 2);

        assertThat(rows).extracting(directoryRow -> directoryRow.row().getId())
                .containsExactly("u-2", "u-3");
    }

    @Test
    void countReturnsUniqueActiveUnionAcrossOwners() {
        AuthAccountDTO alice = account("u-1", "alice", "2026-08-01T00:00:00", null);
        AuthAccountDTO bob = account("u-2", "bob", "2026-08-01T00:00:00", null);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(alice, bob), 2, 1, 100, "t-search"));
        when(profileReadMapper.countSearchCandidates("ali")).thenReturn(2L);
        when(profileReadMapper.findSearchCandidates("ali", 0, 100))
                .thenReturn(List.of(profile("u-1", "Alice", null, null),
                        profile("u-3", "Alice Carol", null, null)));
        when(accountQueryService.countAccountsByIdsExcludingUsernameMatch(Set.of("u-1", "u-3"), "ali"))
                .thenReturn(RpcResult.success(1L, "t-search"));

        assertThat(adapter.count("ali")).isEqualTo(3);
    }

    @Test
    void countDeduplicatesUsingOwnerDatabasePredicates() {
        AuthAccountDTO jose = account("u-1", "José", "2026-08-01T00:00:00", null);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(jose), 1, 1, 100, "t-search"));
        when(profileReadMapper.countSearchCandidates("e")).thenReturn(1L);
        when(profileReadMapper.findSearchCandidates("e", 0, 100))
                .thenReturn(List.of(profile("u-1", "Elena", null, null)));
        when(accountQueryService.countAccountsByIdsExcludingUsernameMatch(Set.of("u-1"), "e"))
                .thenReturn(RpcResult.success(0L, "t-search"));

        assertThat(adapter.count("e")).isEqualTo(1);
    }

    @Test
    void missingAccountIsDroppedAndMissingProfileKeepsNullableFields() {
        AuthAccountDTO account = account("u-1", "alice", "2026-08-01T00:00:00", null);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(account), 1, 1, 100, "t-search"));
        when(profileReadMapper.findSearchCandidates("ali", 0, 100))
                .thenReturn(List.of(profile("gone", "Alice", null, null)));
        when(accountQueryService.getAccountsByIds(Set.of("gone")))
                .thenReturn(RpcResult.success(List.of(), "t-search"));
        when(profileReadMapper.findSearchRowsByAccountIds(any())).thenReturn(List.of());

        List<UserDirectoryRow> rows = adapter.search("ali", 10);

        assertThat(rows).singleElement().satisfies(directoryRow -> {
            UserSearchRow row = directoryRow.row();
            assertThat(row.getId()).isEqualTo("u-1");
            assertThat(row.getName()).isNull();
            assertThat(row.getAvatar()).isNull();
        });
    }

    @Test
    void findByIdPreservesAuthAndProfileFreshnessSeparately() {
        AuthAccountDTO account = account("u-1", "alice", "2026-08-01T00:00:00", "2026-08-16T08:00:00");
        when(accountQueryService.getAccountById("u-1")).thenReturn(RpcResult.success(account, "t-row"));
        when(profileReadMapper.findSearchRowsByAccountIds(Set.of("u-1")))
                .thenReturn(List.of(profile("u-1", "Alice", "/alice.png", "2026-08-16T12:00:00")));

        UserDirectoryRow directoryRow = adapter.findById("u-1");

        assertThat(directoryRow.authUpdatedAt())
                .isEqualTo(LocalDateTime.parse("2026-08-16T08:00:00"));
        assertThat(directoryRow.profileUpdatedAt())
                .isEqualTo(LocalDateTime.parse("2026-08-16T12:00:00"));
        assertThat(directoryRow.freshAt())
                .isEqualTo(LocalDateTime.parse("2026-08-16T12:00:00"));
    }

    @Test
    void findByIdTreatsMissingProfileAsNullableAndKeepsAuthFreshness() {
        AuthAccountDTO account = account("u-3", "carol", "2026-08-01T00:00:00", "2026-08-16T08:00:00");
        when(accountQueryService.getAccountById("u-3")).thenReturn(RpcResult.success(account, "t-row"));
        when(profileReadMapper.findSearchRowsByAccountIds(Set.of("u-3"))).thenReturn(null);

        UserDirectoryRow directoryRow = adapter.findById("u-3");

        assertThat(directoryRow.row().getName()).isNull();
        assertThat(directoryRow.profileUpdatedAt()).isNull();
        assertThat(directoryRow.authUpdatedAt())
                .isEqualTo(LocalDateTime.parse("2026-08-16T08:00:00"));
        assertThat(directoryRow.freshAt())
                .isEqualTo(directoryRow.authUpdatedAt());
    }
    @Test
    void enumerationPaginatesByStableAccountIdAndCarriesProfileWatermark() {
        AuthAccountDTO first = account("u-1", "alice", "2026-08-01T00:00:00", "2026-08-16T08:00:00");
        AuthAccountDTO second = account("u-2", "bob", "2026-08-01T00:00:00", "2026-08-16T08:00:00");
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(first, second), 2, 1, 100, "t-backfill"));
        when(profileReadMapper.findSearchRowsByAccountIds(any()))
                .thenReturn(List.of(profile("u-2", "Bob", "/b.png", "2026-08-16T12:00:00")));

        UserSearchBackfillReadPort backfill = new UserSearchBackfillReadPort(adapter);
        List<SearchBackfillDocument> rows = backfill.enumerateForBackfill(1, 1);

        assertThat(rows).singleElement().satisfies(document -> {
            assertThat(document.documentId()).isEqualTo("u-2");
            assertThat(document.versionMillis())
                    .isEqualTo(SearchBackfillReadPort.toVersionMillis(LocalDateTime.parse("2026-08-16T12:00:00")));
            assertThat(document.document()).containsEntry("username", "bob")
                    .containsEntry("name", "Bob").containsEntry("avatar", "/b.png");
        });
    }

    @Test
    void findByIdsUsesAuthBatchAndKeepsMissingIdsAbsent() {
        Set<String> ids = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 101; i++) {
            ids.add("u-" + i);
        }
        when(accountQueryService.getAccountsByIds(ids)).thenReturn(RpcResult.success(
                List.of(account("u-100", "last", "2026-08-01T00:00:00", null)), "t-batch"));
        when(profileReadMapper.findSearchRowsByAccountIds(ids))
                .thenReturn(List.of(profile("u-100", "Last", null, null)));

        List<UserDirectoryRow> rows = adapter.findByIds(ids);

        assertThat(rows).singleElement().satisfies(directoryRow -> {
            assertThat(directoryRow.row().getId()).isEqualTo("u-100");
            assertThat(directoryRow.row().getUsername()).isEqualTo("last");
        });
        verify(accountQueryService, never()).queryAccounts(any());
    }

    @Test
    void findByIdsDeduplicatesReturnedAccountsAndDropsOutOfRequestIds() {
        Set<String> ids = new java.util.LinkedHashSet<>(List.of("u-1", "u-2"));
        AuthAccountDTO first = account("u-1", "alice", "2026-08-01T00:00:00", null);
        AuthAccountDTO duplicate = account("u-1", "alice", "2026-08-02T00:00:00", null);
        AuthAccountDTO outside = account("u-3", "eve", "2026-08-01T00:00:00", null);
        when(accountQueryService.getAccountsByIds(ids))
                .thenReturn(RpcResult.success(List.of(first, duplicate, outside), "t-batch"));
        when(profileReadMapper.findSearchRowsByAccountIds(ids)).thenReturn(List.of());

        List<UserDirectoryRow> rows = adapter.findByIds(ids);

        assertThat(rows).extracting(directoryRow -> directoryRow.row().getId())
                .containsExactly("u-1");
    }


    @Test
    void directoryRowFreshnessUsesNewestOwnerTimestamp() {
        UserSearchRow row = new UserSearchRow();
        row.setUpdatedAt(LocalDateTime.parse("2026-08-16T08:00:00"));
        row.setProfileUpdatedAt(LocalDateTime.parse("2026-08-16T12:00:00"));

        UserDirectoryRow directoryRow = UserDirectoryRow.from(row);

        assertThat(directoryRow.contractVersion()).isEqualTo(UserDirectoryQueryPort.CONTRACT_VERSION);
        assertThat(directoryRow.freshAt())
                .isEqualTo(LocalDateTime.parse("2026-08-16T12:00:00"));
        assertThat(directoryRow.profileUpdatedAt())
                .isAfter(directoryRow.authUpdatedAt());
    }
    @Test
    void unavailableAuthFailsClosed() {
        adapter = new OwnerUserSearchReadAdapter(profileReadMapper, factsProjection, null);
        assertThatThrownBy(() -> adapter.search("alice", 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account query unavailable");
    }

    @Test
    void unavailableIdentityFailsClosedForProfileSearch() {
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(), 0, 1, 100, "t-search"));
        when(profileReadMapper.findSearchCandidates("alice", 0, 100))
                .thenReturn(List.of(profile("u-1", "Alice", null, null)));
        when(accountQueryService.getAccountsByIds(Set.of("u-1")))
                .thenThrow(new RuntimeException("account query unavailable"));
        assertThatThrownBy(() -> adapter.search("alice", 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account query unavailable");
    }

    private AuthAccountDTO account(String id, String username, String joinedAt, String updatedAt) {
        return new AuthAccountDTO(
                id, username, username + "@example.test", "USER", true, false, null, null,
                LocalDateTime.parse(joinedAt),
                null, 1L,
                updatedAt == null ? LocalDateTime.parse(joinedAt) : LocalDateTime.parse(updatedAt),
                null);
    }

    private UserProfileReadRow profile(String id, String name, String avatar, String updatedAt) {
        UserProfileReadRow row = new UserProfileReadRow();
        row.setAccountId(id);
        row.setName(name);
        row.setAvatar(avatar);
        row.setUpdatedAt(updatedAt == null ? null : LocalDateTime.parse(updatedAt));
        return row;
    }
}
