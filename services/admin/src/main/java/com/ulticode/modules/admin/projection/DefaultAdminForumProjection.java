package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.dto.AdminForumCommunityDTO;
import com.ulticode.app.api.dto.AdminForumCommunityPage;
import com.ulticode.app.api.dto.AdminForumPostPage;
import com.ulticode.app.api.dto.AdminForumPostQuery;
import com.ulticode.app.api.dto.AdminForumPostRowDTO;
import com.ulticode.app.api.service.AdminForumReadPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminForumCommunityVO;
import com.ulticode.modules.admin.dto.AdminForumPostQueryDTO;
import com.ulticode.modules.admin.dto.AdminForumPostVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminForumProjection}. Owns every
 * row-to-VO projection rule and read-side aggregation for the admin forum
 * surface &mdash; see the interface javadoc for why this is a deep module.
 *
 * <p>All methods are pure reads; none mutate post or community state.
 * Underlying data comes from the App-owned {@link AdminForumReadPort}
 * (Dubbo), which returns flat typed rows with comment counts, community
 * name/slug and vote counts already composed; this projection merges the
 * author profile via {@link AdminUserEnricher} and shapes the
 * {@code AdminForumPostVO} / {@code AdminForumCommunityVO} HTTP views.
 * No forum entity or mapper is imported.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminForumProjection implements AdminForumProjection {

    private final AdminForumReadPort adminForumReadPort;
    private final AdminUserEnricher userEnricher;

    // ------------------------------------------------------------------
    // Paginated post list read (query build + batch author enrichment)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminForumPostVO> getPosts(AdminForumPostQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);

        AdminForumPostQuery portQuery = new AdminForumPostQuery(
                query.getSearch(),
                query.getCommunityId(),
                query.getAuthorId(),
                query.getIsFlagged(),
                query.getIsPinned(),
                query.getIsLocked(),
                query.getIsDeleted(),
                query.getSortBy(),
                query.getSortOrder(),
                pageRequest.page(),
                pageRequest.pageSize());

        AdminForumPostPage page = adminForumReadPort.listPosts(portQuery);

        Set<String> userIds = page.rows().stream()
                .map(AdminForumPostRowDTO::getUserId)
                .collect(Collectors.toSet());
        Map<String, AdminUserSummary> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userEnricher.enrich(userIds);

        List<AdminForumPostVO> vos = page.rows().stream()
                .map(row -> toAdminVO(row, userMap.get(row.getUserId())))
                .collect(Collectors.toList());
        return PageResult.of(vos, page.total(), pageRequest);
    }

    // ------------------------------------------------------------------
    // Single-item detail read
    // ------------------------------------------------------------------

    @Override
    public AdminForumPostVO getPost(String id) {
        AdminForumPostRowDTO row = adminForumReadPort.getPost(id);
        if (row == null) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        AdminUserSummary user = userEnricher.enrichOne(row.getUserId());
        return toAdminVO(row, user);
    }

    // ------------------------------------------------------------------
    // Community list read (filter dropdown source)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminForumCommunityVO> getCommunities(int page, int limit, String search) {
        PaginationRequest communitiesRequest = PaginationRequest.of(page, limit);

        AdminForumCommunityPage result = adminForumReadPort.listCommunities(
                communitiesRequest.page(), communitiesRequest.pageSize(), search);

        List<AdminForumCommunityVO> voList = result.rows().stream()
                .map(this::toCommunityVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.total(), communitiesRequest);
    }

    // ------------------------------------------------------------------
    // Projection helpers (row &rarr; AdminForumPostVO / AdminForumCommunityVO)
    // ------------------------------------------------------------------

    /**
     * Convert an App-owned forum post row to an AdminForumPostVO, merging
     * the author profile loaded via {@link AdminUserEnricher}.
     */
    private AdminForumPostVO toAdminVO(AdminForumPostRowDTO row, AdminUserSummary user) {
        if (row == null) {
            return null;
        }

        AdminForumPostVO vo = new AdminForumPostVO();
        vo.setId(row.getId());
        vo.setTitle(row.getTitle());
        vo.setExcerpt(row.getExcerpt());
        vo.setContent(row.getContent());
        vo.setUserId(row.getUserId());
        vo.setCommunityId(row.getCommunityId());
        vo.setCommunityName(row.getCommunityName());
        vo.setCommunitySlug(row.getCommunitySlug());
        vo.setViewCount(row.getViews() != null ? row.getViews() : 0);
        vo.setCommentCount(row.getCommentCount() != null ? row.getCommentCount() : 0);
        vo.setUpvotes(row.getUpvotes() != null ? row.getUpvotes() : 0);
        vo.setDownvotes(row.getDownvotes() != null ? row.getDownvotes() : 0);
        vo.setIsPinned(row.getIsPinned() != null ? row.getIsPinned() : false);
        vo.setIsLocked(row.getIsLocked() != null ? row.getIsLocked() : false);
        vo.setIsFlagged(row.getIsFlagged() != null ? row.getIsFlagged() : false);
        vo.setFlaggedReason(row.getFlaggedReason());
        vo.setFlaggedAt(row.getFlaggedAt());
        vo.setIsDeleted(row.getIsDeleted() != null ? row.getIsDeleted() : false);
        vo.setDeletedAt(row.getDeletedAt());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt() != null ? row.getUpdatedAt() : row.getCreatedAt());

        if (user != null) {
            vo.setUsername(user.username());
            vo.setAvatar(user.avatar());
        }

        return vo;
    }

    /**
     * Convert an App-owned community row to an AdminForumCommunityVO for
     * the filter dropdown.
     */
    private AdminForumCommunityVO toCommunityVO(AdminForumCommunityDTO community) {
        AdminForumCommunityVO vo = new AdminForumCommunityVO();
        vo.setId(community.id());
        vo.setName(community.name());
        vo.setSlug(community.slug());
        vo.setDescription(community.description());
        vo.setPostCount(community.postCount() != null ? community.postCount() : 0);
        vo.setMemberCount(community.memberCount() != null ? community.memberCount() : 0);
        return vo;
    }
}
