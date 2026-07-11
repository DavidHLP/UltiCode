package com.ulticode.modules.admin.policy;

import com.ulticode.modules.forum.entity.ForumPost;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Write policy that flips a single boolean field on a forum post.
 *
 * <p>Encapsulates the load → snapshot → audit → apply → persist → log pattern
 * shared by the four single-field post toggles (pin / unpin / lock / unlock)
 * that lived as byte-identical methods inside
 * {@link com.ulticode.modules.admin.service.impl.AdminForumServiceImpl}.
 * Multi-field writes (flag / unflag) intentionally stay in
 * {@link ForumFlagPolicy} because they carry an extra {@code reason} parameter
 * and a {@link java.time.Clock} dependency that don't compose with the
 * single-field shape — keeping them separate prevents the policy from
 * regressing into an internal if/else dispatch.
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
     * Each constant binds the audit action, the field name, the new value, and
     * the log verb so the policy can stay table-driven and free of branches.
     */
    enum FieldToggle {

        /**
         * Pin the post (set {@code is_pinned=true}).
         */
        PIN(AuditAction.PIN_POST, "isPinned", ForumPost::getIsPinned, ForumPost::setIsPinned, true, "pinned"),

        /**
         * Unpin the post (set {@code is_pinned=false}).
         */
        UNPIN(AuditAction.UNPIN_POST, "isPinned", ForumPost::getIsPinned, ForumPost::setIsPinned, false, "unpinned"),

        /**
         * Lock the post (set {@code is_locked=true}).
         */
        LOCK(AuditAction.LOCK_POST, "isLocked", ForumPost::getIsLocked, ForumPost::setIsLocked, true, "locked"),

        /**
         * Unlock the post (set {@code is_locked=false}).
         */
        UNLOCK(AuditAction.UNLOCK_POST, "isLocked", ForumPost::getIsLocked, ForumPost::setIsLocked, false, "unlocked");

        /**
         * Audit action constants for single-field post toggles. Mirrors the
         * values already declared in
         * {@link com.ulticode.common.audit.AuditVocabulary} so this enum can
         * stay decoupled from the vocabulary module — the policy should not
         * depend on a vocabulary class that lives three packages away.
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
        private final Function<ForumPost, Boolean> reader;
        private final BiConsumer<ForumPost, Boolean> writer;
        private final boolean newValue;
        private final String logVerb;

        FieldToggle(String auditAction, String fieldName,
                    Function<ForumPost, Boolean> reader,
                    BiConsumer<ForumPost, Boolean> writer,
                    boolean newValue, String logVerb) {
            this.auditAction = auditAction;
            this.fieldName = fieldName;
            this.reader = reader;
            this.writer = writer;
            this.newValue = newValue;
            this.logVerb = logVerb;
        }

        /**
         * Read the current value of this field from the post, defaulting to
         * {@code false} when the column is {@code null} (mirrors the prior
         * null-safe getter pattern used by the inline toggle methods).
         *
         * @param post forum post entity
         * @return current boolean state of the field
         */
        public boolean readCurrent(ForumPost post) {
            return Boolean.TRUE.equals(reader.apply(post));
        }

        /**
         * Apply the new value to the post entity in-place.
         *
         * @param post forum post entity to mutate
         */
        public void applyTo(ForumPost post) {
            writer.accept(post, newValue);
        }

        /**
         * @return audit action constant for this toggle.
         */
        public String auditAction() {
            return auditAction;
        }

        /**
         * @return field name used inside audit old/new value maps.
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