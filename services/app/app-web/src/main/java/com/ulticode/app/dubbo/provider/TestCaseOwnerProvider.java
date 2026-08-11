package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.TestCaseOwnerPort;
import com.ulticode.modules.problem.port.DefaultTestCaseOwnerPort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Exposes the problem owner's test-case write boundary to backend-admin.
 * The transaction remains inside the App owner implementation.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class TestCaseOwnerProvider implements TestCaseOwnerPort {

    private final DefaultTestCaseOwnerPort delegate;

    @Override
    public void insertTestCase(TestCaseWrite command) {
        delegate.insertTestCase(command);
    }

    @Override
    public void updateTestCase(TestCaseWrite command) {
        delegate.updateTestCase(command);
    }

    @Override
    public void deleteTestCase(String id) {
        delegate.deleteTestCase(id);
    }

    @Override
    public int deleteAllForProblem(Long problemId) {
        return delegate.deleteAllForProblem(problemId);
    }

    @Override
    public void replaceAllForProblem(Long problemId, List<TestCaseWrite> commands) {
        delegate.replaceAllForProblem(problemId, commands);
    }

    @Override
    public void updateTestOrder(String id, int testOrder, LocalDateTime updatedAt) {
        delegate.updateTestOrder(id, testOrder, updatedAt);
    }

    @Override
    public void updateTestOrders(List<TestCaseOrder> commands) {
        delegate.updateTestOrders(commands);
    }
}
