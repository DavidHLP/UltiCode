package com.ulticode.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.common.util.PartialUpdate;
import com.ulticode.modules.admin.dto.testcase.BulkImportResponse;
import com.ulticode.modules.admin.dto.testcase.BulkImportTestCasesDTO;
import com.ulticode.modules.admin.dto.testcase.CreateTestCaseDTO;
import com.ulticode.modules.admin.dto.testcase.UpdateTestCaseDTO;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.problem.port.TestCaseOwnerPort;
import com.ulticode.modules.problem.port.TestCaseOwnerPort.TestCaseWrite;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Admin service for test case CRUD operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTestCaseService {

    private final TestCaseMapper testCaseMapper;
    private final ProblemMapper problemMapper;
    private final TestCaseOwnerPort testCaseOwnerPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    /** Fail fast with PROBLEM_NOT_FOUND when the owning problem does not exist. */
    private void requireProblem(Long problemId) {
        if (problemMapper.selectById(problemId) == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_NOT_FOUND);
        }
    }

    public PageResult<TestCase> listTestCases(Long problemId, Boolean isSample, Boolean isHidden,
                                               Integer page, Integer limit) {
        requireProblem(problemId);

        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getProblemId, problemId);
        if (isSample != null) {
            wrapper.eq(TestCase::getIsSample, isSample);
        }
        if (isHidden != null) {
            wrapper.eq(TestCase::getIsHidden, isHidden);
        }
        wrapper.orderByAsc(TestCase::getTestOrder);

        PaginationRequest pageRequest = PaginationRequest.of(page, limit);
        int currentPage = pageRequest.page();
        int currentLimit = pageRequest.pageSize();

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TestCase> pageParam =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(currentPage, currentLimit);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TestCase> result =
                testCaseMapper.selectPage(pageParam, wrapper);

        return PageResult.of(result.getRecords(), result.getTotal(), currentPage, currentLimit);
    }

    public TestCase getTestCase(Long problemId, String testCaseId) {
        requireProblem(problemId);
        TestCase testCase = testCaseMapper.selectById(testCaseId);
        if (testCase == null || !testCase.getProblemId().equals(problemId)) {
            throw new BusinessException(AdminErrorCode.TEST_CASE_NOT_FOUND);
        }
        return testCase;
    }

    private void validateInputsJson(String inputs) {
        if (inputs == null || inputs.isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(inputs);
        } catch (Exception e) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Invalid JSON in inputs field: " + e.getMessage());
        }
    }

    @Transactional
    public TestCase createTestCase(Long problemId, CreateTestCaseDTO dto) {
        requireProblem(problemId);
        return persistNewTestCase(problemId, dto);
    }

    /**
     * Build and insert a single test case. Caller is responsible for confirming
     * the owning problem exists; bulk import checks once for the whole batch
     * rather than re-querying on every item.
     */
    private TestCase persistNewTestCase(Long problemId, CreateTestCaseDTO dto) {
        validateInputsJson(dto.getInputs());
        boolean[] scope = resolveCaseScopeFlags(dto.getIsSample(), dto.getIsHidden());
        boolean isSample = scope[0];
        boolean isHidden = scope[1];

        TestCase testCase = new TestCase();
        testCase.setId(uuidGenerator.newId().replace("-", ""));
        testCase.setProblemId(problemId);
        testCase.setIsSample(isSample);
        testCase.setIsHidden(isHidden);
        testCase.setTestOrder(dto.getTestOrder() != null ? dto.getTestOrder() : 0);
        testCase.setInputText(dto.getInputText());
        testCase.setOutputText(dto.getOutputText());
        testCase.setExplanation(dto.getExplanation());
        testCase.setConstraints(dto.getConstraints());
        testCase.setInputs(dto.getInputs());
        testCase.setCreatedAt(LocalDateTime.now(clock));
        testCase.setUpdatedAt(LocalDateTime.now(clock));

        // P3-BURNDOWN-001: write routed through the problem-module owner port;
        // TestCaseMapper stays read-only inside the admin module.
        testCaseOwnerPort.insertTestCase(toWriteCommand(testCase));
        log.info("Test case created: {} for problem {}" , testCase.getId(), problemId);
        return testCase;
    }

    /**
     * Resolve the canonical {@code (is_sample, is_hidden)} pair from a create
     * payload. The backend test_cases table models author intent as a single
     * "CaseScope" dimension (SAMPLE or HIDDEN) — the judging pipeline's
     * {@code findActiveCasesForJudging} only certifies XOR pairs, so every
     * persisted row MUST satisfy XOR. {@link CreateTestCaseDTO#getIsSample()}
     * is {@code @NotNull}; when {@code isHidden} is omitted we default it to
     * {@code !isSample} (the inverse) so an admin that sends only one flag
     * still produces a valid scope instead of the draft {@code (false, false)}
     * or the illegal {@code (true, true)} combination.
     */
    private boolean[] resolveCaseScopeFlags(Boolean isSample, Boolean isHidden) {
        boolean sample = Boolean.TRUE.equals(isSample);
        boolean hidden = isHidden != null ? isHidden : !sample;
        if (sample == hidden) {
            throw new BusinessException(AdminErrorCode.TEST_CASE_INVALID_SCOPE);
        }
        return new boolean[]{sample, hidden};
    }

    /**
     * Map a fully-built {@link TestCase} row onto the owner-port write command.
     * Callers guarantee {@code is_sample} / {@code is_hidden} are non-null
     * (insert resolves the XOR pair; update merges onto a persisted row).
     */
    private TestCaseWrite toWriteCommand(TestCase testCase) {
        return new TestCaseWrite(
                testCase.getId(),
                testCase.getProblemId(),
                Boolean.TRUE.equals(testCase.getIsSample()),
                Boolean.TRUE.equals(testCase.getIsHidden()),
                testCase.getTestOrder() != null ? testCase.getTestOrder() : 0,
                testCase.getInputText(),
                testCase.getOutputText(),
                testCase.getExplanation(),
                testCase.getConstraints(),
                testCase.getInputs(),
                testCase.getCreatedAt(),
                testCase.getUpdatedAt());
    }

    @Transactional
    public TestCase updateTestCase(Long problemId, String testCaseId, UpdateTestCaseDTO dto) {
        TestCase existing = getTestCase(problemId, testCaseId);
        // Validate the supplied JSON before any partial writes touch the row.
        validateInputsJson(dto.getInputs());

        PartialUpdate.setIfPresent(dto, UpdateTestCaseDTO::getIsSample, existing::setIsSample);
        PartialUpdate.setIfPresent(dto, UpdateTestCaseDTO::getIsHidden, existing::setIsHidden);
        // The judging pipeline only certifies XOR (is_sample XOR is_hidden) rows,
        // so every partial update must still leave the row in SAMPLE or HIDDEN
        // scope. Reject any merge that lands on the disallowed (false,false)
        // "draft" or the illegal (true,true) combination before it persists.
        // The frontend always emits both flags together via the CaseScope seam,
        // but alternate admin callers (scripts, future UIs) reach this endpoint
        // too — this guard makes the invariant server-side enforced.
        if (Boolean.TRUE.equals(existing.getIsSample()) == Boolean.TRUE.equals(existing.getIsHidden())) {
            throw new BusinessException(AdminErrorCode.TEST_CASE_INVALID_SCOPE);
        }
        PartialUpdate.setIfPresent(dto, UpdateTestCaseDTO::getTestOrder, existing::setTestOrder);
        PartialUpdate.setIfPresentText(dto, UpdateTestCaseDTO::getInputText, existing::setInputText);
        PartialUpdate.setIfPresentText(dto, UpdateTestCaseDTO::getOutputText, existing::setOutputText);
        PartialUpdate.setIfPresentText(dto, UpdateTestCaseDTO::getExplanation, existing::setExplanation);
        PartialUpdate.setIfPresentText(dto, UpdateTestCaseDTO::getConstraints, existing::setConstraints);
        PartialUpdate.setIfPresentText(dto, UpdateTestCaseDTO::getInputs, existing::setInputs);
        existing.setUpdatedAt(LocalDateTime.now(clock));

        testCaseOwnerPort.updateTestCase(toWriteCommand(existing));
        log.info("Test case updated: {} for problem {}", testCaseId, problemId);
        return existing;
    }

    @Transactional
    public void deleteTestCase(Long problemId, String testCaseId) {
        TestCase existing = getTestCase(problemId, testCaseId);
        testCaseOwnerPort.deleteTestCase(existing.getId());
        log.info("Test case deleted: {} for problem {}", testCaseId, problemId);
    }

    /**
     * Bulk-import test cases. When {@code replaceExisting} is true, every existing
     * test case for the problem is deleted within this transaction before the new
     * batch is inserted, so a failed import never leaves the problem with a mix of
     * old and partial-new cases.
     */
    @Transactional
    public BulkImportResponse bulkImportTestCases(Long problemId, BulkImportTestCasesDTO dto) {
        requireProblem(problemId);

        boolean replace = Boolean.TRUE.equals(dto.getReplaceExisting());
        if (replace) {
            testCaseOwnerPort.deleteAllForProblem(problemId);
        }

        List<CreateTestCaseDTO> dtos = dto.getTestCases();
        List<TestCase> created = new ArrayList<>(dtos.size());
        for (CreateTestCaseDTO createDto : dtos) {
            created.add(persistNewTestCase(problemId, createDto));
        }
        log.info("Bulk imported {} test cases for problem {} (replace={})",
                created.size(), problemId, replace);
        return new BulkImportResponse(created.size());
    }

    @Transactional
    public void reorderTestCases(Long problemId, List<String> testCaseIds) {
        requireProblem(problemId);
        Set<String> uniqueIds = new HashSet<>(testCaseIds);
        if (uniqueIds.size() != testCaseIds.size()) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "Duplicate test case IDs");
        }
        for (int i = 0; i < testCaseIds.size(); i++) {
            TestCase existing = getTestCase(problemId, testCaseIds.get(i));
            testCaseOwnerPort.updateTestOrder(existing.getId(), i, LocalDateTime.now(clock));
        }
        log.info("Reordered {} test cases for problem {}", testCaseIds.size(), problemId);
    }

    public List<TestCase> exportTestCases(Long problemId) {
        requireProblem(problemId);
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getProblemId, problemId);
        wrapper.orderByAsc(TestCase::getTestOrder);
        return testCaseMapper.selectList(wrapper);
    }

    /**
     * Export all test cases for a problem as a JSON string.
     * Used by the controller to return an octet-stream response.
     *
     * @param problemId the problem ID
     * @return JSON string representation of the test cases list
     * @throws BusinessException PROBLEM_NOT_FOUND if the problem doesn't exist
     */
    public String exportTestCasesAsJson(Long problemId) {
        // Reuse exportTestCases() — it already pre-checks problem existence and
        // throws BusinessException(PROBLEM_NOT_FOUND) on miss, so we don't need
        // a duplicate problemMapper lookup here.
        List<TestCase> cases = exportTestCases(problemId);
        try {
            return objectMapper.writeValueAsString(cases);
        } catch (Exception e) {
            log.error("Failed to serialize test cases for problem {}", problemId, e);
            throw new BusinessException(AdminErrorCode.DATABASE_ERROR, "Failed to serialize test cases");
        }
    }
}
