package com.ulticode.common.audit;

import java.util.Map;

/**
 * Port the audit aspect uses to persist an audit record. Hides the
 * admin module's {@code AuditService} from the cross-cutting
 * {@code common/aspect/} package.
 *
 * <p>Prior to this port, {@code AuditAspect} imported
 * {@code com.ulticode.modules.admin.service.AuditService} directly — the
 * cross-cutting infrastructure knew about one specific module. Renaming
 * or moving the admin service would break every aspect unit test.
 *
 * <p><strong>Seam justification:</strong>
 * <ul>
 *   <li>Each owner-local adapter writes its own audit outbox; Admin consumes
 *       the resulting {@code AuditRecorded} event through a durable inbox.</li>
 *   <li>{@code InMemoryAuditSink} (in test sources) — no DB, no
 *       admin-mock noise; aspect tests assert what they emit.</li>
 * </ul>
 *
 * <p>Mirrors the proven {@code RateLimiter} seam: the aspect depends on the
 * port, and the port's storage adapter lives next to the data owner.
 *
 * @author ulticode
 */
public interface AuditSinkPort {

    /**
     * Persist one audit record. Implementations must be non-blocking
     * and side-effect-free beyond their own storage — the aspect calls
     * this on the success and failure paths of the wrapped method.
     *
     * @param performerId actor that invoked the audited method (from
     *                   the authenticated principal, never the request body)
     * @param userId     target user id, if the action is targeted (may be null)
     * @param action     action verb, e.g. {@code UPDATE_USER}
     * @param entityType entity type, e.g. {@code USER}
     * @param entityId   target entity id, or {@code "N/A"} when absent
     * @param oldValues  pre-mutation snapshot (may be null)
     * @param newValues  post-mutation snapshot (may be null)
     * @param ipAddress  client IP, or {@code "unknown"} outside HTTP
     * @param userAgent  client User-Agent, or null
     */
    void log(String performerId,
             String userId,
             String action,
             String entityType,
             String entityId,
             Map<String, Object> oldValues,
             Map<String, Object> newValues,
             String ipAddress,
             String userAgent);
}
