package com.ulticode.modules.problem.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * P3-BURNDOWN-001: default {@link TestCaseOwnerPort} implementation.
 * Lives in the problem module (the OWNER) and is the only admin-facing
 * class allowed to call {@link TestCaseMapper} write methods.
 *
 * <p>{@code @Transactional} on each write method so the port owns its
 * transaction boundary; the bulk-import replace flow joins the admin
 * caller's transaction via propagation, preserving the original
 * all-or-nothing semantics.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultTestCaseOwnerPort implements com.ulticode.app.api.service.TestCaseOwnerPort {

    private final TestCaseMapper testCaseMapper;

    @Override
    @Transactional
    public void insertTestCase(TestCaseWrite command) {
        testCaseMapper.insert(toEntity(command));
        log.info("TestCaseOwnerPort.insertTestCase id={} problemId={}", command.id(), command.problemId());
    }

    @Override
    @Transactional
    public void updateTestCase(TestCaseWrite command) {
        testCaseMapper.updateById(toEntity(command));
        log.info("TestCaseOwnerPort.updateTestCase id={} problemId={}", command.id(), command.problemId());
    }

    @Override
    @Transactional
    public void deleteTestCase(String id) {
        testCaseMapper.deleteById(id);
        log.info("TestCaseOwnerPort.deleteTestCase id={}", id);
    }

    @Override
    @Transactional
    public int deleteAllForProblem(Long problemId) {
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getProblemId, problemId);
        int deleted = testCaseMapper.delete(wrapper);
        log.info("TestCaseOwnerPort.deleteAllForProblem problemId={} deleted={}", problemId, deleted);
        return deleted;
    }

    @Override
    @Transactional
    public void replaceAllForProblem(Long problemId, List<TestCaseWrite> commands) {
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getProblemId, problemId);
        testCaseMapper.delete(wrapper);
        if (commands != null) {
            for (TestCaseWrite command : commands) {
                if (command == null || !Objects.equals(problemId, command.problemId())) {
                    throw new IllegalArgumentException("test case problemId does not match replacement owner");
                }
                testCaseMapper.insert(toEntity(command));
            }
        }
        log.info("TestCaseOwnerPort.replaceAllForProblem problemId={} count={}",
                problemId, commands == null ? 0 : commands.size());
    }

    @Override
    @Transactional
    public void updateTestOrder(String id, int testOrder, LocalDateTime updatedAt) {
        // Shell update: only test_order + updated_at move, matching the
        // legacy reorder loop where the loaded row changed nothing else.
        TestCase shell = new TestCase();
        shell.setId(id);
        shell.setTestOrder(testOrder);
        shell.setUpdatedAt(updatedAt);
        testCaseMapper.updateById(shell);
        log.debug("TestCaseOwnerPort.updateTestOrder id={} testOrder={}", id, testOrder);
    }

    @Override
    @Transactional
    public void updateTestOrders(List<TestCaseOrder> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (TestCaseOrder command : commands) {
            if (command == null || command.id() == null || command.id().isBlank()) {
                throw new IllegalArgumentException("test case order command id is required");
            }
        }
        for (TestCaseOrder command : commands) {
            updateTestOrder(command.id(), command.testOrder(), command.updatedAt());
        }
        log.debug("TestCaseOwnerPort.updateTestOrders count={}", commands.size());
    }

    private static TestCase toEntity(TestCaseWrite command) {
        TestCase testCase = new TestCase();
        testCase.setId(command.id());
        testCase.setProblemId(command.problemId());
        testCase.setIsSample(command.isSample());
        testCase.setIsHidden(command.isHidden());
        testCase.setTestOrder(command.testOrder());
        testCase.setInputText(command.inputText());
        testCase.setOutputText(command.outputText());
        testCase.setExplanation(command.explanation());
        testCase.setConstraints(command.constraints());
        testCase.setInputs(command.inputs());
        testCase.setCreatedAt(command.createdAt());
        testCase.setUpdatedAt(command.updatedAt());
        return testCase;
    }
}
