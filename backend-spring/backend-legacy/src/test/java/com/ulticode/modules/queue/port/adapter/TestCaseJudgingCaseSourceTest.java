package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.dto.ProblemJudgingCaseDTO;
import com.ulticode.app.api.service.ProblemJudgingCaseReadPort;
import com.ulticode.modules.queue.port.JudgingCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression test for the per-case hidden/sample flag propagation through
 * {@link TestCaseJudgingCaseSource}.
 *
 * <p>Before the Problem relocation, {@code TestCaseJudgingCaseSource} read
 * {@code TestCase.getIsHidden()}/{@code getIsSample()} directly and passed
 * them to {@link JudgingCase}. After the relocation to the app-api
 * {@link ProblemJudgingCaseReadPort}, the flags must travel through
 * {@link ProblemJudgingCaseDTO} or they are silently lost, causing every
 * case to resolve to unset scope in {@code DefaultJudgeExecutionPipeline}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TestCaseJudgingCaseSource hidden/sample propagation")
class TestCaseJudgingCaseSourceTest {

    @Mock
    private ProblemJudgingCaseReadPort port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TestCaseJudgingCaseSource source;

    @BeforeEach
    void setUp() {
        source = new TestCaseJudgingCaseSource(port, objectMapper);
    }

    @Test
    @DisplayName("propagates isHidden=true to JudgingCase.hidden")
    void propagatesHiddenFlag() {
        when(port.loadCases(42L)).thenReturn(List.of(
                new ProblemJudgingCaseDTO("tc-1", 1, null, "out", null, true, false)
        ));

        List<JudgingCase> cases = source.loadCases(42L);

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getHidden()).isTrue();
        assertThat(cases.get(0).getSample()).isFalse();
    }

    @Test
    @DisplayName("propagates isSample=true to JudgingCase.sample")
    void propagatesSampleFlag() {
        when(port.loadCases(42L)).thenReturn(List.of(
                new ProblemJudgingCaseDTO("tc-2", 2, null, "out", null, false, true)
        ));

        List<JudgingCase> cases = source.loadCases(42L);

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getHidden()).isFalse();
        assertThat(cases.get(0).getSample()).isTrue();
    }

    @Test
    @DisplayName("passes null flags when DTO carries null (fail-safe)")
    void passesNullFlagsWhenAbsent() {
        when(port.loadCases(42L)).thenReturn(List.of(
                new ProblemJudgingCaseDTO("tc-3", 3, null, "out", null, null, null)
        ));

        List<JudgingCase> cases = source.loadCases(42L);

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getHidden()).isNull();
        assertThat(cases.get(0).getSample()).isNull();
    }

    @Test
    @DisplayName("returns empty list when port returns null")
    void returnsEmptyWhenPortReturnsNull() {
        when(port.loadCases(99L)).thenReturn(null);

        List<JudgingCase> cases = source.loadCases(99L);

        assertThat(cases).isEmpty();
    }

    @Test
    @DisplayName("maps multiple cases preserving flag identity")
    void mapsMultipleCasesWithMixedFlags() {
        when(port.loadCases(42L)).thenReturn(List.of(
                new ProblemJudgingCaseDTO("tc-a", 1, null, "out", null, true, false),
                new ProblemJudgingCaseDTO("tc-b", 2, null, "out", null, false, true),
                new ProblemJudgingCaseDTO("tc-c", 3, null, "out", null, null, null)
        ));

        List<JudgingCase> cases = source.loadCases(42L);

        assertThat(cases).hasSize(3);
        assertThat(cases.get(0).getHidden()).isTrue();
        assertThat(cases.get(0).getSample()).isFalse();
        assertThat(cases.get(1).getHidden()).isFalse();
        assertThat(cases.get(1).getSample()).isTrue();
        assertThat(cases.get(2).getHidden()).isNull();
        assertThat(cases.get(2).getSample()).isNull();
    }
}
