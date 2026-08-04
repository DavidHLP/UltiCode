package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.dto.LanguageCountDTO;
import com.ulticode.app.api.dto.StatusCountDTO;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.app.api.service.SubmissionAnalyticsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Production adapter for {@link SubmissionAnalyticsPort}.
 *
 * <p>Backed by the typed {@code SubmissionMapper} queries (no
 * {@code Map<String, Object>} shapes leak past the seam). The adapter
 * is the only place that touches the mapper for analytics; the rest of
 * the codebase depends on the port.
 */
@Component
@RequiredArgsConstructor
public class SubmissionAnalyticsMapperAdapter implements SubmissionAnalyticsPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public List<StatusCountDTO> countByStatus() {
        return submissionMapper.countByStatusTyped();
    }

    @Override
    public List<LanguageCountDTO> countByLanguage() {
        return submissionMapper.countByLanguageTyped();
    }
}
