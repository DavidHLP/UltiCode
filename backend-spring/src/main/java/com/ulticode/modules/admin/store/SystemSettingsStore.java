package com.ulticode.modules.admin.store;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Storage seam for admin system settings.
 *
 * <p>Each settings category ({@code general}, {@code email},
 * {@code rate-limits}, {@code uploads}, {@code features}) is persisted as a
 * single row in {@code system_settings} whose {@code value} column holds the
 * JSON-serialized VO. The store owns that JSON (de)serialization and the
 * 5-key vocabulary; consumers own the DDL defaults, masking rules, and
 * feature-toggle safety checks.
 *
 * <p>Two real adapters:
 * <ul>
 *   <li>{@link JsonSystemSettingsStore} &mdash; MyBatis-Plus + Jackson
 *       production adapter.</li>
 *   <li>A test double (an in-memory {@code Map}-backed stub) &mdash; injected
 *       in service-level unit tests to skip Testcontainers.</li>
 * </ul>
 *
 * <p>This seam exists because the previous {@code SystemSettingsServiceImpl}
 * embedded JSON encode/decode, the 5-key list, the batched read, the
 * per-row fallback, and 5 default factories inside one class &mdash; interface
 * nearly matched the implementation, and {@code SecurityContextHolder} leaked
 * through {@code currentActor()}. After the deepening, the service holds only
 * business rules (mask, all-defaults rejection, audit anchor); the store
 * holds the storage shape.
 */
public interface SystemSettingsStore {

    /** Category key for the site-level / maintenance-mode settings row. */
    String KEY_GENERAL = "general";
    /** Category key for the SMTP / email settings row. */
    String KEY_EMAIL = "email";
    /** Category key for the rate-limits settings row. */
    String KEY_RATE_LIMITS = "rate-limits";
    /** Category key for the upload settings row. */
    String KEY_UPLOADS = "uploads";
    /** Category key for the per-feature toggle settings row. */
    String KEY_FEATURES = "features";

    /**
     * The five category keys, in stable order. Returned exactly as stored
     * in the {@code `key`} column.
     */
    List<String> categoryKeys();

    /**
     * Load a category row and decode the JSON payload to {@code type}.
     * If the row is absent, blank, or unparseable, return
     * {@code defaultFactory.get()} (the caller owns the DDL default).
     */
    <T> T loadOrDefault(String key, Class<T> type, Supplier<T> defaultFactory);

    /**
     * Parse a JSON payload that was just batched-read by
     * {@link #loadAllRaw(java.util.Collection)}, returning the DDL default
     * if the payload is missing/blank/unparseable. Centralizes the JSON
     * decoder so callers stay free of {@code ObjectMapper} dependencies.
     */
    <T> T parseOrDefault(String json, Class<T> type, Supplier<T> defaultFactory);

    /**
     * Serialize {@code value} to JSON and upsert the row identified by
     * {@code key} (using the configured clock for {@code updated_at}).
     */
    void save(String key, Object value);

    /**
     * Batched read used by the {@code GET /admin/settings/all} path:
     * one {@code SELECT ... WHERE `key` IN (...)} instead of N round-trips.
     * Rows that are absent are simply missing from the returned map.
     */
    Map<String, String> loadAllRaw(Collection<String> keys);

    /**
     * Delete the rows for the given keys. Used by the reset path.
     */
    void deleteAll(Collection<String> keys);
}
