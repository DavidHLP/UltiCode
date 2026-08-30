package com.ulticode.submission.api.service;

import com.ulticode.submission.api.dto.SubmissionAdjudicationFact;

import java.util.Collection;
import java.util.List;

/** Bounded Submission-owner facts for contest adjudication drain checks. */
public interface SubmissionAdjudicationReadPort {

    /** Read current status and generation for the requested submission ids. */
    List<SubmissionAdjudicationFact> findByIds(Collection<String> submissionIds);
}
