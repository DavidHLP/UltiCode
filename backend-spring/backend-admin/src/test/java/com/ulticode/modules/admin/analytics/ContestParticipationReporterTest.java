package com.ulticode.modules.admin.analytics;

import com.ulticode.modules.admin.dto.ContestParticipationReportVO;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.modules.admin.port.ContestSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContestParticipationReporter}.
 *
 * <p>Verifies the contest-participation math against fixed inputs:
 * <ul>
 *   <li>type-bucket averages use the correct running-average formula</li>
 *   <li>top contests are sorted by participants desc and capped at 10</li>
 *   <li>trend weeks use the {@code startDate &isin; [weekStart, weekEnd)}
 *       predicate against the fixed clock</li>
 *   <li>empty input yields zeros, never NPE</li>
 *   <li>null/non-positive {@code days} falls back to 30</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContestParticipationReporterTest {

    @Mock private AdminAnalyticsPort adminAnalyticsPort;

    private ContestParticipationReporter reporter;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 6, 1, 12, 0, 0);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        reporter = new ContestParticipationReporter(adminAnalyticsPort, FIXED_CLOCK);
    }

    @Test
    @DisplayName("null/zero/negative days falls back to 30 (5 trend rows)")
    void defaultDaysWindow() {
        when(adminAnalyticsPort.loadContestData(any())).thenReturn(
                new AdminAnalyticsPort.ContestParticipationData(List.of(), new HashMap<>(), Set.of()));

        ContestParticipationReportVO report = reporter.buildReport(null);
        // No contests in the dataset → totalContests=0, but the analysis window
        // is the 30-day default, so the trend has 30/7 + 1 = 5 rows.
        assertEquals(Integer.valueOf(0), report.getTotalContests());
        assertEquals(0L, report.getTotalParticipants());
        assertEquals(5, report.getParticipationTrend().size());

        report = reporter.buildReport(0);
        assertEquals(5, report.getParticipationTrend().size());

        report = reporter.buildReport(-5);
        assertEquals(5, report.getParticipationTrend().size());
    }

    @Test
    @DisplayName("empty data: zeros across the board, never NPE")
    void emptyData() {
        when(adminAnalyticsPort.loadContestData(any())).thenReturn(
                new AdminAnalyticsPort.ContestParticipationData(List.of(), new HashMap<>(), Set.of()));

        ContestParticipationReportVO report = reporter.buildReport(30);

        assertEquals(Integer.valueOf(0), report.getTotalContests());
        assertEquals(Long.valueOf(0), report.getTotalParticipants());
        assertEquals(0.0, report.getAverageParticipantsPerContest());
        assertNotNull(report.getByType());
        assertTrue(report.getByType().isEmpty());
        assertNotNull(report.getTopContests());
        assertTrue(report.getTopContests().isEmpty());
        assertNotNull(report.getParticipationTrend());
        // 30/7 = 4 + 1 loop iteration = 5 trend rows (the (i >= 0) iteration adds the current week)
        assertEquals(5, report.getParticipationTrend().size());
    }

    @Test
    @DisplayName("type buckets compute running-average participants correctly")
    void typeBucketAverages() {
        ContestSummary icpc1 = new ContestSummary("c1", "ICPC 1", "ICPC",
                LocalDateTime.of(2026, 5, 10, 9, 0));
        ContestSummary icpc2 = new ContestSummary("c2", "ICPC 2", "ICPC",
                LocalDateTime.of(2026, 5, 17, 9, 0));
        ContestSummary ioi1 = new ContestSummary("c3", "IOI 1", "IOI",
                LocalDateTime.of(2026, 5, 12, 9, 0));

        Map<String, Long> participantsByContest = new LinkedHashMap<>();
        participantsByContest.put("c1", 10L);
        participantsByContest.put("c2", 30L);
        participantsByContest.put("c3", 5L);

        when(adminAnalyticsPort.loadContestData(any())).thenReturn(
                new AdminAnalyticsPort.ContestParticipationData(
                        List.of(icpc1, icpc2, ioi1), participantsByContest, Set.of()));

        ContestParticipationReportVO report = reporter.buildReport(30);

        Map<String, ContestParticipationReportVO.TypeStats> byType = new LinkedHashMap<>();
        for (ContestParticipationReportVO.TypeStats stats : report.getByType()) {
            byType.put(stats.getType(), stats);
        }

        ContestParticipationReportVO.TypeStats icpcStats = byType.get("ICPC");
        assertNotNull(icpcStats);
        assertEquals(Integer.valueOf(2), icpcStats.getCount());
        // running avg: (10 + 30) / 2 = 20.0
        assertEquals(20.0, icpcStats.getAvgParticipants());

        ContestParticipationReportVO.TypeStats ioiStats = byType.get("IOI");
        assertNotNull(ioiStats);
        assertEquals(Integer.valueOf(1), ioiStats.getCount());
        assertEquals(5.0, ioiStats.getAvgParticipants());
    }

    @Test
    @DisplayName("top contests are sorted desc by participants and capped at 10")
    void topContestsSortingAndLimit() {
        List<ContestSummary> contests = new java.util.ArrayList<>();
        Map<String, Long> participantsByContest = new LinkedHashMap<>();
        for (int i = 0; i < 15; i++) {
            contests.add(new ContestSummary("c" + i, "Contest " + i, "ICPC",
                    LocalDateTime.of(2026, 5, 5 + i, 9, 0)));
            participantsByContest.put("c" + i, (long) (15 - i));
        }

        when(adminAnalyticsPort.loadContestData(any())).thenReturn(
                new AdminAnalyticsPort.ContestParticipationData(
                        contests, participantsByContest, Set.of()));

        ContestParticipationReportVO report = reporter.buildReport(30);

        assertEquals(10, report.getTopContests().size());
        // The highest-participant contest is c0 (15), the 10th is c9 (6).
        assertEquals("c0", report.getTopContests().get(0).getContestId());
        assertEquals(15, report.getTopContests().get(0).getParticipants());
        assertEquals("c9", report.getTopContests().get(9).getContestId());
        assertEquals(6, report.getTopContests().get(9).getParticipants());
        // All carry the 100.0 completion-rate placeholder.
        assertEquals(100.0, report.getTopContests().get(0).getCompletionRate());
    }

    @Test
    @DisplayName("trend buckets contests by [weekStart, weekEnd)")
    void trendBucketing() {
        // FIXED_NOW = 2026-06-01 12:00. With days=28, the loop runs i=4,3,2,1,0 → 5 rows.
        // i=4: weekStart = 2026-05-04, weekEnd = 2026-05-11
        // i=3: weekStart = 2026-05-11, weekEnd = 2026-05-18
        // i=2: weekStart = 2026-05-18, weekEnd = 2026-05-25
        // i=1: weekStart = 2026-05-25, weekEnd = 2026-06-01
        // i=0: weekStart = 2026-06-01, weekEnd = 2026-06-08
        ContestSummary c1 = new ContestSummary("c1", "old", "ICPC",
                LocalDateTime.of(2026, 5, 7, 9, 0)); // i=4 bucket
        ContestSummary c2 = new ContestSummary("c2", "mid", "ICPC",
                LocalDateTime.of(2026, 5, 20, 9, 0)); // i=2 bucket
        ContestSummary c3 = new ContestSummary("c3", "new", "ICPC",
                LocalDateTime.of(2026, 5, 28, 9, 0)); // i=1 bucket

        Map<String, Long> participants = new LinkedHashMap<>();
        participants.put("c1", 7L);
        participants.put("c2", 11L);
        participants.put("c3", 3L);

        when(adminAnalyticsPort.loadContestData(any())).thenReturn(
                new AdminAnalyticsPort.ContestParticipationData(
                        List.of(c1, c2, c3), participants, Set.of()));

        ContestParticipationReportVO report = reporter.buildReport(28);

        assertEquals(5, report.getParticipationTrend().size());
        // Oldest row first per the loop order; verify each bucket has the expected counts.
        assertEquals("2026-05-04", report.getParticipationTrend().get(0).getDate());
        assertEquals(Integer.valueOf(1), report.getParticipationTrend().get(0).getContests());
        assertEquals(Integer.valueOf(7), report.getParticipationTrend().get(0).getParticipants());

        assertEquals("2026-05-11", report.getParticipationTrend().get(1).getDate());
        assertEquals(Integer.valueOf(0), report.getParticipationTrend().get(1).getContests());
        assertEquals(Integer.valueOf(0), report.getParticipationTrend().get(1).getParticipants());

        assertEquals("2026-05-18", report.getParticipationTrend().get(2).getDate());
        assertEquals(Integer.valueOf(1), report.getParticipationTrend().get(2).getContests());
        assertEquals(Integer.valueOf(11), report.getParticipationTrend().get(2).getParticipants());

        assertEquals("2026-05-25", report.getParticipationTrend().get(3).getDate());
        assertEquals(Integer.valueOf(1), report.getParticipationTrend().get(3).getContests());
        assertEquals(Integer.valueOf(3), report.getParticipationTrend().get(3).getParticipants());

        assertEquals("2026-06-01", report.getParticipationTrend().get(4).getDate());
        assertEquals(Integer.valueOf(0), report.getParticipationTrend().get(4).getContests());
    }

    @Test
    @DisplayName("virtual participation row stays at (0, 0.0)")
    void virtualParticipationZeroed() {
        when(adminAnalyticsPort.loadContestData(any())).thenReturn(
                new AdminAnalyticsPort.ContestParticipationData(List.of(), new HashMap<>(), Set.of()));

        ContestParticipationReportVO report = reporter.buildReport(30);

        assertNotNull(report.getVirtualParticipation());
        assertEquals(Integer.valueOf(0), report.getVirtualParticipation().getTotal());
        assertEquals(0.0, report.getVirtualParticipation().getAverageCompletionRate());
    }

    @Test
    @DisplayName("average participants per contest uses unique-participant count, not total slots")
    void averageParticipantsPerContest() {
        ContestSummary c1 = new ContestSummary("c1", "A", "ICPC",
                LocalDateTime.of(2026, 5, 5, 9, 0));
        ContestSummary c2 = new ContestSummary("c2", "B", "ICPC",
                LocalDateTime.of(2026, 5, 6, 9, 0));

        // 5 unique users, but per-contest slot totals are 10 and 12.
        when(adminAnalyticsPort.loadContestData(any())).thenReturn(
                new AdminAnalyticsPort.ContestParticipationData(
                        List.of(c1, c2),
                        Map.of("c1", 10L, "c2", 12L),
                        Set.of("u1", "u2", "u3", "u4", "u5")));

        ContestParticipationReportVO report = reporter.buildReport(30);

        assertEquals(Long.valueOf(5), report.getTotalParticipants());
        // 5 unique / 2 contests = 2.5 (NOT 22/2 = 11.0 from slot totals)
        assertEquals(2.5, report.getAverageParticipantsPerContest());
    }
}