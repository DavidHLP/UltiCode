package com.ulticode.modules.problem.projection;

import com.ulticode.app.api.dto.ProblemCompletionReportDTO;
import com.ulticode.app.api.service.ProblemAnalyticsReadPort;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.submission.api.dto.ProblemDifficultyCompletion;
import com.ulticode.submission.api.service.ProblemSubmissionStatsPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProblemAnalyticsProjectionTest {

    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private ProblemTagMapper problemTagMapper;
    @Mock
    private ProblemTagRelationMapper problemTagRelationMapper;
    @Mock
    private ProblemSubmissionStatsPort problemSubmissionStats;

    @Test
    void mapsOwnerDifficultyFactsToTheExistingUppercaseReportContract() {
        when(problemSubmissionStats.countCreatedSince(any())).thenReturn(10L);
        when(problemSubmissionStats.countAcceptedSince(any())).thenReturn(4L);
        when(problemSubmissionStats.countProblemCompletionByDifficulty(any())).thenReturn(List.of(
                new ProblemDifficultyCompletion("EASY", 10L, 4L)));
        when(problemTagMapper.selectList(any())).thenReturn(List.of());
        when(problemMapper.selectList(any())).thenReturn(List.of());

        ProblemAnalyticsReadPort projection = new DefaultProblemAnalyticsProjection(
                problemMapper, problemTagMapper, problemTagRelationMapper,
                problemSubmissionStats,
                Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC));

        ProblemCompletionReportDTO report = projection.loadProblemCompletionReport(30);

        assertThat(report.byDifficulty())
                .extracting(ProblemCompletionReportDTO.DifficultyStats::difficulty)
                .containsExactly("EASY", "MEDIUM", "HARD");
        assertThat(report.byDifficulty().get(0).completed()).isEqualTo(4);
    }
}
