package com.ulticode.submission.port.adapter;

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
import java.util.Collection;
import java.util.List;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dubbo adapter for {@link ProblemAdminReadPort} — problem admin facts are
 * owned by {@code backend-app} and read through its existing provider.
 *
 * <p>SPLIT-004 slice-5: the Submission owner's admin read seam needs the
 * problem-title search pre-fetch (legacy admin semantics) without reading
 * problem tables, per DEC-011 (no cross-service SQL). Only
 * {@link #searchProblemIdsByTitle} is used by the Submission admin read
 * provider; the remaining port methods are never called on this path and
 * throw {@link UnsupportedOperationException} to fail loudly instead of
 * silently returning null.
 */
@Component
@Primary
public class ProblemAdminReadDubboAdapter implements ProblemAdminReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemAdminReadPort appProblemAdminRead;

    @Override
    public List<Long> searchProblemIdsByTitle(String title) {
        return appProblemAdminRead.searchProblemIdsByTitle(title);
    }

    @Override
    public ProblemAdminRowDTO findProblem(Long id) {
        throw unsupported();
    }

    @Override
    public ProblemAdminRowDTO findBySlug(String slug) {
        throw unsupported();
    }

    @Override
    public List<ProblemAdminRowDTO> findBySlugs(Collection<String> slugs) {
        throw unsupported();
    }

    @Override
    public List<ProblemAdminRowDTO> findProblemsByIds(Collection<Long> ids) {
        throw unsupported();
    }

    @Override
    public ProblemAdminDescriptionDTO findDescription(Long problemId) {
        throw unsupported();
    }

    @Override
    public ProblemAdminCodeDTO findCode(Long problemId) {
        throw unsupported();
    }

    @Override
    public ProblemAdminCasesDTO findCases(Long problemId) {
        throw unsupported();
    }

    @Override
    public PageResult<ProblemAdminRowDTO> listProblems(ProblemAdminQueryDTO query) {
        throw unsupported();
    }

    @Override
    public List<ProblemAdminRowDTO> listAllProblems(ProblemAdminQueryDTO query) {
        throw unsupported();
    }

    @Override
    public PageResult<ProblemAdminRowDTO> listFlaggedProblems(String status, int page, int limit) {
        throw unsupported();
    }

    @Override
    public PageResult<ProblemAdminTestCaseDTO> listTestCases(
            Long problemId, Boolean isSample, Boolean isHidden, int page, int limit) {
        throw unsupported();
    }

    @Override
    public ProblemAdminTestCaseDTO getTestCase(Long problemId, String testCaseId) {
        throw unsupported();
    }

    @Override
    public List<ProblemAdminTestCaseDTO> findTestCasesByIds(
            Long problemId, Collection<String> testCaseIds) {
        throw unsupported();
    }

    @Override
    public List<ProblemAdminTestCaseDTO> exportTestCases(Long problemId) {
        throw unsupported();
    }

    @Override
    public PageResult<ProblemAdminTagDTO> listTags(
            String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        throw unsupported();
    }

    @Override
    public ProblemAdminTagDTO getTagById(String id) {
        throw unsupported();
    }

    @Override
    public boolean tagNameExists(String name) {
        throw unsupported();
    }

    @Override
    public boolean tagSlugExists(String slug) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
                "not used by backend-submission admin read");
    }
}
