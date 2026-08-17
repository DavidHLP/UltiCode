package com.ulticode.submission.compat;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.port.DefaultSubmissionWritePort;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionWritePort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;

/**
 * Transitional Submission owner provider.
 *
 * <p>The default {@code compat} mode forwards to the App writer while the App
 * route remains local. {@code local} delegates to the Submission-schema
 * writer after the authorized runtime cutover and grant transition.
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
