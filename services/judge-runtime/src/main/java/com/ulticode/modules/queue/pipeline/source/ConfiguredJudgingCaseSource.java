package com.ulticode.modules.queue.pipeline.source;

import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.JudgingCaseSource;
import com.ulticode.modules.queue.port.adapter.ProblemExampleJudgingCaseSource;
import com.ulticode.modules.queue.port.adapter.TestCaseJudgingCaseSource;
import com.ulticode.modules.submission.port.JudgeConfigPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Source-selection policy owner. Picks the canonical {@code test_cases}
 * adapter or the legacy {@code problem_examples} adapter based on
 * {@link JudgeSourceProperties#isUseTestCases()}, so the canonical-vs-legacy
 * decision concentrates here instead of branching inside the judge pipeline.
 *
 * <p>Marked {@link Primary} so {@link JudgingCaseSource} injection resolves to
 * this composite rather than either raw adapter.
 */
@Component
@org.springframework.context.annotation.Profile("!test")
@Primary
@RequiredArgsConstructor
public class ConfiguredJudgingCaseSource implements JudgingCaseSource {

    private final TestCaseJudgingCaseSource canonicalSource;
    private final ProblemExampleJudgingCaseSource legacySource;
    private final JudgeConfigPort judgeSourceProperties;

    @Override
    public List<JudgingCase> loadCases(long problemId) {
        if (judgeSourceProperties.isUseTestCases()) {
            return canonicalSource.loadCases(problemId);
        }
        return legacySource.loadCases(problemId);
    }
}
