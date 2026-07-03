package com.ulticode.modules.user.service.impl;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.dto.ProfileVO;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserSkillsDTO;
import com.ulticode.modules.user.dto.UserStatsDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.port.UserWritePort;
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * Thin facade delegating to {@link UserReadProjection} (reads) and
 * {@link UserWritePort} (writes). Preserved for backwards compatibility
 * with cross-module callers in {@code auth}, {@code admin},
 * {@code forum} and {@code websocket}; new code should bind to the
 * underlying ports/projections directly.
 *
 * @author ulticode
 * @deprecated use {@code UserReadProjection} and {@code UserWritePort}
 *             instead. This facade will be removed once all cross-module
 *             callers migrate.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserReadProjection userReadProjection;
    private final UserWritePort userWritePort;

    @Override
    public Optional<User> findById(String id) {
        return userReadProjection.findById(id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userReadProjection.findByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userReadProjection.findByEmail(email);
    }

    @Override
    public UserVO getCurrentUser() {
        return userReadProjection.getCurrentUser();
    }

    @Override
    public UserVO updateCurrentUser(UpdateUserDTO updateDTO) {
        return userWritePort.updateCurrentUser(updateDTO);
    }

    @Override
    public PageResult<UserVO> listUsers(Integer page, Integer pageSize) {
        return userReadProjection.listUsers(page, pageSize);
    }

    @Override
    public UserVO getUserById(String id) {
        return userReadProjection.getUserById(id);
    }

    @Override
    public UserStatsDTO getUserStatsById(String id) {
        return userReadProjection.getUserStatsById(id);
    }

    @Override
    public void updateLastLoginAt(String userId) {
        userWritePort.updateLastLoginAt(userId);
    }

    @Override
    public UserSkillsDTO getUserSkillsById(String id) {
        return userReadProjection.getUserSkillsById(id);
    }

    @Override
    public UserVO toVO(User user) {
        return userReadProjection.toVO(user);
    }

    @Override
    public ProfileVO getUserProfile(String id) {
        return userReadProjection.getUserProfile(id);
    }

    @Override
    public ProfileVO getUserProfileByUsername(String username) {
        return userReadProjection.getUserProfileByUsername(username);
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        return userWritePort.uploadAvatar(file);
    }

    @Override
    public void changePassword(ChangePasswordDTO changePasswordDTO) {
        userWritePort.changePassword(changePasswordDTO);
    }
}
