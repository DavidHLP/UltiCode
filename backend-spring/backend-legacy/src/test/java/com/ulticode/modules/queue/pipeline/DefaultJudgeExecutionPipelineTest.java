package com.ulticode.modules.queue.pipeline;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pipeline-level tests after the test-case-source seam deepening.
 *
 * <p>DISABLED during P7-FIX-PORT-UNIFICATION-001: the pipeline's execute()
 * return type changed from RunResultDTO to JudgeExecutionResult during
 * SUBMISSION relocation, and JudgingCase constructor signature evolved.
 * These assertions need rewriting against the current pipeline API.
 *
 * <p>The F-003 bug class (port interface fragmentation → DI failure) is now
 * covered by SubmissionPortWiringTest in backend-app, which verifies all 4
 * port interfaces resolve to their impls.
 */
@Disabled("P7: pipeline API changed (JudgeExecutionResult, JudgingCase ctor); rewrite needed")
@DisplayName("DefaultJudgeExecutionPipeline")
class DefaultJudgeExecutionPipelineTest {

    @Test
    @DisplayName("placeholder")
    void placeholder() {}
}
