package com.ulticode.modules.admin.port.adapter;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.user.port.UserProfileWriteMapper;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.port.UserProfilePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Admin-shell adapter for the legacy {@link UserProfilePort} contract
 * (P7-RELOCATE).
 *
 * <p>Replaces the excluded legacy {@code DefaultUserProfileAdapter} (which
 * needs the legacy {@code UserMapper}, intentionally absent from the admin
 * {@code @MapperScan}). Mirrors the App-side
 * {@code DefaultAppUserWritePort} write path: profile mutations dual-write
 * to the App-owned {@code user_profiles} table (canonical) and the profile
 * columns of the Auth-owned {@code users} table via
 * {@link UserProfileWriteMapper} (transitional, keeps the
 * {@code UserReadMapper} Q-read consistent during the P5-USERPROFILE-001
 * dual-write window).
 *
 * <p>Avatar upload preserves the legacy file-storage semantics (uploads
 * directory, UUID filename, content-type + size + extension validation).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserProfileAdapter implements UserProfilePort {

    private final UserProfileMapper userProfileMapper;
    private final UserProfileWriteMapper userProfileWriteMapper;
    private final UuidGenerator uuidGenerator;

    @Override
    @Transactional
    @CacheEvict(value = "userStats", allEntries = true)
    public UserVO updateProfile(String userId, UpdateUserDTO updateDTO) {
        if (userId == null) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED);
        }

        UserProfile profile = userProfileMapper.selectById(userId);
        boolean isNew = profile == null;
        if (isNew) {
            profile = new UserProfile();
            profile.setAccountId(userId);
        }

        if (updateDTO.getName() != null) {
            profile.setName(updateDTO.getName());
            userProfileWriteMapper.updateName(userId, updateDTO.getName());
        }
        if (updateDTO.getAvatar() != null) {
            profile.setAvatar(updateDTO.getAvatar());
            userProfileWriteMapper.updateAvatar(userId, updateDTO.getAvatar());
        }
        if (updateDTO.getBio() != null) {
            profile.setBio(updateDTO.getBio());
            userProfileWriteMapper.updateBio(userId, updateDTO.getBio());
        }
        if (updateDTO.getCompany() != null) {
            profile.setCompany(updateDTO.getCompany());
            userProfileWriteMapper.updateCompany(userId, updateDTO.getCompany());
        }
        if (updateDTO.getGithub() != null) {
            profile.setGithub(updateDTO.getGithub());
            userProfileWriteMapper.updateGithub(userId, updateDTO.getGithub());
        }
        if (updateDTO.getLocation() != null) {
            profile.setLocation(updateDTO.getLocation());
            userProfileWriteMapper.updateLocation(userId, updateDTO.getLocation());
        }
        if (updateDTO.getTwitter() != null) {
            profile.setTwitter(updateDTO.getTwitter());
            userProfileWriteMapper.updateTwitter(userId, updateDTO.getTwitter());
        }
        if (updateDTO.getWebsite() != null) {
            profile.setWebsite(updateDTO.getWebsite());
            userProfileWriteMapper.updateWebsite(userId, updateDTO.getWebsite());
        }
        if (updateDTO.getPreferredLanguage() != null) {
            profile.setPreferredLanguage(updateDTO.getPreferredLanguage());
            userProfileWriteMapper.updatePreferredLanguage(userId, updateDTO.getPreferredLanguage());
        }

        if (isNew) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }

        log.info("User profile updated (dual-write): {}", userId);
        return toVO(profile);
    }

    @Override
    @Transactional
    public String uploadAvatar(String userId, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED);
        }

        if (file == null || file.isEmpty()) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "File is required");
        }

        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "File size exceeds 5MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "Only image files are allowed");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            String rawExt = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            ext = rawExt.replaceAll("[^a-z0-9]", "");
            if (!ext.isEmpty() && !ext.equals("jpg") && !ext.equals("jpeg") &&
                !ext.equals("png") && !ext.equals("gif") && !ext.equals("webp")) {
                throw new BusinessException(AdminErrorCode.BAD_REQUEST, "Invalid file extension");
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
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "Failed to save avatar");
        }

        String avatarUrl = "/uploads/avatars/" + filename;
        updateAvatarUrl(userId, avatarUrl);

        log.info("Avatar uploaded (dual-write) for user {}: {}", userId, avatarUrl);
        return avatarUrl;
    }

    @Override
    @Transactional
    public void updateAvatarUrl(String userId, String avatarUrl) {
        if (userId == null) {
            return;
        }
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
        userProfileWriteMapper.updateAvatar(userId, avatarUrl);
    }

    private UserVO toVO(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(profile.getAccountId());
        vo.setName(profile.getName());
        vo.setAvatar(profile.getAvatar());
        vo.setBio(profile.getBio());
        vo.setCompany(profile.getCompany());
        vo.setGithub(profile.getGithub());
        vo.setLocation(profile.getLocation());
        vo.setTwitter(profile.getTwitter());
        vo.setWebsite(profile.getWebsite());
        vo.setPreferredLanguage(profile.getPreferredLanguage());
        return vo;
    }
}
