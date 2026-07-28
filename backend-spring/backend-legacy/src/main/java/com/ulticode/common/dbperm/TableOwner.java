package com.ulticode.common.dbperm;

/**
 * Represents the three Database User Shadow domains for UltiCode (P3-DBPERM-001).
 *
 * <ul>
 *   <li>{@link #AUTH} &mdash; owns auth_rw tables (credentials, roles, sessions)</li>
 *   <li>{@link #ADMIN} &mdash; owns admin_rw tables (governance, audit, moderation)</li>
 *   <li>{@link #APP} &mdash; owns app_rw tables (OJ, problems, submissions, forum, etc.)</li>
 * </ul>
 */
public enum TableOwner {
    AUTH,
    ADMIN,
    APP
}
