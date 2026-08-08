package com.ulticode.modules.user.projection;

import com.ulticode.app.user.port.UserSummaryView;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.user.dto.ProfileVO;
import com.ulticode.modules.user.dto.UserSkillsDTO;
import com.ulticode.modules.user.dto.UserStatsDTO;
import com.ulticode.modules.user.dto.UserVO;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Read surface for the user domain.
 *
 * <p>Extracted from the deleted {@code UserService} facade. Owns every
 * read-side operation on user records — including the cross-table joins
 * the facade used to scatter across {@code UserServiceImpl} (user +
 * submissions + problems + problem-tag-relations + follow). The
 * dependency category is in-process; the seam is real because
 * {@code UserController} is the only caller and the default adapter is
 * the only provider today (tests can substitute a fake).
 *
 * @author ulticode
 */
public interface UserReadProjection {

    /**
     * Find a user by their unique ID.
     *
     * @param id the user ID
     * @return the user if found
     */
    Optional<UserSummaryView> findById(String id);

    /**
     * Find multiple users by their IDs, returned as a map keyed by user ID.
     *
     * @param ids the collection of user IDs
     * @return a map of user ID to user
     */
    Map<String, UserSummaryView> findAllById(Collection<String> ids);

    /**
     * Find a user by their username.
     *
     * @param username the username
     * @return the user if found
     */
    Optional<UserSummaryView> findByUsername(String username);

    /**
     * Find a user by their email address.
     *
     * @param email the email address
     * @return the user if found
     */
    Optional<UserSummaryView> findByEmail(String email);

    /**
     * Get the current authenticated user's profile.
     *
     * @return the user view object
     */
    UserVO getCurrentUser();

    /**
     * List users with pagination.
     *
     * @param page     page number (1-based)
     * @param pageSize page size
     * @return paginated user list
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
     * submission heatmap, and global ranking.
     *
     * @param id the user ID
     * @return the user statistics
     */
    UserStatsDTO getUserStatsById(String id);

    /**
     * Get user skills (tag statistics) for a user.
     *
     * @param id the user ID
     * @return the user skills DTO
     */
    UserSkillsDTO getUserSkillsById(String id);

    /**
     * Convert a UserSummaryView to UserVO.
     *
     * @param user the user summary view
     * @return the user view object
     */
    UserVO toVO(UserSummaryView user);

    /**
     * Get a user's full profile including stats and social counts.
     *
     * @param id the user ID
     * @return the profile view object
     */
    ProfileVO getUserProfile(String id);

    /**
     * Get a user's full profile by their username.
     *
     * @param username the username
     * @return the profile view object
     */
    ProfileVO getUserProfileByUsername(String username);
}
