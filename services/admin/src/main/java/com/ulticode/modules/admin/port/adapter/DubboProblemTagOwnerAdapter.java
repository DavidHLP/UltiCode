package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.ProblemTagOwnerPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dubbo consumer adapter registering {@link ProblemTagOwnerPort} as a local
 * admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.ProblemTagOwnerProvider}).
 *
 * <p>Tag mutation commands are idempotent-by-row (complete row shape), so
 * the write reference uses the global write-safe consumer default
 * (no auto-retry) per {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboProblemTagOwnerAdapter implements ProblemTagOwnerPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ProblemTagOwnerPort problemTagOwnerPort;

    @Override
    public void createTag(TagWrite command) {
        problemTagOwnerPort.createTag(command);
    }

    @Override
    public void updateTag(TagWrite command) {
        problemTagOwnerPort.updateTag(command);
    }

    @Override
    public void deleteTag(String id) {
        problemTagOwnerPort.deleteTag(id);
    }

    @Override
    public void mergeTags(String sourceId, String targetTagId) {
        problemTagOwnerPort.mergeTags(sourceId, targetTagId);
    }
}
