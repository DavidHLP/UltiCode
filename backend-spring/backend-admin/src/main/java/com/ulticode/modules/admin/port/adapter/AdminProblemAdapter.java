package com.ulticode.modules.admin.port.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.port.AdminProblemPort;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.service.ProblemService;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Production adapter for {@link AdminProblemPort}.
 *
 * <p>Hides the two cross-module dependencies that
 * {@code AdminProblemServiceImpl} used to import directly:
 * {@code ProblemService} and {@code SubmissionMapper}. All problem lifecycle
 * writes, VO conversion, slug lookup, and submission pagination flow through
 * this adapter, keeping admin's compile-time dependency graph inside its own
 * module boundary.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class AdminProblemAdapter implements AdminProblemPort {

    private final ProblemService problemService;
    private final SubmissionMapper submissionMapper;

    @Override
    public ProblemVO toVO(Problem problem) {
        return problemService.toVO(problem);
    }

    @Override
    public Optional<Problem> findBySlug(String slug) {
        return problemService.findBySlug(slug);
    }

    @Override
    public PageResult<Submission> findSubmissionsByProblemId(Long problemId, int page, int limit) {
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Submission::getProblemId, problemId)
                .orderByDesc(Submission::getCreatedAt);

        Page<Submission> submissionPage = new Page<>(page, limit);
        Page<Submission> result = submissionMapper.selectPage(submissionPage, wrapper);

        return PageResult.of(result.getRecords(), result.getTotal(), page, limit);
    }

    @Override
    public void publishProblem(Long id) {
        problemService.publishProblem(id);
    }

    @Override
    public void unpublishProblem(Long id) {
        problemService.unpublishProblem(id);
    }

    @Override
    public void deleteProblem(Long id) {
        problemService.deleteProblem(id);
    }
}
