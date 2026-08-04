package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.service.JudgeConfigPort;
import com.ulticode.modules.submission.config.JudgeSourceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link JudgeConfigPort}, delegating to
 * {@link JudgeSourceProperties#isUseTestCases()}.
 */
@Component
@RequiredArgsConstructor
public class JudgeConfigAdapter implements JudgeConfigPort {

    private final JudgeSourceProperties judgeSourceProperties;

    @Override
    public boolean isUseTestCases() {
        return judgeSourceProperties.isUseTestCases();
    }
}
