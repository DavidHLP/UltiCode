package com.ulticode.submission.compat;

import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionWritePort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.port.DefaultSubmissionWritePort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;

/**
 * Transitional Submission owner provider.
 *
 * <p>SPLIT-002 introduced this as the only new network seam: it delegates to
 * the current App writer so there is exactly one storage writer during
 * expand/backfill. SPLIT-003 slice-4 adds the {@code local} mode
 * ({@code app.submission.owner.mode=local}): the provider then delegates to
 * the in-process {@link DefaultSubmissionWritePort} writing the Submission
 * schema, which is the runtime cutover. The default remains {@code compat}
 * (forward to App); switch both modes only after the SPLIT-003 cutover runbook
 * (scripts/dev/submission-schema-cutover.sh) and the SPLIT-004 read-path
 * migration. It intentionally imports only app-api DTOs and ports, never an
 * App entity or mapper.
 */
@DubboService(group = "backend-submission", version = "1.0.0")
@Profile("!test")
public class SubmissionWriteCompatibilityProvider implements SubmissionWritePort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 10000, retries = 0, check = false)
    private SubmissionWritePort appWriter;

    private final ObjectProvider<DefaultSubmissionWritePort> localWriter;

    @Value("${app.submission.owner.mode:compat}")
    private String ownerMode;

    /** Default constructor keeps the compat-only contract test path working. */
    public SubmissionWriteCompatibilityProvider() {
        this.localWriter = null;
    }

    @Autowired
    public SubmissionWriteCompatibilityProvider(
            ObjectProvider<DefaultSubmissionWritePort> localWriter) {
        this.localWriter = localWriter;
    }

    private SubmissionWritePort delegate() {
        if ("local".equals(ownerMode)) {
            if (localWriter == null) {
                throw new IllegalStateException(
                        "app.submission.owner.mode=local but no local writer provider wired");
            }
            DefaultSubmissionWritePort local = localWriter.getIfAvailable();
            if (local == null) {
                throw new IllegalStateException(
                        "app.submission.owner.mode=local but no local DefaultSubmissionWritePort bean");
            }
            return local;
        }
        return appWriter;
    }

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        return delegate().submit(userId, createDTO);
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO) {
        return delegate().submitContest(userId, createDTO);
    }

    @Override
    public void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                       int runtime, Double memory, String testDetailsJson) {
        delegate().updateSubmissionResult(submissionId, status, runtime, memory, testDetailsJson);
    }

    @Override
    public boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                                int runtime, Double memory, String testDetailsJson,
                                                long generation, String attemptId) {
        return delegate().updateSubmissionResultFenced(
                submissionId, status, runtime, memory, testDetailsJson, generation, attemptId);
    }
}
