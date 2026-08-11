package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.ProblemAdminCasesDTO;
import com.ulticode.app.api.dto.ProblemAdminCodeDTO;
import com.ulticode.app.api.dto.ProblemAdminDescriptionDTO;
import com.ulticode.app.api.dto.ProblemAdminQueryDTO;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.dto.ProblemAdminTagDTO;
import com.ulticode.app.api.dto.ProblemAdminTestCaseDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Dubbo consumer adapter registering {@link ProblemAdminReadPort} as a local
 * admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.ProblemAdminReadProvider}).
 *
 * <p>Admin services keep depending on the entity-free port contract; this
 * adapter is the only local bean of that type. Read references use the
 * query RPC policy (800 ms, one retry) per {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboProblemAdminReadAdapter implements ProblemAdminReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemAdminReadPort problemAdminReadPort;

    @Override
    public ProblemAdminRowDTO findProblem(Long id) {
        return problemAdminReadPort.findProblem(id);
    }

    @Override
    public ProblemAdminRowDTO findBySlug(String slug) {
        return problemAdminReadPort.findBySlug(slug);
    }

    @Override
    public List<ProblemAdminRowDTO> findBySlugs(Collection<String> slugs) {
        return problemAdminReadPort.findBySlugs(slugs);
    }

    @Override
    public List<ProblemAdminRowDTO> findProblemsByIds(Collection<Long> ids) {
        return problemAdminReadPort.findProblemsByIds(ids);
    }

    @Override
    public ProblemAdminDescriptionDTO findDescription(Long problemId) {
        return problemAdminReadPort.findDescription(problemId);
    }

    @Override
    public ProblemAdminCodeDTO findCode(Long problemId) {
        return problemAdminReadPort.findCode(problemId);
    }

    @Override
    public ProblemAdminCasesDTO findCases(Long problemId) {
        return problemAdminReadPort.findCases(problemId);
    }

    @Override
    public PageResult<ProblemAdminRowDTO> listProblems(ProblemAdminQueryDTO query) {
        return problemAdminReadPort.listProblems(query);
    }

    @Override
    public List<ProblemAdminRowDTO> listAllProblems(ProblemAdminQueryDTO query) {
        return problemAdminReadPort.listAllProblems(query);
    }

    @Override
    public PageResult<ProblemAdminRowDTO> listFlaggedProblems(String status, int page, int limit) {
        return problemAdminReadPort.listFlaggedProblems(status, page, limit);
    }

    @Override
    public List<Long> searchProblemIdsByTitle(String title) {
        return problemAdminReadPort.searchProblemIdsByTitle(title);
    }

    @Override
    public PageResult<ProblemAdminTestCaseDTO> listTestCases(
            Long problemId, Boolean isSample, Boolean isHidden, int page, int limit) {
        return problemAdminReadPort.listTestCases(problemId, isSample, isHidden, page, limit);
    }

    @Override
    public ProblemAdminTestCaseDTO getTestCase(Long problemId, String testCaseId) {
        return problemAdminReadPort.getTestCase(problemId, testCaseId);
    }

    @Override
    public List<ProblemAdminTestCaseDTO> findTestCasesByIds(
            Long problemId, Collection<String> testCaseIds) {
        return problemAdminReadPort.findTestCasesByIds(problemId, testCaseIds);
    }

    @Override
    public List<ProblemAdminTestCaseDTO> exportTestCases(Long problemId) {
        return problemAdminReadPort.exportTestCases(problemId);
    }

    @Override
    public PageResult<ProblemAdminTagDTO> listTags(
            String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        return problemAdminReadPort.listTags(search, pageNum, pageSize, sortBy, sortOrder);
    }

    @Override
    public ProblemAdminTagDTO getTagById(String id) {
        return problemAdminReadPort.getTagById(id);
    }

    @Override
    public boolean tagNameExists(String name) {
        return problemAdminReadPort.tagNameExists(name);
    }

    @Override
    public boolean tagSlugExists(String slug) {
        return problemAdminReadPort.tagSlugExists(slug);
    }
}
