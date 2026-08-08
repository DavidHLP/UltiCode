package com.ulticode.modules.submission.port;

import com.ulticode.app.api.dto.LanguageCountDTO;
import com.ulticode.app.api.dto.StatusCountDTO;

import java.util.List;

/**
 * Typed read port for submission analytics. Replaces the
 * {@code Map<String, Object>} leakage at the submission persistence seam
 * and stops the admin module from reaching into the submission mapper
 * directly.
 *
 * <p>The submission module owns this port. The admin module depends on it
 * (not on {@code SubmissionMapper}). Production adapter is the typed
 * mapper; tests can supply an in-memory list.
 */
public interface SubmissionAnalyticsPort {

    /**
     * Count submissions grouped by status. Used by the admin dashboard
     * and the submission module's own statistics.
     */
    List<StatusCountDTO> countByStatus();

    /**
     * Count submissions grouped by language. Used by the admin dashboard.
     */
    List<LanguageCountDTO> countByLanguage();
}
