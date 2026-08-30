package com.ulticode.submission.admin;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Submission-owned, generation-fenced rejudge transition. */
@Service
@RequiredArgsConstructor
public class SubmissionRejudgeService {

    private final SubmissionMapper submissionMapper;
    private final SubmissionCreatedOutboxMapper createdOutboxMapper;
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    @Transactional
    public RejudgeResultDTO rejudge(String submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            return failure(submissionId, AppErrorCode.CONTENT_NOT_FOUND, "Submission not found");
        }
        if (createdOutboxMapper.findLatestBySubmissionId(submissionId) != null) {
            return failure(submissionId, AppErrorCode.CONTENT_STATE_CONFLICT,
                    "Contest submissions cannot be rejudged until replacement scoring is supported");
        }

        SubmissionStatus current;
        try {
            current = SubmissionStatus.fromDbName(submission.getStatus());
        } catch (RuntimeException exception) {
            return failure(submissionId, AppErrorCode.CONTENT_STATE_CONFLICT,
                    "Submission status cannot be rejudged");
        }
        long generation = submission.getGeneration() == null ? 1L : submission.getGeneration();

        if (current == SubmissionStatus.JUDGING) {
            if (submissionMapper.requestJudgingRejudge(submissionId, generation) != 1) {
                return failure(submissionId, AppErrorCode.VERSION_CONFLICT,
                        "Submission changed concurrently");
            }
            Submission updated = submissionMapper.selectById(submissionId);
            return success(updated, "Judging");
        }
        if (!current.isTerminal()) {
            return failure(submissionId, AppErrorCode.CONTENT_STATE_CONFLICT,
                    "Only terminal or judging submissions can be rejudged");
        }

        long newGeneration = generation + 1;
        if (submissionMapper.rejudgeTerminal(submissionId, generation, newGeneration) != 1) {
            return failure(submissionId, AppErrorCode.VERSION_CONFLICT,
                    "Submission changed concurrently");
        }
        submission.setGeneration(newGeneration);
        submission.setRetryCount((submission.getRetryCount() == null ? 0 : submission.getRetryCount()) + 1);
        submission.setStatus(SubmissionStatus.PENDING.wireValue());
        submission.setCurrentAttemptId(null);
        submission.setJudgingLeaseExpiresAt(null);
        judgeOutboxMapper.insert(JudgeOutboxRecord.forResubmission(
                submission,
                String.valueOf(submission.getProblemId()),
                newGeneration,
                false,
                uuidGenerator));
        return success(submission, SubmissionStatus.PENDING.wireValue());
    }

    private RejudgeResultDTO success(Submission submission, String status) {
        int retryCount = submission == null || submission.getRetryCount() == null
                ? 0 : submission.getRetryCount();
        return new RejudgeResultDTO(
                submission == null ? null : submission.getId(),
                status,
                clock.millis(),
                retryCount,
                true,
                null,
                null);
    }

    private static RejudgeResultDTO failure(
            String submissionId, AppErrorCode code, String message) {
        return new RejudgeResultDTO(
                submissionId, "unknown", 0L, 0, false, code.code(), message);
    }
}
