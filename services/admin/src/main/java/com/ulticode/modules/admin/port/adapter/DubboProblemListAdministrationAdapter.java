package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.command.CreateProblemListCommand;
import com.ulticode.app.api.command.DeleteProblemListCommand;
import com.ulticode.app.api.command.ReplaceListProblemsCommand;
import com.ulticode.app.api.command.UpdateBannerCommand;
import com.ulticode.app.api.command.UpdateBasicInfoCommand;
import com.ulticode.app.api.command.UpdateProblemListCommand;
import com.ulticode.app.api.command.UpdateVisibilityCommand;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.service.ProblemListAdministrationService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dubbo consumer adapter registering {@link ProblemListAdministrationService}
 * as a local admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.ProblemListAdministrationProvider}).
 *
 * <p>Admin services keep depending on the entity-free write contract; this
 * adapter is the only local bean of that type. Write references use the
 * write RPC policy (3 s timeout, zero auto-retry) per {@link RpcPolicy} so
 * a retried write cannot double-apply a side effect.
 */
@Primary
@Component
public class DubboProblemListAdministrationAdapter implements ProblemListAdministrationService {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ProblemListAdministrationService problemListAdministrationService;

    @Override
    public RpcResult<ProblemListSummaryDTO> createProblemList(CreateProblemListCommand command) {
        return problemListAdministrationService.createProblemList(command);
    }

    @Override
    public RpcResult<ProblemListSummaryDTO> updateProblemList(UpdateProblemListCommand command) {
        return problemListAdministrationService.updateProblemList(command);
    }

    @Override
    public RpcResult<Void> deleteProblemList(DeleteProblemListCommand command) {
        return problemListAdministrationService.deleteProblemList(command);
    }

    @Override
    public RpcResult<ProblemListSummaryDTO> updateBasicInfo(UpdateBasicInfoCommand command) {
        return problemListAdministrationService.updateBasicInfo(command);
    }

    @Override
    public RpcResult<ProblemListSummaryDTO> updateVisibility(UpdateVisibilityCommand command) {
        return problemListAdministrationService.updateVisibility(command);
    }

    @Override
    public RpcResult<ProblemListSummaryDTO> updateBanner(UpdateBannerCommand command) {
        return problemListAdministrationService.updateBanner(command);
    }

    @Override
    public RpcResult<Void> replaceListProblems(ReplaceListProblemsCommand command) {
        return problemListAdministrationService.replaceListProblems(command);
    }
}
