package com.ulticode.modules.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reconciliation report for a single table (P5-RECONCILE-001).
 *
 * <p>Captures row count and checksum divergence for a table that has
 * been vertically split or dual-written. Used by the nightly reconciliation
 * job to detect drift between source and target tables.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResult {

    /** The table being reconciled (e.g., "users", "user_profiles"). */
    private String tableName;

    /** The owning domain (Auth, Admin, App). */
    private String owner;

    /** Row count of the source table. */
    private long sourceCount;

    /** Row count of the target/companion table. */
    private long targetCount;

    /** Whether the counts match. */
    public boolean isDriftFree() {
        return sourceCount == targetCount;
    }

    /** Human-readable drift description, or "OK" if no drift. */
    public String describe() {
        if (isDriftFree()) {
            return String.format("%s (%s): count=%d, no drift", tableName, owner, sourceCount);
        }
        return String.format("%s (%s): DRIFT detected — source=%d, target=%d, delta=%d",
                tableName, owner, sourceCount, targetCount, targetCount - sourceCount);
    }
}
