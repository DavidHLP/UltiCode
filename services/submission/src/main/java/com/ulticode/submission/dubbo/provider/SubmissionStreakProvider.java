package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.service.SubmissionStreakPort;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/** Exposes the Submission-owner streak calculation. */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionStreakProvider implements SubmissionStreakPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public int computeStreak(String userId) {
        Integer streak = submissionMapper.calculateStreak(userId);
        return streak == null ? 0 : Math.max(0, streak);
    }
}
