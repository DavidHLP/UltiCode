package com.ulticode.modules.admin.port.adapter;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.admin.port.AdminSubmissionReadPort;
import com.ulticode.modules.submission.dto.LanguageCountDTO;
import com.ulticode.modules.submission.dto.StatusCountDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Production adapter for {@link AdminSubmissionReadPort}.
 *
 * <p>Backed by {@code SubmissionMapper}. Inherits analytics queries from
 * {@link com.ulticode.modules.submission.port.SubmissionAnalyticsPort}.
 * Tests can substitute a fixture by providing another bean of the port
 * interface; the admin module never sees the mapper.
 */
@Component
@RequiredArgsConstructor
public class AdminSubmissionMapperReadAdapter implements AdminSubmissionReadPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public Submission findById(String id) {
        return submissionMapper.selectById(id);
    }

    @Override
    public long countAll() {
        Long n = submissionMapper.selectCount(new QueryWrapper<>());
        return n == null ? 0L : n;
    }

    @Override
    public List<StatusCountDTO> countByStatus() {
        return submissionMapper.countByStatusTyped();
    }

    @Override
    public List<LanguageCountDTO> countByLanguage() {
        return submissionMapper.countByLanguageTyped();
    }
}
