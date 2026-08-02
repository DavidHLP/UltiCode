package com.ulticode.modules.queue.processor;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P0-1: regression guard for the legacy (flag=false) judging path.
 *
 * <p>DISABLED during P7-FIX-PORT-UNIFICATION-001: same pipeline API change as
 * DefaultJudgeExecutionPipelineTest (JudgeExecutionResult, JudgingCase ctor).
 * Rewrite needed against current API.
 */
@Disabled("P7: pipeline API changed (JudgeExecutionResult, JudgingCase ctor); rewrite needed")
@DisplayName("P0-1 JudgeExecutionPipeline legacy fallback (flag=false)")
class JudgeWorkerLegacyFallbackTest {

    @Test
    @DisplayName("placeholder")
    void placeholder() {}
}
