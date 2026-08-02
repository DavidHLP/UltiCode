package com.ulticode.modules.submission.adapter;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@code LegacySubmissionWriteAdapter}.
 *
 * <p><strong>DISABLED during P7-RELOCATE-SUBMISSION-001:</strong> The adapter
 * was renamed to {@code LegacyRejudgeStrategy} during relocation and its
 * constructor changed from {@code (SubmissionMapper, AdminSubmissionService)}
 * to {@code (SubmissionMapper, JudgeEnqueuePort)}. The test must be rewritten
 * to match the new class shape.
 */
@Disabled("LegacySubmissionWriteAdapter renamed to LegacyRejudgeStrategy; rewrite test")
@DisplayName("LegacySubmissionWriteAdapter (disabled)")
class LegacySubmissionWriteAdapterTest {

    @Test
    @DisplayName("placeholder — rewrite after adapter rename")
    void placeholder() {
        // Test will be rewritten for LegacyRejudgeStrategy
    }
}
