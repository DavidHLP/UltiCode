package com.ulticode.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of UserService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public Optional<User> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectById(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        return Optional.ofNullable(userMapper.selectOne(queryWrapper));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, email);
        return Optional.ofNullable(userMapper.selectOne(queryWrapper));
    }

    @Override
    public UserVO getCurrentUser() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return toVO(user);
    }

    @Override
    @Transactional
    public UserVO updateCurrentUser(UpdateUserDTO updateDTO) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if email is being changed and if it's already taken
        if (updateDTO.getEmail() != null && !updateDTO.getEmail().equals(user.getEmail())) {
            Optional<User> existingUser = findByEmail(updateDTO.getEmail());
            if (existingUser.isPresent()) {
                throw new BusinessException(ErrorCode.AUTH_EMAIL_TAKEN);
            }
        }

        // Update fields from DTO (only non-null fields)
        if (updateDTO.getName() != null) {
            user.setName(updateDTO.getName());
        }
        if (updateDTO.getEmail() != null) {
            user.setEmail(updateDTO.getEmail());
        }
        if (updateDTO.getAvatar() != null) {
            user.setAvatar(updateDTO.getAvatar());
        }
        if (updateDTO.getBio() != null) {
            user.setBio(updateDTO.getBio());
        }
        if (updateDTO.getCompany() != null) {
            user.setCompany(updateDTO.getCompany());
        }
        if (updateDTO.getGithub() != null) {
            user.setGithub(updateDTO.getGithub());
        }
        if (updateDTO.getLocation() != null) {
            user.setLocation(updateDTO.getLocation());
        }
        if (updateDTO.getTwitter() != null) {
            user.setTwitter(updateDTO.getTwitter());
        }
        if (updateDTO.getWebsite() != null) {
            user.setWebsite(updateDTO.getWebsite());
        }
        if (updateDTO.getPreferredLanguage() != null) {
            user.setPreferredLanguage(updateDTO.getPreferredLanguage());
        }

        userMapper.updateById(user);

        log.info("User profile updated: {}", userId);
        return toVO(user);
    }

    @Override
    public PageResult<UserVO> listUsers(Integer page, Integer pageSize) {
        // Set default pagination values
        int currentPage = (page != null && page > 0) ? page : 1;
        int currentPageSize = (pageSize != null && pageSize > 0) ? pageSize : 20;

        // Limit page size to prevent large queries
        currentPageSize = Math.min(currentPageSize, 100);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getIsActive, true)
                .eq(User::getIsBanned, false)
                .orderByDesc(User::getJoinedAt);

        Page<User> userPage = new Page<>(currentPage, currentPageSize);
        Page<User> result = userMapper.selectPage(userPage, queryWrapper);

        List<UserVO> userVOList = result.getRecords().stream()
                .map(this::toPublicVO)
                .collect(Collectors.toList());

        return PageResult.of(userVOList, result.getTotal(), currentPage, currentPageSize);
    }

    @Override
    public UserVO getUserById(String id) {
        User user = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Return public profile (without email)
        return toPublicVO(user);
    }

    @Override
    @Transactional
    public void updateLastLoginAt(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        User user = new User();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.debug("Updated last login time for user: {}", userId);
    }

    @Override
    public UserVO toVO(User user) {
        if (user == null) {
            return null;
        }

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    /**
     * Convert a User entity to UserVO without sensitive information.
     * Used for public profiles.
     *
     * @param user the user entity
     * @return the user view object without email
     */
    private UserVO toPublicVO(User user) {
        if (user == null) {
            return null;
        }

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setCompany(user.getCompany());
        vo.setGithub(user.getGithub());
        vo.setJoinedAt(user.getJoinedAt());
        vo.setLocation(user.getLocation());
        vo.setTwitter(user.getTwitter());
        vo.setWebsite(user.getWebsite());
        vo.setPreferredLanguage(user.getPreferredLanguage());
        // Email is not included in public profile
        return vo;
    }
}
