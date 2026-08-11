package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.TestCaseOwnerPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-side consumer for the App-owned test-case write boundary.
 */
@Primary
@Component
public class DubboTestCaseOwnerAdapter implements TestCaseOwnerPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private TestCaseOwnerPort delegate;

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
