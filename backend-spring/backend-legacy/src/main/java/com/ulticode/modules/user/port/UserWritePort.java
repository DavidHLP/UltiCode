package com.ulticode.modules.user.port;

import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * Write surface for the user domain.
 *
 * <p>Extracted from the deleted {@code UserService} facade. Owns every
 * mutating operation on user records behind a small interface. The
 * default adapter preserves the {@code @Transactional} and
 * {@code @CacheEvict} guards the facade used to scatter across methods.
 * The dependency category is in-process; the seam is real because
 * {@code UserController} is the only caller and the default adapter is
 * the only provider today (tests can substitute a fake).
 *
 * @author ulticode
 */
public interface UserWritePort {

    /**
     * Update the current authenticated user's profile.
     *
     * @param updateDTO the update data
     * @return the updated user view object
     */
    UserVO updateCurrentUser(UpdateUserDTO updateDTO);

    /**
     * Update the last login timestamp for a user.
     *
     * @param userId the user ID
     */
    void updateLastLoginAt(String userId);

    /**
     * Change the current authenticated user's password.
     *
     * @param changePasswordDTO the change password data
     */
    void changePassword(ChangePasswordDTO changePasswordDTO);

    /**
     * Upload and update the current user's avatar.
     *
     * @param file the avatar image file
     * @return the URL of the uploaded avatar
     */
    String uploadAvatar(MultipartFile file);
}