package com.ulticode.common.audit;

import java.util.Map;

/**
 * Deep audit-emission port for callers that do not go through the
 * {@code @Audited} proxy seam (architecture-review candidate #5).
 *
 * <p>Owns the audit-emission policy that used to live in
 * {@code com.ulticode.common.util.AuditHelper} (deprecated shim): performer
 * resolution from {@link com.ulticode.common.auth.CurrentUserProvider},
 * client IP from {@link com.ulticode.websecurity.util.ClientIpResolver}, user-agent
 * capture from the current request, and final dispatch to
 * {@link AuditSinkPort}. Callers crossing this seam get identical metadata
 * capture to {@code @Audited} methods without needing a Spring proxy.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #record} — performer is the current authenticated user.</li>
 *   <li>{@link #recordForUser} — actor is the current user; the audit entry's
 *       target user is the supplied {@code userId} (used by policy and bulk
 *       operations that act on another principal's data).</li>
 * </ul>
 *
 * <p>This is the single seam for non-proxy audit callers; all five of them
 * (forum flag/unflag, post field toggle, user bulk delete, admin contest
 * mutation dead-injection cleanup) move here.
 *
 * @author ulticode
 */
public interface AuditRecorder {

    /**
     * Record an audit event with the current authenticated user as performer.
     *
     * @param action     audit action constant (e.g. {@link AuditVocabulary})
     * @param entityType entity type constant (e.g. {@link AuditVocabulary})
     * @param entityId   identifier of the affected entity (may be null → "N/A")
     * @param oldValues  previous state (may be null)
     * @param newValues  new state (may be null)
     */
    void record(String action,
                String entityType,
                String entityId,
                Map<String, Object> oldValues,
                Map<String, Object> newValues);

    /**
     * Record an audit event where the action targets a specific user (the
     * moderator's action against a post owner, a bulk delete acting on
     * another principal's row, etc.). Performer is still the current
     * authenticated user.
     *
     * @param action     audit action constant
     * @param entityType entity type constant
     * @param entityId   identifier of the affected entity (may be null → "N/A")
     * @param userId     target user ID the action is performed on
     * @param oldValues  previous state (may be null)
     * @param newValues  new state (may be null)
     */
    void recordForUser(String action,
                       String entityType,
                       String entityId,
                       String userId,
                       Map<String, Object> oldValues,
                       Map<String, Object> newValues);
}