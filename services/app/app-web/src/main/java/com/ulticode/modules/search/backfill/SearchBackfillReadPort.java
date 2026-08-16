package com.ulticode.modules.search.backfill;

import com.ulticode.modules.search.dto.SearchIndexType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * SEARCH-003 backfill enumeration seam.
 *
 * <p>Each source owner implements this port over its own entity mapper so
 * the SQL (read predicates, version columns, stable paging) stays in the
 * owning domain module while the search module consumes only the seam —
 * the same direction as the Q-read ports (P7-RELOCATE-*). Documents must
 * be built through {@code SearchDocumentBuilders} so backfill snapshots
 * match live-published documents exactly (DEC-017).
 */
public interface SearchBackfillReadPort {

    /** The index this port enumerates. */
    SearchIndexType type();

    /**
     * Return one stable page of index-safe documents, ordered by the
     * natural key ascending. Pages are stable only if no row is written
     * between calls for the same logical snapshot; callers page until an
     * empty page is returned.
     *
     * @param offset zero-based row offset
     * @param limit  page size (&gt; 0)
     */
    List<SearchBackfillDocument> enumerateForBackfill(int offset, int limit);

    /**
     * Convert a row timestamp to the version domain (epoch millis in the
     * JVM zone, same as the live publisher's {@code aggregateVersion}).
     * {@code null} timestamps map to 0 (never stale-skipped, first-write
     * semantics).
     */
    static long toVersionMillis(LocalDateTime timestamp) {
        if (timestamp == null) {
            return 0L;
        }
        return timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
