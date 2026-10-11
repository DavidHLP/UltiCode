package com.ulticode.modules.queue.pipeline.source;

import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.adapter.ProblemExampleJudgingCaseSource;
import com.ulticode.modules.queue.port.adapter.TestCaseJudgingCaseSource;
import com.ulticode.modules.submission.port.JudgeConfigPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Configured judging case source")
class ConfiguredJudgingCaseSourceTest {
    @Mock private TestCaseJudgingCaseSource canonicalSource;
    @Mock private ProblemExampleJudgingCaseSource legacySource;
    @Mock private JudgeConfigPort configuration;
    @InjectMocks private ConfiguredJudgingCaseSource source;

    @Test
    void selectsCanonicalCasesWhenEnabled() {
        var cases = List.of(new JudgingCase("hidden", "hidden", "42", List.of(), true, false));
        when(configuration.isUseTestCases()).thenReturn(true);
        when(canonicalSource.loadCases(42L)).thenReturn(cases);

        assertThat(source.loadCases(42L)).isSameAs(cases);
        verifyNoInteractions(legacySource);
    }

    @Test
    void selectsLegacyCasesWhenDisabled() {
        var cases = List.of(new JudgingCase("example", "example", "42", List.of(), false, true));
        when(configuration.isUseTestCases()).thenReturn(false);
        when(legacySource.loadCases(42L)).thenReturn(cases);

        assertThat(source.loadCases(42L)).isSameAs(cases);
        verifyNoInteractions(canonicalSource);
    }

    @Test
    void doesNotFallBackWhenCanonicalCasesAreEmpty() {
        when(configuration.isUseTestCases()).thenReturn(true);
        when(canonicalSource.loadCases(42L)).thenReturn(List.of());

        assertThat(source.loadCases(42L)).isEmpty();
        verifyNoInteractions(legacySource);
    }

    @Test
    void propagatesCanonicalFailureWithoutFallingBack() {
        var failure = new IllegalStateException("canonical source unavailable");
        when(configuration.isUseTestCases()).thenReturn(true);
        when(canonicalSource.loadCases(42L)).thenThrow(failure);

        assertThatThrownBy(() -> source.loadCases(42L)).isSameAs(failure);
        verifyNoInteractions(legacySource);
    }
}
