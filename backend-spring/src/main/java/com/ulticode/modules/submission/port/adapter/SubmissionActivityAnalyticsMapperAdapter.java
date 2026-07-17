package com.ulticode.modules.submission.port.adapter;

import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.port.SubmissionActivityAnalyticsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Production adapter for {@link SubmissionActivityAnalyticsPort}, backed by
 * {@code SubmissionMapper}. Confines the active-user aggregation queries to
 * the submission module; the user analytics projection depends on the port.
 */
@Component
@RequiredArgsConstructor
public class SubmissionActivityAnalyticsMapperAdapter implements SubmissionActivityAnalyticsPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public List<Map<String, Object>> countDailyActiveUsers(LocalDateTime startDate, LocalDateTime endDate) {
        return submissionMapper.countDailyActiveUsers(startDate, endDate);
    }

    @Override
    public List<Map<String, Object>> countWeeklyActiveUsers(LocalDateTime startDate) {
        return submissionMapper.countWeeklyActiveUsers(startDate);
    }

    @Override
    public List<Map<String, Object>> countActiveUsersByHour(LocalDateTime startDate) {
        return submissionMapper.countActiveUsersByHour(startDate);
    }

    @Override
    public List<Map<String, Object>> findTopActiveUsers(LocalDateTime startDate, int limit) {
        return submissionMapper.findTopActiveUsers(startDate, limit);
    }

    @Override
    public long countDistinctUsersInRange(LocalDateTime startDate, LocalDateTime endDate) {
        return submissionMapper.countDistinctUsersInRange(startDate, endDate);
    }
}
