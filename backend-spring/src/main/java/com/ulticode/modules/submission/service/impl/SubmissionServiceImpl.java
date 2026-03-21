package com.ulticode.modules.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of SubmissionService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;

    /**
     * Supported languages for submission.
     */
    private static final List<String> SUPPORTED_LANGUAGES = List.of(
            "javascript", "typescript", "python", "java", "cpp", "c",
            "go", "rust", "csharp", "php", "ruby", "swift", "kotlin"
    );

    @Override
    @Transactional
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        // Validate user ID
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
        }

        // Validate code is not empty
        if (!StringUtils.hasText(createDTO.getCode())) {
            throw new BusinessException(ErrorCode.SUBMISSION_CODE_EMPTY);
        }

        // Validate language is supported
        String language = createDTO.getLanguage().toLowerCase();
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);
        }

        // Verify problem exists
        Problem problem = problemMapper.selectById(createDTO.getProblemId());
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        // Verify user exists
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Create submission with Pending status
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID().toString());
        submission.setUserId(userId);
        submission.setProblemId(createDTO.getProblemId());
        submission.setLanguage(language);
        submission.setCode(createDTO.getCode());
        submission.setStatus("Pending");
        submission.setRuntime(0);
        submission.setMemory(0.0);
        submission.setCreatedAt(LocalDateTime.now());
        submission.setTestDetails(new ArrayList<>());

        // Save submission
        submissionMapper.insert(submission);

        log.info("Created submission {} for user {} and problem {}", submission.getId(), userId, createDTO.getProblemId());

        // TODO: Add to judge queue for async processing
        // For now, the submission stays in Pending status until the judge service processes it

        return toVO(submission);
    }

    @Override
    public SubmissionVO findById(String id, String userId) {
        Submission submission = submissionMapper.selectById(id);

        if (submission == null) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        // Access control: users can only see their own submissions
        if (StringUtils.hasText(userId) && !submission.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }

        return toVO(submission);
    }

    @Override
    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
        }

        int page = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        Page<Submission> pageParam = new Page<>(page, pageSize);
        IPage<Submission> result = submissionMapper.findByUserId(pageParam, userId);

        List<SubmissionVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();

        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }

    @Override
    public PageResult<SubmissionVO> findByProblemId(Long problemId, String userId, SubmissionQueryDTO query) {
        int page = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;

        Page<Submission> pageParam = new Page<>(page, pageSize);
        IPage<Submission> result = submissionMapper.findByProblemId(pageParam, problemId, userId);

        List<SubmissionVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();

        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }

    @Override
    public SubmissionVO findBest(Long problemId, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.SUBMISSION_USER_ID_REQUIRED);
        }

        Optional<Submission> bestSubmission = submissionMapper.findBestByProblemIdAndUserId(problemId, userId);

        return bestSubmission.map(this::toVO).orElse(null);
    }

    @Override
    public Optional<Submission> getSubmissionEntity(String id) {
        return Optional.ofNullable(submissionMapper.selectById(id));
    }

    @Override
    public SubmissionVO toVO(Submission submission) {
        SubmissionVO vo = new SubmissionVO();

        // Basic fields
        vo.setId(submission.getId());
        vo.setProblemId(submission.getProblemId());
        vo.setUserId(submission.getUserId());
        vo.setLanguage(submission.getLanguage());
        vo.setCode(submission.getCode());
        vo.setStatus(submission.getStatus());
        vo.setRuntime(submission.getRuntime());
        vo.setMemory(submission.getMemory());
        vo.setNotes(submission.getNotes());
        vo.setCreatedAt(submission.getCreatedAt());
        vo.setRuntimePercentile(submission.getRuntimePercentile());
        vo.setMemoryPercentile(submission.getMemoryPercentile());

        // Convert test details to test results
        if (submission.getTestDetails() != null && !submission.getTestDetails().isEmpty()) {
            List<SubmissionVO.TestResult> tests = new ArrayList<>();
            for (int i = 0; i < submission.getTestDetails().size(); i++) {
                Submission.TestCaseDetail detail = submission.getTestDetails().get(i);
                SubmissionVO.TestResult test = new SubmissionVO.TestResult();
                test.setId("test-" + submission.getId() + "-" + (i + 1));
                test.setStatus(detail.getStatus() != null ? detail.getStatus() : submission.getStatus());
                test.setRuntime(detail.getTime() != null ? detail.getTime() : submission.getRuntime());
                test.setMemory(detail.getMemory() != null ? detail.getMemory() : submission.getMemory());
                tests.add(test);
            }
            vo.setTests(tests);

            // Extract error information from first failing test
            for (Submission.TestCaseDetail detail : submission.getTestDetails()) {
                if (detail.getStatus() != null && !"Accepted".equals(detail.getStatus())) {
                    if ("Compile Error".equals(detail.getStatus())) {
                        vo.setCompilerError(detail.getDetail());
                    }
                    vo.setErrorDetail(detail.getDetail());

                    // Format input
                    if (detail.getInputs() != null && !detail.getInputs().isEmpty()) {
                        StringBuilder inputBuilder = new StringBuilder();
                        for (Submission.TestCaseDetail.InputParam input : detail.getInputs()) {
                            if (inputBuilder.length() > 0) {
                                inputBuilder.append(", ");
                            }
                            inputBuilder.append(input.getName()).append(" = ").append(input.getValue());
                        }
                        vo.setInput(inputBuilder.toString());
                    }

                    vo.setOutput(detail.getOutput());
                    vo.setExpectedOutput(detail.getExpectedOutput());
                    break;
                }
            }
        }

        // Add user info
        User user = userMapper.selectById(submission.getUserId());
        if (user != null) {
            SubmissionVO.UserInfo userInfo = new SubmissionVO.UserInfo();
            userInfo.setId(user.getId());
            userInfo.setUsername(user.getUsername());
            userInfo.setAvatar(user.getAvatar());
            vo.setUser(userInfo);
        }

        // Add problem info
        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem != null) {
            SubmissionVO.ProblemInfo problemInfo = new SubmissionVO.ProblemInfo();
            problemInfo.setId(problem.getId());
            problemInfo.setTitle(problem.getTitle());
            problemInfo.setSlug(problem.getSlug());
            vo.setProblem(problemInfo);
        }

        return vo;
    }
}
