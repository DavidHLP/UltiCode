package com.ulticode.modules.search.backfill;

import java.util.Map;

/**
 * One enumerable search-index document for backfill (DEC-017).
 *
 * @param documentId   document id (must equal the live publisher's aggregate id)
 * @param versionMillis row last-change epoch millis (worker ledger version)
 * @param document     full index-safe document, byte-identical shape to the
 *                     live publisher's UPSERT document (via SearchDocumentBuilders)
 */
public record SearchBackfillDocument(String documentId, long versionMillis, Map<String, Object> document) {
}
