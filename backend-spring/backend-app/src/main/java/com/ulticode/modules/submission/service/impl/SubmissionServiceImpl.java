package com.ulticode.modules.submission.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.PerformanceStats;
import com.ulticode.app.api.dto.SubmissionDetailVO;
import com.ulticode.app.api.dto.SubmissionListItemVO;
import com.ulticode.app.api.dto.SubmissionQueryDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.port.SubmissionWritePort;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Submission domain facade — controller-facing seam for the submission state
 * machine.
 *
 * <p><b>Deep-module boundary.</b> Every state mutation (Submission intake +
 * the two verdict writers) is owned by
 * {@link com.ulticode.modules.submission.port.SubmissionWritePort} /
 * {@code DefaultSubmissionWritePort}. This implementation surfaces two
 * surfaces to callers:
 * <ul>
 *   <li>the <em>write delegate</em> {@link #submit}, a thin facade so
 *       in-module controllers stay on the {@code Controller → Service → Port}
 *       path; cross-module write callers inject
 *       {@code SubmissionWritePort} directly, which is the legitimate
 *       cross-module consumer-seam pattern;</li>
 *   <li>the <em>boundary reads</em> the caller crosses just after the state
 *       boundary: {@link #findById}, {@link #findByUserId},
 *       {@link #findByProblemId}, and {@link #findBest}.</li>
 * </ul>
 *
 * <p>View-shape aggregation (calendar, learning progress, history) stays
 * behind {@link SubmissionProjection}, and the entity-to-VO projection used
 * by {@code findBest} delegates to {@code SubmissionProjection} so the
 * shaping rules live in one place.
 *
 * @author ulticode
 */
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final SubmissionProjection submissionProjection;
    private final SubmissionPerformanceStats performanceStats;
    private final SubmissionWritePort submissionWritePort;

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        return submissionWritePort.submit(userId, createDTO);
    }

    @Override
    public SubmissionDetailVO findById(String id, String userId) {
        Submission submission = submissionMapper.selectById(id);

        if (submission == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND);
        }

        // Access control: users can only see their own submissions
        if (StringUtils.hasText(userId) && !submission.getUserId().equals(userId)) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND);
        }

        PerformanceStats stats = PerformanceStats.EMPTY;
        if ("Accepted".equals(submission.getStatus())) {
            stats = performanceStats.compute(submission,
                    submission.getRuntime() != null ? submission.getRuntime() : 0,
                    submission.getMemory());
        }

        return submissionProjection.toDetailVO(submission, stats);
    }

    @Override
    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST);
        }

        int page = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        Page<Submission> pageParam = new Page<>(page, pageSize);
        IPage<SubmissionMapper.SubmissionWithProblem> result =
                submissionMapper.findByUserIdWithProblem(userId, pageParam);

        List<SubmissionVO> voList = result.getRecords().stream()
                .map(submissionProjection::toVO)
                .toList();

        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }

    @Override
    public PageResult<SubmissionListItemVO> findByProblemId(Long problemId, String userId, SubmissionQueryDTO query) {
        int page = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        Page<Submission> pageParam = new Page<>(page, pageSize);
        IPage<SubmissionMapper.SubmissionWithProblem> result =
                submissionMapper.findByProblemIdWithProblem(problemId, userId, pageParam);

        List<SubmissionListItemVO> voList = result.getRecords().stream()
                .map(submissionProjection::toListItemVO)
                .toList();

        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }

    @Override
    public SubmissionVO findBest(Long problemId, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST);
        }

        Optional<Submission> bestSubmission = submissionMapper.findBestByProblemIdAndUserId(problemId, userId);

        return bestSubmission.map(submissionProjection::toVO).orElse(null);
    }
}
