package com.ulticode.modules.user.port;

import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * Application-side user profile write seam (P3-OWNER-002).
 *
 * <p>App owns profile fields (nickname, avatar, bio, githubUrl, blogUrl, location).
 * Account fields (credentials, password, role, ban) are owned by Auth (AuthAccountPort).
 */
public interface UserProfilePort {

    /**
     * Update profile attributes for a user (nickname, bio, githubUrl, blogUrl, location).
     *
     * @param userId the user ID to update
     * @param updateDTO the profile update data
     * @return the updated user view object
     */
    UserVO updateProfile(String userId, UpdateUserDTO updateDTO);

    /**
     * Upload and update avatar for a user.
     *
     * @param userId the user ID
     * @param file the avatar file
     * @return the saved avatar URL
     */
    String uploadAvatar(String userId, MultipartFile file);

    /**
     * Directly set/update avatar URL for a user.
     *
     * @param userId the user ID
     * @param avatarUrl the avatar URL
     */
    void updateAvatarUrl(String userId, String avatarUrl);
}
