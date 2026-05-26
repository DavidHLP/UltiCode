package com.ulticode.modules.forum.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.*;

import java.util.List;

public interface ForumService {

    List<ForumPostVO> findAllPosts(String userId);

    PageResult<ForumPostVO> findAllPosts(String userId, int page, int pageSize);

    PageResult<ForumPostVO> findAllPosts(String userId, String sortBy, int page, int pageSize);

    void recordShare(String postId);

    void recordView(String postId);

    ForumPostVO findPostById(String id, String userId);

    List<ForumPostVO> findMyPosts(String userId);

    PageResult<ForumPostVO> findMyPosts(String userId, int page, int pageSize);

    ForumPostVO createPost(CreatePostDTO dto, String userId);

    ForumPostVO updatePost(String id, UpdatePostDTO dto, String userId);

    void deletePost(String id, String userId);

    ForumPostThreadVO getPostThread(String postId, String userId);

    ForumCommentVO createComment(String postId, CreateCommentDTO dto, String userId);

    ForumCommentVO updateComment(String id, UpdateCommentDTO dto, String userId);

    void deleteComment(String id, String userId);

    List<ForumCommunityVO> findAllCommunities(boolean featuredOnly);

    ForumCommunityDetailVO findCommunityBySlugOrId(String slugOrId);

    List<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId);

    PageResult<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId, int page, int pageSize);

    void joinCommunity(String communityId, String userId);

    void leaveCommunity(String communityId, String userId);

    List<ForumTagVO> findAllTags();

    List<QuickFilterDTO> getQuickFilters();
}