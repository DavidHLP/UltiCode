package com.ulticode.admin.port;

import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin-shell user profile write seam.
 *
 * <p>Replaces the former {@code com.ulticode.modules.user.port.UserProfilePort}
 * from backend-legacy. The admin shell owns this interface; its implementation
 * ({@code AdminUserProfileAdapter}) dual-writes to the App-owned
 * {@code user_profiles} table and the transitional {@code users} profile
 * columns via {@code UserProfileWriteMapper}.
 *
 * <p>DTO types ({@link UpdateUserDTO}, {@link UserVO}) remain in
 * {@code com.ulticode.modules.user.dto} (backend-app) and are shared across
 * the App/Admin boundary at the same FQN.
 */
public interface UserProfilePort {

    /**
     * Update profile attributes for a user (name, bio, github, location, etc.).
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
