package com.ulticode.submission.api.service;

import com.ulticode.submission.api.dto.LanguageCountDTO;
import com.ulticode.submission.api.dto.StatusCountDTO;

import java.util.List;

/**
 * Retired compatibility type for the former standalone analytics seam.
 *
 * @deprecated use {@link SubmissionAdminReadPort}; no runtime provider is
 * registered for this duplicate contract and it will be removed in the next
 * incompatible contract release.
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public interface SubmissionAnalyticsPort {

    /** Count submissions grouped by status. */
    List<StatusCountDTO> countByStatus();

    /** Count submissions grouped by language. */
    List<LanguageCountDTO> countByLanguage();
}
