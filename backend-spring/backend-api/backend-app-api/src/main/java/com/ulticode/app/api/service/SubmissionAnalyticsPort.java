package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.LanguageCountDTO;
import com.ulticode.app.api.dto.StatusCountDTO;

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
