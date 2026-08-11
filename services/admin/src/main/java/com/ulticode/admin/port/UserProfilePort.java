package com.ulticode.admin.port;

import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin-shell user profile write seam.
 *
 * <p>Replaces the former {@code com.ulticode.modules.user.port.UserProfilePort}
 * from backend-legacy. The admin shell owns this interface; its implementation
 * ({@code AdminUserProfileAdapter}) issues App-owned
 * {@link UpdateProfileCommand} commands to the public
 * {@code ProfileWriteService} (backend-app), which is the sole writer of the
 * App-owned {@code user_profiles} table (canonical source).
 *
 * <p>All carrier types are public backend-app-api contracts
 * ({@link UpdateProfileCommand}, {@link ProfileWriteResult}); no App-private
 * DTO/entity/mapper types cross this seam.
 */
public interface UserProfilePort {

    /**
     * Update profile attributes for a user (name, bio, github, location, etc.).
     *
     * @param command the profile update command (carries accountId + fields)
     * @return the post-update profile write result
     */
    ProfileWriteResult updateProfile(UpdateProfileCommand command);

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
