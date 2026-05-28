package com.ulticode.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.testcase.CreateTestCaseDTO;
import com.ulticode.modules.admin.dto.testcase.UpdateTestCaseDTO;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin service for test case CRUD operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTestCaseService {

    private final TestCaseMapper testCaseMapper;
    private final ProblemMapper problemMapper;
    private final ObjectMapper objectMapper;

    public PageResult<TestCase> listTestCases(Long problemId, Boolean isSample, Boolean isHidden,
                                               Integer page, Integer limit) {
        if (problemMapper.selectById(problemId) == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getProblemId, problemId);
        if (isSample != null) {
            wrapper.eq(TestCase::getIsSample, isSample);
        }
        if (isHidden != null) {
            wrapper.eq(TestCase::getIsHidden, isHidden);
        }
        wrapper.orderByAsc(TestCase::getTestOrder);

        int currentPage = (page != null && page > 0) ? page : 1;
        int currentLimit = (limit != null && limit > 0) ? limit : 20;
        currentLimit = Math.min(currentLimit, 100);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TestCase> pageParam =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(currentPage, currentLimit);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TestCase> result =
                testCaseMapper.selectPage(pageParam, wrapper);

        return PageResult.of(result.getRecords(), result.getTotal(), currentPage, currentLimit);
    }

    public TestCase getTestCase(Long problemId, String testCaseId) {
        if (problemMapper.selectById(problemId) == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        TestCase testCase = testCaseMapper.selectById(testCaseId);
        if (testCase == null || !testCase.getProblemId().equals(problemId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Test case not found");
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
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid JSON in inputs field: " + e.getMessage());
        }
    }

    @Transactional
    public TestCase createTestCase(Long problemId, CreateTestCaseDTO dto) {
        if (problemMapper.selectById(problemId) == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        validateInputsJson(dto.getInputs());

        TestCase testCase = new TestCase();
        testCase.setId(UUID.randomUUID().toString().replace("-", ""));
        testCase.setProblemId(problemId);
        testCase.setIsSample(dto.getIsSample() != null ? dto.getIsSample() : false);
        testCase.setIsHidden(dto.getIsHidden() != null ? dto.getIsHidden() : false);
        testCase.setTestOrder(dto.getTestOrder() != null ? dto.getTestOrder() : 0);
        testCase.setInputText(dto.getInputText());
        testCase.setOutputText(dto.getOutputText());
        testCase.setExplanation(dto.getExplanation());
        testCase.setConstraints(dto.getConstraints());
        testCase.setInputs(dto.getInputs());
        testCase.setCreatedAt(LocalDateTime.now());
        testCase.setUpdatedAt(LocalDateTime.now());

        testCaseMapper.insert(testCase);
        log.info("Test case created: {} for problem {}" , testCase.getId(), problemId);
        return testCase;
    }

    @Transactional
    public TestCase updateTestCase(Long problemId, String testCaseId, UpdateTestCaseDTO dto) {
        TestCase existing = getTestCase(problemId, testCaseId);
        validateInputsJson(dto.getInputs());

        if (dto.getIsSample() != null) {
            existing.setIsSample(dto.getIsSample());
        }
        if (dto.getIsHidden() != null) {
            existing.setIsHidden(dto.getIsHidden());
        }
        if (dto.getTestOrder() != null) {
            existing.setTestOrder(dto.getTestOrder());
        }
        if (dto.getInputText() != null) {
            existing.setInputText(dto.getInputText());
        }
        if (dto.getOutputText() != null) {
            existing.setOutputText(dto.getOutputText());
        }
        if (dto.getExplanation() != null) {
            existing.setExplanation(dto.getExplanation());
        }
        if (dto.getConstraints() != null) {
            existing.setConstraints(dto.getConstraints());
        }
        if (dto.getInputs() != null) {
            existing.setInputs(dto.getInputs());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        testCaseMapper.updateById(existing);
        log.info("Test case updated: {} for problem {}", testCaseId, problemId);
        return existing;
    }

    @Transactional
    public void deleteTestCase(Long problemId, String testCaseId) {
        TestCase existing = getTestCase(problemId, testCaseId);
        testCaseMapper.deleteById(existing.getId());
        log.info("Test case deleted: {} for problem {}", testCaseId, problemId);
    }

    @Transactional
    public List<TestCase> bulkImportTestCases(Long problemId, List<CreateTestCaseDTO> dtos) {
        if (problemMapper.selectById(problemId) == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        List<TestCase> created = new ArrayList<>();
        for (CreateTestCaseDTO dto : dtos) {
            created.add(createTestCase(problemId, dto));
        }
        log.info("Bulk imported {} test cases for problem {}", created.size(), problemId);
        return created;
    }

    @Transactional
    public void reorderTestCases(Long problemId, List<String> testCaseIds) {
        if (problemMapper.selectById(problemId) == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        for (int i = 0; i < testCaseIds.size(); i++) {
            TestCase existing = getTestCase(problemId, testCaseIds.get(i));
            existing.setTestOrder(i);
            existing.setUpdatedAt(LocalDateTime.now());
            testCaseMapper.updateById(existing);
        }
        log.info("Reordered {} test cases for problem {}", testCaseIds.size(), problemId);
    }

    public List<TestCase> exportTestCases(Long problemId) {
        if (problemMapper.selectById(problemId) == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getProblemId, problemId);
        wrapper.orderByAsc(TestCase::getTestOrder);
        return testCaseMapper.selectList(wrapper);
    }
}
