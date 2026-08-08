package com.ulticode.modules.forum.service;

import com.ulticode.modules.forum.dto.CreatePostDTO;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.dto.UpdatePostDTO;

/**
 * Write-only side of the forum post lifecycle. Owns the transactional
 * create / update / delete paths plus share / view bumps.
 *
 * <p>Read-side methods live on
 * {@link com.ulticode.modules.forum.projection.ForumReadProjection} — this
 * split breaks the constructor-injection cycle that previously existed
 * between the read projection and this service (Spring Boot 3.x forbids
 * cyclic bean wiring by default).
 */
public interface ForumPostService {

    ForumPostVO createPost(CreatePostDTO dto, String userId);

    ForumPostVO updatePost(String id, UpdatePostDTO dto, String userId);

    void deletePost(String id, String userId);

    void recordShare(String postId);

    void recordView(String postId);
}