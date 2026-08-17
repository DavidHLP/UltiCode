package com.ulticode.modules.submission.reaper;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/** Recovers expired Submission-owner leases through the durable judge outbox. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.submission.owner.mode",
        havingValue = "local")
public class JudgingLeaseReaper {

    private static final int REAPER_BATCH_SIZE = 20;

    private final SubmissionMapper submissionMapper;
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final FeatureFlagsProperties featureFlags;
    private final UuidGenerator uuidGenerator;
    private final MeterRegistry meterRegistry;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;

    private volatile TransactionTemplate rowTransactionTemplate;

    @Scheduled(fixedDelayString = "${judge.reaper.interval-ms:5000}",
            initialDelayString = "${judge.reaper.initial-delay-ms:10000}")
    public int recoverExpiredLeases() {
        if (!featureFlags.isUseGenerationFence()
                || !featureFlags.isUseJudgeOutbox()
                || !featureFlags.getJudgeQueue().isUsePort()) {
            return 0;
        }

        List<Submission> expired = submissionMapper.selectExpiredJudgingForUpdate(
                REAPER_BATCH_SIZE);
        if (expired.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        List<String> failed = new ArrayList<>();
        for (Submission submission : expired) {
            try {
                if (recoverOne(submission)) {
                    recovered++;
                    incrementLeaseExpired();
                }
            } catch (Exception e) {
                failed.add(submission.getId());
                log.error("Submission lease recovery failed for {} (row kept JUDGING; "
                                + "next sweep retries): {}",
                        submission.getId(), e.getMessage());
            }
        }
        if (!failed.isEmpty()) {
            log.error("Submission lease recovery failed for {} of {} expired rows: {}",
                    failed.size(), expired.size(), failed);
        }
        return recovered;
    }

    private boolean recoverOne(Submission submission) {
        TransactionTemplate template = rowTransactionTemplate();
        if (template == null) {
            return recoverOneInTx(submission);
        }
        Boolean recovered = template.execute(status -> recoverOneInTx(submission));
        return Boolean.TRUE.equals(recovered);
    }

    private boolean recoverOneInTx(Submission submission) {
        long observedGeneration = submission.getGeneration() == null
                ? 1L : submission.getGeneration();
        long newGeneration = observedGeneration + 1;
        int rows = submissionMapper.bumpGenerationAndReset(
                submission.getId(), observedGeneration, newGeneration);
        if (rows != 1) {
            log.debug("Submission lease recovery skipped for {} (generation {} already moved)",
                    submission.getId(), observedGeneration);
            return false;
        }

        judgeOutboxMapper.insert(JudgeOutboxRecord.forResubmission(
                submission, String.valueOf(submission.getProblemId()),
                newGeneration, false, uuidGenerator));
        log.info("submission.reaper.outbox.insert submissionId={} problemId={} generation={}",
                submission.getId(), submission.getProblemId(), newGeneration);
        return true;
    }

    private TransactionTemplate rowTransactionTemplate() {
        TransactionTemplate local = rowTransactionTemplate;
        if (local == null) {
            PlatformTransactionManager transactionManager =
                    transactionManagerProvider.getIfAvailable();
            if (transactionManager == null) {
                return null;
            }
            TransactionTemplate created = new TransactionTemplate(transactionManager);
            created.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            rowTransactionTemplate = created;
            local = created;
        }
        return local;
    }

    private void incrementLeaseExpired() {
        if (meterRegistry != null) {
            meterRegistry.counter("judge.lease.expired").increment();
        }
    }
}
