package com.ulticode.modules.admin.port;

import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.port.SubmissionAnalyticsPort;

/**
 * Typed read port the admin module uses to query the submission module.
 *
 * <p>The admin module used to reach into {@code SubmissionMapper},
 * {@code JudgeOutboxMapper}, {@code RealtimeService} and the user
 * mapper directly — 49 cross-module imports. With this port (and the
 * companion ports for audit, dashboard, etc.) the admin module depends
 * on narrow typed interfaces, not on operational mappers.
 *
 * <p>This is the first phase of the AdminReadModel seam. Future phases
 * add admin reads for user, contest, and forum; each lands as a
 * dedicated port with production + in-memory adapters.
 */
public interface AdminSubmissionReadPort extends SubmissionAnalyticsPort {

    /**
     * Find a submission by id for the admin detail page. Returns null
     * if the id is not found; the caller decides the error mapping.
     */
    Submission findById(String id);

    /**
     * Count all submissions across the system.
     */
    long countAll();
}
