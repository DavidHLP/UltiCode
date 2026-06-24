package com.ulticode.modules.user.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.dto.ProfileVO;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserSkillsDTO;
import com.ulticode.modules.user.dto.UserStatsDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;

import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service interface for user-related operations.
 */
public interface UserService {

    /**
     * Find a user by their unique ID.
     *
     * @param id the user ID
     * @return the user entity, or empty if not found
     */
    Optional<User> findById(String id);

    /**
     * Find multiple users by their IDs, returned as a map keyed by user ID.
     *
     * @param ids the user IDs
     * @return map of user ID to User entity
     */
    default Map<String, User> findAllById(Collection<String> ids) {
        return ids.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /**
     * Find a user by their username.
     *
     * @param username the username
     * @return the user entity, or empty if not found
     */
    Optional<User> findByUsername(String username);

    /**
     * Find a user by their email address.
     *
     * @param email the email address
     * @return the user entity, or empty if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Get the current authenticated user's profile.
     *
     * @return the user view object
     */
    UserVO getCurrentUser();

    /**
     * Update the current authenticated user's profile.
     *
     * @param updateDTO the update data
     * @return the updated user view object
     */
    UserVO updateCurrentUser(UpdateUserDTO updateDTO);

    /**
     * List users with pagination.
     *
     * @param page     the page number (1-based)
     * @param pageSize the number of items per page
     * @return paginated result of user view objects
     */
    PageResult<UserVO> listUsers(Integer page, Integer pageSize);

    /**
     * Get a user by ID (public profile).
     *
     * @param id the user ID
     * @return the user view object
     */
    UserVO getUserById(String id);

    /**
     * Get user statistics including solved problems count by difficulty,
     * streak, total solved, and submission heatmap.
     *
     * @param id the user ID
     * @return the user statistics
     */
    UserStatsDTO getUserStatsById(String id);

    /**
     * Update the last login timestamp for a user.
     *
     * @param userId the user ID
     */
    void updateLastLoginAt(String userId);

    /**
     * Get user skills (tag statistics) for a user.
     *
     * @param id the user ID
     * @return the user skills data
     */
    UserSkillsDTO getUserSkillsById(String id);

    /**
     * Convert a User entity to UserVO.
     *
     * @param user the user entity
     * @return the user view object
     */
    UserVO toVO(User user);

    /**
     * Get a user's full profile including stats and social counts.
     *
     * @param id the user ID
     * @return the user profile view object
     */
    ProfileVO getUserProfile(String id);

    /**
     * Get a user's full profile by their username.
     *
     * @param username the username
     * @return the user profile view object
     */
    ProfileVO getUserProfileByUsername(String username);

    /**
     * Upload and update the current user's avatar.
     *
     * @param file the avatar image file
     * @return the URL of the uploaded avatar
     */
    String uploadAvatar(MultipartFile file);

    /**
     * Change the current authenticated user's password.
     *
     * @param changePasswordDTO the change password data
     */
    void changePassword(ChangePasswordDTO changePasswordDTO);
}
