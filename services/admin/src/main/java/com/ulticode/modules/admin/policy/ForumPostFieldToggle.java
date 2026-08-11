package com.ulticode.modules.admin.policy;

import com.ulticode.common.exception.BusinessException;

/**
 * Write policy that flips a single boolean field on a forum post.
 *
 * <p>Encapsulates the load &rarr; snapshot &rarr; audit &rarr; apply &rarr;
 * persist &rarr; log pattern shared by the four single-field post toggles
 * (pin / unpin / lock / unlock) that lived as byte-identical methods inside
 * {@link com.ulticode.modules.admin.service.impl.AdminForumServiceImpl}.
 * Multi-field writes (flag / unflag) intentionally stay in
 * {@link ForumFlagPolicy} because they carry an extra {@code reason}
 * parameter and a {@link java.time.Clock} dependency that don't compose
 * with the single-field shape &mdash; keeping them separate prevents the
 * policy from regressing into an internal if/else dispatch.
 *
 * <p>ADMIN-007: the enum is fully typed (field name + target value + audit
 * action + log verb) and carries no reference to the {@code ForumPost}
 * entity, which is no longer on the admin classpath. The default
 * implementation routes each toggle through the App-owned
 * {@code ForumOwnerPort} by field name.
 *
 * @author ulticode
 */
public interface ForumPostFieldToggle {

    /**
     * Toggle a single boolean field on the post identified by {@code postId}.
     *
     * @param postId       target post id
     * @param fieldToggle  which field to flip and to which value
     * @throws com.ulticode.common.exception.BusinessException
     *                     with {@code NOT_FOUND} when the post does not exist
     */
    void toggle(String postId, FieldToggle fieldToggle);

    /**
     * Single-field toggles accepted by {@link ForumPostFieldToggle#toggle}.
     * Each constant binds the audit action, the field name, the new value,
     * and the log verb so the policy can stay table-driven and free of
     * branches.
     */
    enum FieldToggle {

        /**
         * Pin the post (set {@code is_pinned=true}).
         */
        PIN(AuditAction.PIN_POST, "isPinned", true, "pinned"),

        /**
         * Unpin the post (set {@code is_pinned=false}).
         */
        UNPIN(AuditAction.UNPIN_POST, "isPinned", false, "unpinned"),

        /**
         * Lock the post (set {@code is_locked=true}).
         */
        LOCK(AuditAction.LOCK_POST, "isLocked", true, "locked"),

        /**
         * Unlock the post (set {@code is_locked=false}).
         */
        UNLOCK(AuditAction.UNLOCK_POST, "isLocked", false, "unlocked");

        /**
         * Audit action constants for single-field post toggles. Mirrors the
         * values already declared in
         * {@link com.ulticode.common.audit.AuditVocabulary} so this enum can
         * stay decoupled from the vocabulary module &mdash; the policy should
         * not depend on a vocabulary class that lives three packages away.
         */
        public static final class AuditAction {
            public static final String PIN_POST = "PIN_POST";
            public static final String UNPIN_POST = "UNPIN_POST";
            public static final String LOCK_POST = "LOCK_POST";
            public static final String UNLOCK_POST = "UNLOCK_POST";

            private AuditAction() {
            }
        }

        private final String auditAction;
        private final String fieldName;
        private final boolean newValue;
        private final String logVerb;

        FieldToggle(String auditAction, String fieldName, boolean newValue, String logVerb) {
            this.auditAction = auditAction;
            this.fieldName = fieldName;
            this.newValue = newValue;
            this.logVerb = logVerb;
        }

        /**
         * @return audit action constant for this toggle.
         */
        public String auditAction() {
            return auditAction;
        }

        /**
         * @return field name used inside audit old/new value maps and to
         *         route the toggle on the App owner port.
         */
        public String fieldName() {
            return fieldName;
        }

        /**
         * @return the new boolean value this toggle writes to the field.
         */
        public boolean newValue() {
            return newValue;
        }

        /**
         * @return past-tense verb used in the log line.
         */
        public String logVerb() {
            return logVerb;
        }
    }
}
