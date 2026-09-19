package com.ulticode.modules.submission.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Logs the judge feature-flag values at startup so operators can confirm
 * whether the M3a outbox shadow write and the M3b generation fence / lease path
 * are active for this deployment.
 *
 * <p>Normal development modes use the Streams + generation-fence path. The
 * legacy RQueue path is an explicit {@code legacy-rollback} mode only.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class FeatureFlagsStartupLogger implements ApplicationRunner {

    private final FeatureFlagsProperties featureFlags;

    @Override
    public void run(ApplicationArguments args) {
        log.info("judge feature flags: useJudgeOutbox={}, useGenerationFence={}",
                featureFlags.isUseJudgeOutbox(),
                featureFlags.isUseGenerationFence());
    }
}
