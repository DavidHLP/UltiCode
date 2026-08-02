package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
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

        PageResult<ContestAdminDTO> result = contestAdminReadPort.selectPage(
                page, limit, query.getSearch(), query.getStatus(), query.getType());

        List<AdminContestVO> vos = result.getItems().stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());

        return PageResult.of(vos, result.getTotal(), page, limit);
    }

    @Override
    public AdminContestVO getContest(String id) {
        ContestAdminDTO contest = contestAdminReadPort.selectById(id);
        if (contest == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Contest not found");
        }
        return toAdminVO(contest);
    }

    @Override
    public AdminContestVO toAdminVO(ContestAdminDTO contest) {
        if (contest == null) {
            return null;
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
        vo.setCreatedAt(contest.getCreatedAt());
        vo.setUpdatedAt(contest.getUpdatedAt());
        vo.setProblemCount((int) contestAdminReadPort.countProblemsByContestId(contest.getId()));

        return vo;
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
