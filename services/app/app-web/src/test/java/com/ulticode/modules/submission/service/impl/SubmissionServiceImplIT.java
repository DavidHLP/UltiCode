package com.ulticode.modules.submission.service.impl;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for SubmissionServiceImpl.
 *
 * <p><strong>DISABLED during P7-RELOCATE-SUBMISSION-001:</strong> This IT
 * requires {@code ProblemMapper}, {@code UserMapper}, and {@code QueueService}
 * from modules that have not yet been relocated to backend-app.
 * Re-enable after P7-RELOCATE-PROBLEM-001 completes the problem family
 * relocation.
 */
@Disabled("Requires ProblemMapper/UserMapper/QueueService from un-relocated legacy modules")
@DisplayName("SubmissionServiceImpl IT (disabled)")
class SubmissionServiceImplIT {

    @Test
    @DisplayName("placeholder — re-enable after problem/user family relocation")
    void placeholder() {
        // IT will be restored when all cross-module dependencies are available
    }
}
