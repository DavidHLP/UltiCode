package com.ulticode.modules.queue.pipeline.source;

import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.adapter.ProblemExampleJudgingCaseSource;
import com.ulticode.modules.queue.port.adapter.TestCaseJudgingCaseSource;
import com.ulticode.modules.submission.config.JudgeSourceProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the source-selection policy concentrates in {@link ConfiguredJudgingCaseSource}:
 * the {@code useTestCases} flag routes to exactly one adapter (single-source
 * guarantee — no silent fallback to the other).
 */
class ConfiguredJudgingCaseSourceTest {

    @Test
    @DisplayName("useTestCases=true delegates to canonical source only")
    void useTestCasesTrue_delegatesToCanonical() {
        TestCaseJudgingCaseSource canonical = mock(TestCaseJudgingCaseSource.class);
        ProblemExampleJudgingCaseSource legacy = mock(ProblemExampleJudgingCaseSource.class);
        JudgeSourceProperties props = new JudgeSourceProperties();
        props.setUseTestCases(true);
        JudgingCase c = new JudgingCase("1", "Case 1", "out", List.of(), false, true);
        when(canonical.loadCases(5L)).thenReturn(List.of(c));

        ConfiguredJudgingCaseSource src = new ConfiguredJudgingCaseSource(canonical, legacy, props);

        assertThat(src.loadCases(5L)).containsExactly(c);
        verify(legacy, never()).loadCases(anyLong());
    }

    @Test
    @DisplayName("useTestCases=false delegates to legacy source only")
    void useTestCasesFalse_delegatesToLegacy() {
        TestCaseJudgingCaseSource canonical = mock(TestCaseJudgingCaseSource.class);
        ProblemExampleJudgingCaseSource legacy = mock(ProblemExampleJudgingCaseSource.class);
        JudgeSourceProperties props = new JudgeSourceProperties();
        props.setUseTestCases(false);
        JudgingCase c = new JudgingCase("1", "Case 1", "out", List.of(), null, null);
        when(legacy.loadCases(5L)).thenReturn(List.of(c));

        ConfiguredJudgingCaseSource src = new ConfiguredJudgingCaseSource(canonical, legacy, props);

        assertThat(src.loadCases(5L)).containsExactly(c);
        verify(canonical, never()).loadCases(anyLong());
    }
}
