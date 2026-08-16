package com.ulticode.modules.search.backfill;

import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.port.UserSearchReadMapper;
import com.ulticode.modules.search.port.UserSearchRow;
import com.ulticode.modules.search.source.SearchDocumentBuilders;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SEARCH-003 user backfill enumeration (DEC-017).
 *
 * <p>The user document changes on identity writes (Auth, {@code users}
 * row), profile writes (App, {@code user_profiles} row) and soft deletes.
 * The version is the GREATEST of the row timestamps that can advance, so a
 * snapshot is ordered against every live event that touches either side.
 */
@Component
@RequiredArgsConstructor
public class UserSearchBackfillReadPort implements SearchBackfillReadPort {

    private final UserSearchReadMapper userSearchReadMapper;

    @Override
    public SearchIndexType type() {
        return SearchIndexType.USERS;
    }

    @Override
    public List<SearchBackfillDocument> enumerateForBackfill(int offset, int limit) {
        return userSearchReadMapper.enumerateIndex(offset, limit).stream()
                .map(row -> new SearchBackfillDocument(
                        row.getId(),
                        userVersionMillis(row),
                        SearchDocumentBuilders.user(
                                row.getId(), row.getUsername(), row.getName(), row.getAvatar())))
                .toList();
    }

    private long userVersionMillis(UserSearchRow row) {
        LocalDateTime max = row.getUpdatedAt();
        if (row.getProfileUpdatedAt() != null
                && (max == null || row.getProfileUpdatedAt().isAfter(max))) {
            max = row.getProfileUpdatedAt();
        }
        if (row.getDeletedAt() != null && (max == null || row.getDeletedAt().isAfter(max))) {
            max = row.getDeletedAt();
        }
        if (row.getJoinedAt() != null && (max == null || row.getJoinedAt().isAfter(max))) {
            max = row.getJoinedAt();
        }
        return SearchBackfillReadPort.toVersionMillis(max);
    }
}
