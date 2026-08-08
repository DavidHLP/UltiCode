package com.ulticode.modules.submission.result;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for SubmissionResultDispatcher.
 *
 * <p><strong>DISABLED during P7-RELOCATE-SUBMISSION-001:</strong>
 * {@code IntegrationEventPublisher} (from {@code com.ulticode.modules.event.outbox})
 * was a legacy-only dependency. The dispatcher's event publishing is stubbed
 * during relocation. Re-enable when the notification/event family relocates
 * and a port replaces IntegrationEventPublisher.
 */
@Disabled("IntegrationEventPublisher not available; dispatcher event publishing stubbed")
@DisplayName("SubmissionResultDispatcher IT (disabled)")
class SubmissionResultDispatcherIT {

    @Test
    @DisplayName("placeholder — re-enable after notification family relocation")
    void placeholder() {
        // IT will be restored when IntegrationEventPublisher port is available
    }
}
