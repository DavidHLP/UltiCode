package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.admin.error.AdminReadContract;
import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default adapter for {@link AdminContestProjection}.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7: all contest entity/mapper imports
 * replaced with {@link ContestAdminReadPort}. {@code toAdminVO} now accepts
 * {@link ContestAdminDTO} instead of Contest entity.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminContestProjection implements AdminContestProjection {

    private final ContestAdminReadPort contestAdminReadPort;
    private final UuidGenerator uuidGenerator;

    @Override
    public PageResult<AdminContestVO> getContests(AdminContestQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);
        int page = pageRequest.page();
        int limit = pageRequest.pageSize();

        PageResult<ContestAdminDTO> result;
        try {
            result = contestAdminReadPort.selectPage(
                    page, limit, query.getSearch(), query.getStatus(), query.getType(),
                    query.getSortBy(), query.getSortOrder());
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("App contest", exception);
        }
        requirePage(result, "App contest");

        List<AdminContestVO> vos = result.getItems().stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());

        return PageResult.of(vos, result.getTotal(), pageRequest, DegradationStatus.OK);
    }

    @Override
    public AdminContestVO getContest(String id) {
        ContestAdminDTO contest;
        try {
            contest = contestAdminReadPort.selectByIdOrSlug(id);
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("App contest", exception);
        }
        if (contest == null) {
            throw new BusinessException(AdminErrorCode.CONTEST_NOT_FOUND);
        }
        return toAdminVO(contest);
    }

    @Override
    public AdminContestVO toAdminVO(ContestAdminDTO contest) {
        if (contest == null) {
            return null;
        }

        long problemCount;
        try {
            problemCount = contestAdminReadPort.countProblemsByContestId(contest.getId());
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("App problem", exception);
        }
        if (problemCount < 0 || problemCount > Integer.MAX_VALUE) {
            throw AdminReadContract.ownerUnavailable("App problem");
        }

        AdminContestVO vo = new AdminContestVO();
        vo.setId(contest.getId());
        vo.setSlug(contest.getSlug());
        vo.setTitle(contest.getTitle());
        vo.setDescription(contest.getDescription());
        vo.setContestType(contest.getContestType());
        vo.setStatus(contest.getStatus());
        vo.setStartTime(contest.getStartTime());
        vo.setEndTime(contest.getEndTime());
        vo.setDurationMinutes(contest.getDurationMinutes());
        vo.setIsVisible(contest.getIsVisible());
        vo.setParticipantCount(contest.getRegisteredCount());
        vo.setCreatedAt(contest.getCreatedAt());
        vo.setUpdatedAt(contest.getUpdatedAt());
        vo.setProblemCount((int) problemCount);

        return vo;
    }

    private static void requirePage(PageResult<?> page, String owner) {
        if (page == null || page.getItems() == null || page.getItems().stream().anyMatch(java.util.Objects::isNull)
                || page.getTotal() == null || page.getTotal() < 0) {
            throw AdminReadContract.ownerUnavailable(owner);
        }
    }

    @Override
    public String generateSlug(String title) {
        if (title == null || title.isBlank()) {
            return "contest-" + uuidGenerator.newId().substring(0, 8);
        }
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (slug.length() < 3) {
            slug = slug + "-" + uuidGenerator.newId().substring(0, 8);
        }

        return slug;
    }
}
