package com.ulticode.modules.submission.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.PerformanceStats;
import com.ulticode.modules.submission.dto.SubmissionDetailVO;
import com.ulticode.modules.submission.dto.SubmissionListItemVO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionStatusMeta;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.port.SubmissionWritePort;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Submission service — state-machine + boundary-read facade.
 *
 * <p><b>Deep-module boundary.</b> The write surface (Submission intake + the
 * two verdict writers) is now owned by {@link SubmissionWritePort} /
 * {@code DefaultSubmissionWritePort}; {@link #submit},
 * {@link #updateSubmissionResult} and {@link #updateSubmissionResultFenced}
 * are one-line delegates to that port. The intake's @Transactional boundary,
 * the ADR-003 fenced CAS, the F4 stats fold, the judge-outbox transactional
 * insert, contest recording, achievement triggers and the submission-result
 * notification all live behind the port.
 *
 * <p>What stays on this interface (per the {@link SubmissionService} seam
 * contract): the <em>boundary reads</em> — {@link #findById},
 * {@link #findByUserId}, {@link #findByProblemId}, {@link #findBest},
 * {@link #getSubmissionEntity} — and the static status catalog
 * {@link #getStatuses}. These are the state-machine interface's read boundary
 * (a caller that just crossed the state boundary wants a directly usable
 * payload), not view-shape aggregation, which lives behind
 * {@link SubmissionProjection}. Returning {@link SubmissionVO} from
 * {@code submit} and {@code findBest} therefore stays on this interface; the
 * entity-to-VO projection itself delegates to {@code SubmissionProjection}.
 *
 * <p>The facade is preserved (not deleted) because cross-module callers still
 * inject {@link SubmissionService}: {@code ContestServiceImpl#submit} and the
 * {@code JudgeWorkerProcessor} verdict writes. Migrating those callers to
 * {@link SubmissionWritePort} + a read port is a separate flag-day; until
 * then they see zero behavioural change through this delegate.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final SubmissionProjection submissionProjection;
    private final SubmissionPerformanceStats performanceStats;
    /**
     * Deep module owning the Submission intake + verdict writers. Every write
     * method on this facade delegates to it.
     */
    private final SubmissionWritePort submissionWritePort;

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        return submissionWritePort.submit(userId, createDTO);
    }

    @Override
    public SubmissionDetailVO findById(String id, String userId) {
        Submission submission = submissionMapper.selectById(id);

        if (submission == null) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        // Access control: users can only see their own submissions
        if (StringUtils.hasText(userId) && !submission.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
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
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
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
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
        }

        Optional<Submission> bestSubmission = submissionMapper.findBestByProblemIdAndUserId(problemId, userId);

        return bestSubmission.map(submissionProjection::toVO).orElse(null);
    }

    @Override
    public Optional<Submission> getSubmissionEntity(String id) {
        return Optional.ofNullable(submissionMapper.selectById(id));
    }

    @Override
    public List<SubmissionStatusMeta> getStatuses() {
        return submissionProjection.getStatusCatalog();
    }

    @Override
    public void updateSubmissionResult(String submissionId, String status, int runtime,
                                        Double memory, List<Submission.TestCaseDetail> testDetails) {
        submissionWritePort.updateSubmissionResult(submissionId, status, runtime, memory, testDetails);
    }

    @Override
    public boolean updateSubmissionResultFenced(String submissionId, long generation, String attemptId,
                                                String status, int runtime, Double memory,
                                                List<Submission.TestCaseDetail> testDetails) {
        return submissionWritePort.updateSubmissionResultFenced(
                submissionId, generation, attemptId, status, runtime, memory, testDetails);
    }
}
