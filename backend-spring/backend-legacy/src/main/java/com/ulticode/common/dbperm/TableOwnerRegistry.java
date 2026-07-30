package com.ulticode.common.dbperm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry mapping database table names to their designated TableOwner (P3-DBPERM-001).
 * Aligned with .auto-flow/TABLE_OWNERS.md.
 */
public final class TableOwnerRegistry {

    private TableOwnerRegistry() {}

    private static final Map<String, TableOwner> TABLE_OWNER_MAP = new HashMap<>();

    static {
        // Auth tables
        Set<String> authTables = Set.of(
            "users",
            "refresh_tokens",
            "password_resets",
            "role_permissions",
            "user_permissions",
            "oauth_provider_identities"
        );
        for (String table : authTables) {
            TABLE_OWNER_MAP.put(table, TableOwner.AUTH);
        }

        // Admin tables
        // NOTE: audit_outbox is intentionally NOT attributed to a single owner. Every domain
        // appends audit rows into it inside its own business transaction (P3-AUDIT-001) while
        // the admin dispatcher consumes them, so it is a cross-domain integration seam granted
        // to all shadow users; attributing it to ADMIN would flag every legitimate audited
        // business write as a false-positive violation.
        Set<String> adminTables = Set.of(
            "audit_logs",
            "system_settings",
            "moderation_queue",
            "moderation_actions",
            "user_warnings"
        );
        for (String table : adminTables) {
            TABLE_OWNER_MAP.put(table, TableOwner.ADMIN);
        }

        // App tables
        Set<String> appTables = Set.of(
            "problems",
            "problem_details",
            "problem_examples",
            "problem_languages",
            "problem_notes",
            "problem_lists",
            "problem_list_items",
            "test_cases",
            "contests",
            "contest_problems",
            "contest_participants",
            "contest_announcements",
            "submissions",
            "submission_test_details",
            "judge_outbox",
            "solutions",
            "solution_comments",
            "solution_topics",
            "forum_posts",
            "forum_comments",
            "forum_communities",
            "notifications",
            "notification_delivery_ledger",
            "achievements",
            "bookmarks",
            "votes",
            "backups"
        );
        for (String table : appTables) {
            TABLE_OWNER_MAP.put(table, TableOwner.APP);
        }
    }

    public static TableOwner getOwner(String tableName) {
        if (tableName == null) {
            return null;
        }
        return TABLE_OWNER_MAP.get(tableName.toLowerCase().replace("`", "").trim());
    }
}
