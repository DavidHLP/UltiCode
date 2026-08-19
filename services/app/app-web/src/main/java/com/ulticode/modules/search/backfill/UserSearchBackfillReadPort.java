package com.ulticode.modules.search.backfill;

import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.port.UserDirectoryQueryPort;
import com.ulticode.modules.search.port.UserDirectoryRow;
import com.ulticode.modules.search.source.SearchDocumentBuilders;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SEARCH-003 user backfill enumeration (DEC-017).
 *
 * <p>The user document changes on identity writes (Auth) and profile writes
 * (App). The injected {@link UserDirectoryQueryPort} composes both owner read
 * seams without reading Auth-owned tables through the App datasource.
 */
@Component
@RequiredArgsConstructor
public class UserSearchBackfillReadPort implements SearchBackfillReadPort {

    private final UserDirectoryQueryPort userDirectoryQueryPort;

    @Override
    public SearchIndexType type() {
        return SearchIndexType.USERS;
    }

    @Override
    public List<SearchBackfillDocument> enumerateForBackfill(int offset, int limit) {
        return userDirectoryQueryPort.enumerate(offset, limit).stream()
                .map(directoryRow -> new SearchBackfillDocument(
                        directoryRow.row().getId(),
                        SearchBackfillReadPort.toVersionMillis(directoryRow.freshAt()),
                        SearchDocumentBuilders.user(
                                directoryRow.row().getId(),
                                directoryRow.row().getUsername(),
                                directoryRow.row().getName(),
                                directoryRow.row().getAvatar())))
                .toList();
    }

}
