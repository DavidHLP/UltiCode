package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.ProblemAdminCasesDTO;
import com.ulticode.app.api.dto.ProblemAdminCodeDTO;
import com.ulticode.app.api.dto.ProblemAdminDescriptionDTO;
import com.ulticode.app.api.dto.ProblemAdminQueryDTO;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.dto.ProblemAdminTagDTO;
import com.ulticode.app.api.dto.ProblemAdminTestCaseDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.adapter.DefaultProblemAdminReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Collection;
import java.util.List;

/**
 * Dubbo provider for {@link ProblemAdminReadPort} exported by
 * {@code backend-app} so backend-admin reads Problem / TestCase / Tag /
 * Detail / Export data without importing the problem module.
 *
 * <p>Delegates to the concrete {@link DefaultProblemAdminReadAdapter} — never
 * to the port interface itself — so the app bean graph keeps exactly one
 * primary local implementation plus this RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ProblemAdminReadProvider implements ProblemAdminReadPort {

    private final DefaultProblemAdminReadAdapter delegate;

    @Override
    public ProblemAdminRowDTO findProblem(Long id) {
        return delegate.findProblem(id);
    }

    @Override
    public ProblemAdminRowDTO findBySlug(String slug) {
        return delegate.findBySlug(slug);
    }

    @Override
    public List<ProblemAdminRowDTO> findBySlugs(Collection<String> slugs) {
        if (slugs != null && slugs.size() > ProblemOwnerPort.MAX_IMPORT_SIZE) {
            throw new IllegalArgumentException("Too many problem slugs");
        }
        return delegate.findBySlugs(slugs);
    }

    @Override
    public List<ProblemAdminRowDTO> findProblemsByIds(Collection<Long> ids) {
        return delegate.findProblemsByIds(ids);
    }

    @Override
    public ProblemAdminDescriptionDTO findDescription(Long problemId) {
        return delegate.findDescription(problemId);
    }

    @Override
    public ProblemAdminCodeDTO findCode(Long problemId) {
        return delegate.findCode(problemId);
    }

    @Override
    public ProblemAdminCasesDTO findCases(Long problemId) {
        return delegate.findCases(problemId);
    }

    @Override
    public PageResult<ProblemAdminRowDTO> listProblems(ProblemAdminQueryDTO query) {
        return delegate.listProblems(query);
    }

    @Override
    public List<ProblemAdminRowDTO> listAllProblems(ProblemAdminQueryDTO query) {
        return delegate.listAllProblems(query);
    }

    @Override
    public PageResult<ProblemAdminRowDTO> listFlaggedProblems(String status, int page, int limit) {
        return delegate.listFlaggedProblems(status, page, limit);
    }

    @Override
    public List<Long> searchProblemIdsByTitle(String title) {
        return delegate.searchProblemIdsByTitle(title);
    }

    @Override
    public PageResult<ProblemAdminTestCaseDTO> listTestCases(
            Long problemId, Boolean isSample, Boolean isHidden, int page, int limit) {
        return delegate.listTestCases(problemId, isSample, isHidden, page, limit);
    }

    @Override
    public ProblemAdminTestCaseDTO getTestCase(Long problemId, String testCaseId) {
        return delegate.getTestCase(problemId, testCaseId);
    }

    @Override
    public List<ProblemAdminTestCaseDTO> findTestCasesByIds(
            Long problemId, Collection<String> testCaseIds) {
        return delegate.findTestCasesByIds(problemId, testCaseIds);
    }

    @Override
    public List<ProblemAdminTestCaseDTO> exportTestCases(Long problemId) {
        return delegate.exportTestCases(problemId);
    }

    @Override
    public PageResult<ProblemAdminTagDTO> listTags(
            String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        return delegate.listTags(search, pageNum, pageSize, sortBy, sortOrder);
    }

    @Override
    public ProblemAdminTagDTO getTagById(String id) {
        return delegate.getTagById(id);
    }

    @Override
    public boolean tagNameExists(String name) {
        return delegate.tagNameExists(name);
    }

    @Override
    public boolean tagSlugExists(String slug) {
        return delegate.tagSlugExists(slug);
    }
}
