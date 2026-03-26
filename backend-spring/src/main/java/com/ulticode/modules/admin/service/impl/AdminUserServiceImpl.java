package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.service.AdminUserService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of AdminUserService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<AdminUserVO> getUsers(AdminUserQueryDTO query) {
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // Search filter
        if (StringUtils.hasText(query.getSearch())) {
            String search = "%" + query.getSearch() + "%";
            wrapper.and(w -> w
                    .like(User::getUsername, search)
                    .or().like(User::getEmail, search)
                    .or().like(User::getName, search));
        }

        // Role filter
        if (StringUtils.hasText(query.getRole())) {
            wrapper.eq(User::getRole, query.getRole());
        }

        // Active status filter
        if (query.getIsActive() != null) {
            wrapper.eq(User::getIsActive, query.getIsActive());
        }

        // Banned status filter
        if (query.getIsBanned() != null) {
            wrapper.eq(User::getIsBanned, query.getIsBanned());
        }

        // Sorting
        boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "joinedAt";
        switch (sortBy) {
            case "username" -> wrapper.orderBy(true, isAsc, User::getUsername);
            case "email" -> wrapper.orderBy(true, isAsc, User::getEmail);
            case "lastLoginAt" -> wrapper.orderBy(true, isAsc, User::getLastLoginAt);
            default -> wrapper.orderBy(true, isAsc, User::getJoinedAt);
        }

        Page<User> userPage = new Page<>(page, limit);
        Page<User> result = userMapper.selectPage(userPage, wrapper);

        List<AdminUserVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), page, limit);
    }

    @Override
    public AdminUserVO getUserById(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    @Override
    @Transactional
    public AdminUserVO banUser(String id, String reason, String until) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, id)
                .set(User::getIsBanned, true)
                .set(User::getBannedReason, reason);

        if (StringUtils.hasText(until)) {
            try {
                wrapper.set(User::getBannedUntil, LocalDateTime.parse(until));
            } catch (Exception e) {
                log.warn("Failed to parse banned_until date: {}", until);
            }
        }

        userMapper.update(null, wrapper);

        log.info("User banned: {} - reason: {}", id, reason);
        return getUserById(id);
    }

    @Override
    @Transactional
    public AdminUserVO unbanUser(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, id)
                .set(User::getIsBanned, false)
                .set(User::getBannedReason, null)
                .set(User::getBannedUntil, null);

        userMapper.update(null, wrapper);

        log.info("User unbanned: {}", id);
        return getUserById(id);
    }

    @Override
    @Transactional
    public void resetPassword(String id, String newPassword) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        String hashedPassword = passwordEncoder.encode(newPassword);
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, id)
                .set(User::getPassword, hashedPassword);

        userMapper.update(null, wrapper);
        log.info("Password reset for user: {}", id);
    }

    @Override
    @Transactional
    public List<BanResult> bulkBan(List<String> ids, String reason) {
        List<BanResult> results = new ArrayList<>();

        for (String id : ids) {
            try {
                banUser(id, reason, null);
                results.add(new BanResult(id, true, null));
            } catch (Exception e) {
                log.error("Failed to ban user {}: {}", id, e.getMessage());
                results.add(new BanResult(id, false, e.getMessage()));
            }
        }

        return results;
    }

    @Override
    @Transactional
    public List<BanResult> bulkUnban(List<String> ids) {
        List<BanResult> results = new ArrayList<>();

        for (String id : ids) {
            try {
                unbanUser(id);
                results.add(new BanResult(id, true, null));
            } catch (Exception e) {
                log.error("Failed to unban user {}: {}", id, e.getMessage());
                results.add(new BanResult(id, false, e.getMessage()));
            }
        }

        return results;
    }

    @Override
    @Transactional
    public List<DeleteResult> bulkDelete(List<String> ids) {
        List<DeleteResult> results = new ArrayList<>();

        for (String id : ids) {
            try {
                int deleted = userMapper.deleteById(id);
                if (deleted > 0) {
                    results.add(new DeleteResult(id, true, null));
                } else {
                    results.add(new DeleteResult(id, false, "User not found"));
                }
            } catch (Exception e) {
                log.error("Failed to delete user {}: {}", id, e.getMessage());
                results.add(new DeleteResult(id, false, e.getMessage()));
            }
        }

        return results;
    }

    /**
     * Convert User entity to AdminUserVO
     */
    private AdminUserVO toVO(User user) {
        if (user == null) {
            return null;
        }

        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setIsActive(user.getIsActive());
        vo.setIsBanned(user.getIsBanned());
        vo.setBanReason(user.getBannedReason());
        vo.setBannedUntil(user.getBannedUntil());
        vo.setJoinedAt(user.getJoinedAt());
        vo.setLastLoginAt(user.getLastLoginAt());
        // Permissions and stats can be added later if needed

        return vo;
    }
}
