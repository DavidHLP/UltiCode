package com.ulticode.modules.forum.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.*;
import com.ulticode.modules.forum.entity.ForumPost;

import java.util.List;

/**
 * Write-side service for forum posts. Owns the transactional create / update /
 * delete paths and the SQL + paging for the read paths.
 *
 * <p><b>Deepened.</b> All entity-to-VO projection rules and the batch-load
 * helpers live behind {@code ForumReadProjection}; this service delegates to it
 * for any VO it returns. The seam inversion keeps the projection rules with
 * the data they describe.
 *
 * <p>Reads that return VOs ({@link #findAllPosts}, {@link #findMyPosts},
 * {@link #findPostById}) are kept on this interface because callers
 * (e.g. {@code ForumReadProjection}) cross this seam to reach the SQL; the
 * projection then re-projects entities to VOs through its own rules. This is
 * the same {@code ModerationProjection} / {@code ForumPostService} pattern
 * used elsewhere — see ADR-0011.
 */
public interface ForumPostService {

    List<ForumPostVO> findAllPosts(String userId);

    PageResult<ForumPostVO> findAllPosts(String userId, int page, int pageSize);

    PageResult<ForumPostVO> findAllPosts(String userId, String sortBy, int page, int pageSize);

    ForumPostVO findPostById(String id, String userId);

    List<ForumPostVO> findMyPosts(String userId);

    PageResult<ForumPostVO> findMyPosts(String userId, int page, int pageSize);

    ForumPostVO createPost(CreatePostDTO dto, String userId);

    ForumPostVO updatePost(String id, UpdatePostDTO dto, String userId);

    void deletePost(String id, String userId);

    ForumPostThreadVO getPostThread(String postId, String userId);

    void recordShare(String postId);

    void recordView(String postId);

    long countByCommunityId(String communityId);

    List<ForumPost> findByCommunityId(String communityId, int limit, int offset);
}