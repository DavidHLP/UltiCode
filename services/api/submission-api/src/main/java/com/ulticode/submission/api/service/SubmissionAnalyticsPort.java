package com.ulticode.submission.api.service;

import com.ulticode.submission.api.dto.LanguageCountDTO;
import com.ulticode.submission.api.dto.StatusCountDTO;

import java.util.List;

/**
 * Typed read port for submission analytics.
 */
public interface SubmissionAnalyticsPort {

    /** Count submissions grouped by status. */
    List<StatusCountDTO> countByStatus();

    /** Count submissions grouped by language. */
    List<LanguageCountDTO> countByLanguage();
}
