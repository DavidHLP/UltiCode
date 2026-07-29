package com.ulticode.modules.user.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.entity.UserProfile;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Production implementation of {@link UserProfilePort} (P3-OWNER-002).
 *
 * <p>Owns profile attribute mutations (name, avatar, bio, company, github, location,
 * twitter, website, preferredLanguage).
 *
 * <p>P5-USERPROFILE-001: Dual-write expand phase. Profile updates are written to both
 * the legacy {@code users} table (account + profile columns, backward compat) and the
 * new {@code user_profiles} table (profile columns only). The shadow compare IT verifies
 * both tables stay in sync. A future contract migration will remove profile columns from
 * {@code users} once the dual-write window is validated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultUserProfileAdapter implements UserProfilePort {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final UuidGenerator uuidGenerator;

    @Override
    @Transactional
    @CacheEvict(value = "userStats", allEntries = true)
    public UserVO updateProfile(String userId, UpdateUserDTO updateDTO) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Update fields from DTO (only non-null profile fields)
        if (updateDTO.getName() != null) {
            user.setName(updateDTO.getName());
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

        // P5-USERPROFILE-001: dual-write to user_profiles table
        syncProfileTable(userId, updateDTO);

        log.info("User profile updated: {}", userId);
        return toVO(user);
    }

    @Override
    @Transactional
    public String uploadAvatar(String userId, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File is required");
        }

        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File size exceeds 5MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only image files are allowed");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            String rawExt = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            ext = rawExt.replaceAll("[^a-z0-9]", "");
            if (!ext.isEmpty() && !ext.equals("jpg") && !ext.equals("jpeg") &&
                !ext.equals("png") && !ext.equals("gif") && !ext.equals("webp")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid file extension");
            }
            ext = "." + ext;
        }
        String filename = uuidGenerator.newId() + ext;

        Path uploadDir = Paths.get("uploads/avatars");
        try {
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(filename);
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            log.error("Failed to save avatar for user {}: {}", userId, e.getMessage());
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to save avatar");
        }

        String avatarUrl = "/uploads/avatars/" + filename;
        updateAvatarUrl(userId, avatarUrl);

        log.info("Avatar uploaded for user {}: {}", userId, avatarUrl);
        return avatarUrl;
    }

    @Override
    @Transactional
    public void updateAvatarUrl(String userId, String avatarUrl) {
        if (userId == null) {
            return;
        }
        User user = new User();
        user.setId(userId);
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);

        // P5-USERPROFILE-001: dual-write avatar to user_profiles table
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setAccountId(userId);
            profile.setAvatar(avatarUrl);
            userProfileMapper.insert(profile);
        } else {
            profile.setAvatar(avatarUrl);
            userProfileMapper.updateById(profile);
        }
    }

    /**
     * P5-USERPROFILE-001: Sync profile fields from the DTO to the {@code user_profiles} table.
     * Inserts a new row if none exists, or updates non-null fields otherwise.
     */
    private void syncProfileTable(String userId, UpdateUserDTO updateDTO) {
        UserProfile existing = userProfileMapper.selectById(userId);
        boolean isNew = existing == null;
        if (isNew) {
            existing = new UserProfile();
            existing.setAccountId(userId);
        }

        if (updateDTO.getName() != null) {
            existing.setName(updateDTO.getName());
        }
        if (updateDTO.getAvatar() != null) {
            existing.setAvatar(updateDTO.getAvatar());
        }
        if (updateDTO.getBio() != null) {
            existing.setBio(updateDTO.getBio());
        }
        if (updateDTO.getCompany() != null) {
            existing.setCompany(updateDTO.getCompany());
        }
        if (updateDTO.getGithub() != null) {
            existing.setGithub(updateDTO.getGithub());
        }
        if (updateDTO.getLocation() != null) {
            existing.setLocation(updateDTO.getLocation());
        }
        if (updateDTO.getTwitter() != null) {
            existing.setTwitter(updateDTO.getTwitter());
        }
        if (updateDTO.getWebsite() != null) {
            existing.setWebsite(updateDTO.getWebsite());
        }
        if (updateDTO.getPreferredLanguage() != null) {
            existing.setPreferredLanguage(updateDTO.getPreferredLanguage());
        }

        if (isNew) {
            userProfileMapper.insert(existing);
        } else {
            userProfileMapper.updateById(existing);
        }
    }

    private UserVO toVO(User user) {
        if (user == null) {
            return null;
        }

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
