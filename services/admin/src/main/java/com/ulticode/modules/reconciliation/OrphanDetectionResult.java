package com.ulticode.modules.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Orphan detection result for a cross-owner reference (P5-RECONCILE-001).
 *
 * <p>Identifies dangling references where a child table's foreign key
 * points to a parent table owned by a different domain, and the referenced
 * row does not exist.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrphanDetectionResult {

    /** The child table containing the dangling reference. */
    private String childTable;

    /** The child table's owner domain. */
    private String childOwner;

    /** The column in the child table that holds the reference. */
    private String referenceColumn;

    /** The parent table being referenced. */
    private String parentTable;

    /** The parent table's owner domain. */
    private String parentOwner;

    /** Number of orphaned rows (child rows with no matching parent). */
    private long orphanCount;

    /** Whether no orphans were found. */
    public boolean isOrphanFree() {
        return orphanCount == 0;
    }

    /** Human-readable description. */
    public String describe() {
        if (isOrphanFree()) {
            return String.format("%s.%s → %s: no orphans", childTable, referenceColumn, parentTable);
        }
        return String.format("%s.%s → %s: %d orphaned rows (child owner=%s, parent owner=%s)",
                childTable, referenceColumn, parentTable, orphanCount, childOwner, parentOwner);
    }
}
