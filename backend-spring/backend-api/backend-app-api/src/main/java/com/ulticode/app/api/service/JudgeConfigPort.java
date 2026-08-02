package com.ulticode.app.api.service;

/**
 * Port through which the queue module reads judge source configuration
 * without importing the submission module's config classes.
 *
 * <p>P7-RELOCATE-SUBMISSION-001: extracted when JudgeSourceProperties
 * relocated to backend-app.
 */
public interface JudgeConfigPort {

    /**
     * @return true if test cases should be used as the judging source
     */
    boolean isUseTestCases();
}
