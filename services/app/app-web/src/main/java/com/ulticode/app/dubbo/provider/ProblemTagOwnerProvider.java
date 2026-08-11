package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.ProblemTagOwnerPort;
import com.ulticode.modules.problem.port.DefaultProblemTagOwnerPort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo provider for {@link ProblemTagOwnerPort} exported by
 * {@code backend-app} so backend-admin mutates problem tags without
 * importing the problem module.
 *
 * <p>Delegates to the concrete {@link DefaultProblemTagOwnerPort} — never
 * to the port interface itself — so the app bean graph keeps exactly one
 * primary local implementation plus this RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ProblemTagOwnerProvider implements ProblemTagOwnerPort {

    private final DefaultProblemTagOwnerPort delegate;

    @Override
    public void createTag(TagWrite command) {
        delegate.createTag(command);
    }

    @Override
    public void updateTag(TagWrite command) {
        delegate.updateTag(command);
    }

    @Override
    public void deleteTag(String id) {
        delegate.deleteTag(id);
    }

    @Override
    public void mergeTags(String sourceId, String targetTagId) {
        delegate.mergeTags(sourceId, targetTagId);
    }
}
