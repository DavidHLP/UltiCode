package com.ulticode.modules.search.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.user.port.UserProfileReadMapper;
import com.ulticode.app.user.port.UserProfileReadRow;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.IdentityQueryService;
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

    @Mock
    private IdentityQueryService identityQueryService;

    private OwnerUserSearchReadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OwnerUserSearchReadAdapter(profileReadMapper);
        adapter.setAccountQueryService(accountQueryService);
        adapter.setIdentityQueryService(identityQueryService);
    }

    @Test
    void usernameSearchUsesAuthAccountFieldsAndLocalProfileFields() {
        AuthAccountDTO account = account("u-1", "alice", "2026-08-01T00:00:00", null);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(account), 1, 1, 100, "t-search"));
        when(profileReadMapper.findSearchCandidatesBounded("ali", 10)).thenReturn(List.of());
        when(profileReadMapper.findSearchRowsByAccountIds(Set.of("u-1")))
                .thenReturn(List.of(profile("u-1", "Alice", "/alice.png", null)));

        List<UserSearchRow> rows = adapter.searchIndex("ali", 10);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo("u-1");
            assertThat(row.getUsername()).isEqualTo("alice");
            assertThat(row.getName()).isEqualTo("Alice");
            assertThat(row.getAvatar()).isEqualTo("/alice.png");
        });
        verify(identityQueryService, never()).batchGetIdentity(any());
    }

    @Test
    void profileNameSearchUnionsAndDeduplicatesDeterministically() {
        AuthAccountDTO alice = account("u-1", "alice", "2026-08-01T00:00:00", null);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(alice), 1, 1, 100, "t-search"));
        when(profileReadMapper.findSearchCandidatesBounded("ali", 10))
                .thenReturn(List.of(profile("u-1", "Alice", "/a.png", null),
                        profile("u-2", "Alice Cooper", "/b.png", null)));
        when(identityQueryService.batchGetIdentity(Set.of("u-2")))
                .thenReturn(RpcResult.success(List.of(
                        new UserIdentityDTO("u-2", "bob", "USER", true, false)), "t-search"));
        when(profileReadMapper.findSearchRowsByAccountIds(any()))
                .thenReturn(List.of(profile("u-1", "Alice", "/a.png", null),
                        profile("u-2", "Alice Cooper", "/b.png", null)));

        List<UserSearchRow> rows = adapter.searchIndex("ali", 10);

        assertThat(rows).extracting(UserSearchRow::getId).containsExactly("u-1", "u-2");
        assertThat(rows).extracting(UserSearchRow::getUsername).containsExactly("alice", "bob");
    }

    @Test
    void missingAccountIsDroppedAndMissingProfileKeepsNullableFields() {
        AuthAccountDTO account = account("u-1", "alice", "2026-08-01T00:00:00", null);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(account), 1, 1, 100, "t-search"));
        when(profileReadMapper.findSearchCandidatesBounded("ali", 10))
                .thenReturn(List.of(profile("gone", "Alice", null, null)));
        when(identityQueryService.batchGetIdentity(Set.of("gone")))
                .thenReturn(RpcResult.success(List.of(), "t-search"));
        when(profileReadMapper.findSearchRowsByAccountIds(any())).thenReturn(List.of());

        List<UserSearchRow> rows = adapter.searchIndex("ali", 10);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo("u-1");
            assertThat(row.getName()).isNull();
            assertThat(row.getAvatar()).isNull();
        });
    }

    @Test
    void findIndexRowByIdComposesAccountAndProfile() {
        AuthAccountDTO account = account("u-1", "alice", "2026-08-01T00:00:00", null);
        when(accountQueryService.getAccountById("u-1")).thenReturn(RpcResult.success(account, "t-row"));
        when(profileReadMapper.findByAccountId("u-1")).thenReturn(new UserProfileDTO(
                "u-1", "Alice", "/alice.png", null, null, null, null, null, null, null));

        UserSearchRow row = adapter.findIndexRowById("u-1");

        assertThat(row.getId()).isEqualTo("u-1");
        assertThat(row.getUsername()).isEqualTo("alice");
        assertThat(row.getName()).isEqualTo("Alice");
        assertThat(row.getAvatar()).isEqualTo("/alice.png");
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
    void unavailableAuthFailsClosed() {
        adapter.setAccountQueryService(null);

        assertThatThrownBy(() -> adapter.searchIndex("alice", 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account query unavailable");
    }
    @Test
    void unavailableIdentityFailsClosedForProfileSearch() {
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(), 0, 1, 100, "t-search"));
        when(profileReadMapper.findSearchCandidatesBounded("alice", 10))
                .thenReturn(List.of(profile("u-1", "Alice", null, null)));
        assertThatThrownBy(() -> adapter.searchIndex("alice", 10))
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
