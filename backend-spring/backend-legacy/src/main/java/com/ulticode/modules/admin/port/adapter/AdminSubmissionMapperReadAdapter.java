package com.ulticode.modules.admin.port.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminSubmissionQueryDTO;
import com.ulticode.modules.admin.port.AdminSubmissionReadPort;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.dto.LanguageCountDTO;
import com.ulticode.modules.submission.dto.StatusCountDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Production adapter for {@link AdminSubmissionReadPort}.
 *
 * <p>Backed by {@code SubmissionMapper}. Inherits analytics queries from
 * {@link com.ulticode.modules.submission.port.SubmissionAnalyticsPort} and owns
 * the paginated submission search (resolving the search-term user/problem id
 * pre-fetch). Tests can substitute a fixture by providing another bean of the
 * port interface; the admin module never sees the mapper.
 */
@Component
@RequiredArgsConstructor
public class AdminSubmissionMapperReadAdapter implements AdminSubmissionReadPort {

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;

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
    public PageResult<Submission> searchSubmissions(AdminSubmissionQueryDTO query, int page, int pageSize) {
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();

        // Search filter — resolve at DB level by pre-fetching matching user/problem IDs
        if (StringUtils.hasText(query.getSearch())) {
            String search = query.getSearch();
            List<String> matchingUserIds = userMapper.selectList(
                    new LambdaQueryWrapper<User>().like(User::getUsername, search)
            ).stream().map(User::getId).collect(Collectors.toList());
            List<Long> matchingProblemIds = problemMapper.selectList(
                    new LambdaQueryWrapper<Problem>().like(Problem::getTitle, search)
            ).stream().map(Problem::getId).collect(Collectors.toList());

            wrapper.and(w -> {
                w.like(Submission::getId, search)
                        .or().eq(Submission::getLanguage, search);
                if (!matchingUserIds.isEmpty()) {
                    w.or().in(Submission::getUserId, matchingUserIds);
                }
                if (!matchingProblemIds.isEmpty()) {
                    w.or().in(Submission::getProblemId, matchingProblemIds);
                }
            });
        }

        if (StringUtils.hasText(query.getUserId())) {
            wrapper.eq(Submission::getUserId, query.getUserId());
        }
        if (query.getProblemId() != null) {
            wrapper.eq(Submission::getProblemId, query.getProblemId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Submission::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getLanguage())) {
            wrapper.eq(Submission::getLanguage, query.getLanguage());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(Submission::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(Submission::getCreatedAt, query.getEndDate());
        }

        boolean isAsc = !"desc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        switch (sortBy) {
            case "createdAt" -> wrapper.orderBy(true, isAsc, Submission::getCreatedAt);
            case "runtime" -> wrapper.orderBy(true, isAsc, Submission::getRuntime);
            case "memory" -> wrapper.orderBy(true, isAsc, Submission::getMemory);
            case "status" -> wrapper.orderBy(true, isAsc, Submission::getStatus);
            default -> wrapper.orderBy(true, isAsc, Submission::getCreatedAt);
        }

        Page<Submission> result = submissionMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    public long countCreatedSince(LocalDateTime from) {
        Long n = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().ge(Submission::getCreatedAt, from));
        return n == null ? 0L : n;
    }

    @Override
    public long countByStatus(String status) {
        Long n = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().eq(Submission::getStatus, status));
        return n == null ? 0L : n;
    }

    @Override
    public List<String> findDistinctLanguages() {
        return submissionMapper.findDistinctLanguages();
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
