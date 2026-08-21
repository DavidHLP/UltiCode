package com.ulticode.modules.admin.port;

import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-owned deep seam for Dashboard aggregation.
 *
 * <p>Callers know only the Admin read shape and failure semantics. The
 * implementation hides the bounded App/Submission owner fan-out.</p>
 */
public interface AdminDashboardReadPort {

    /** Load all non-Auth dashboard aggregates at one observation time. */
    DashboardData loadStats(LocalDateTime now);

    /** Load a chart's owner-side date buckets. */
    List<ChartPoint> loadChartData(
            String metric, LocalDateTime start, LocalDateTime end, String period);

    record DashboardData(
            DashboardUserData users,
            DashboardAppStatsDTO app,
            SubmissionDashboardStatsDTO submission) {

        public DashboardData(DashboardAppStatsDTO app, SubmissionDashboardStatsDTO submission) {
            this(new DashboardUserData(0, 0, 0, 0, 0, 0, java.util.Map.of()), app, submission);
        }
    }

    /** Auth-owned dashboard facts already normalized to the Dashboard slice. */
    record DashboardUserData(
            long total,
            long active,
            long banned,
            long activeToday,
            long activeWeek,
            long activeMonth,
            java.util.Map<String, Long> byRole) {
    }

    record ChartPoint(String date, long count) {
    }
}
