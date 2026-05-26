package com.ulticode.modules.forum.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.*;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.user.entity.User;

import java.util.List;
import java.util.Map;

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

    Map<String, User> batchLoadAuthors(List<ForumPost> posts);

    ForumPostVO convertToPostVO(ForumPost post, String userId, User author);

    ForumCommunityVO toCommunityVO(com.ulticode.modules.forum.entity.ForumCommunity community);

    ForumTagVO toTagVO(com.ulticode.modules.forum.entity.ForumTag tag);
}