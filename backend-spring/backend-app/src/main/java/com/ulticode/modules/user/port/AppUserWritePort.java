package com.ulticode.modules.user.port;

import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * App-side user write port for profile mutations
 * (P7-RELOCATE-USER-REMAINDER-001).
 *
 * <p>Owns profile-field mutations (name, avatar, bio, company, github,
 * location, twitter, website, preferredLanguage). These write to the
 * App-owned {@code user_profiles} table only; the transitional dual-write
 * to {@code users} profile columns (P5-USERPROFILE-001) ends here.
 *
 * <p>Account mutations (password, role, ban status) stay Auth-owned and
 * are NOT on this interface.
 */
public interface AppUserWritePort {

    /**
     * Update the current authenticated user's profile.
     *
     * @param userId   the current user id (must be non-null)
     * @param updateDTO the profile update data
     * @return the updated user view object
     */
    UserVO updateProfile(String userId, UpdateUserDTO updateDTO);

    /**
     * Upload and update the current user's avatar.
     *
     * @param userId the current user id
     * @param file   the avatar image file
     * @return the saved avatar URL
     */
    String uploadAvatar(String userId, MultipartFile file);
}
