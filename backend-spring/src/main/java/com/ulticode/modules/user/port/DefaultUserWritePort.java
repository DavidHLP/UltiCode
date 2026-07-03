package com.ulticode.modules.user.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Default (and only) adapter for {@link UserWritePort}. Owns every
 * mutating operation on user records — see the interface javadoc for why
 * this is a deep module.
 *
 * <p>The profile update preserves the {@code @Transactional} +
 * {@code @CacheEvict("userStats", allEntries=true)} guards the deleted
 * {@code UserService} facade used to declare. The password change
 * preserves its own {@code @Transactional} boundary plus the password
 * match + confirm guards. The avatar upload owns the file-type / size
 * validation, the on-disk write under {@code uploads/avatars}, and the
 * avatar-URL persistence — all of which the facade used to inline.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultUserWritePort implements UserWritePort {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    @CacheEvict(value = "userStats", allEntries = true)
    public UserVO updateCurrentUser(UpdateUserDTO updateDTO) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Check if email is being changed and if it's already taken
        if (updateDTO.getEmail() != null && !updateDTO.getEmail().equals(user.getEmail())) {
            LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(User::getEmail, updateDTO.getEmail());
            User existingUser = userMapper.selectOne(queryWrapper);
            if (existingUser != null) {
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
    @Transactional
    public void changePassword(ChangePasswordDTO changePasswordDTO) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(changePasswordDTO.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_INCORRECT);
        }

        if (!changePasswordDTO.getNewPassword().equals(changePasswordDTO.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_MISMATCH);
        }

        String hashedPassword = passwordEncoder.encode(changePasswordDTO.getNewPassword());
        user.setPassword(hashedPassword);
        userMapper.updateById(user);

        log.info("Password changed for user: {}", userId);
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        String userId = SecurityUtil.getCurrentUserId();
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
        String filename = UUID.randomUUID().toString() + ext;

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

        User user = new User();
        user.setId(userId);
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);

        log.info("Avatar uploaded for user {}: {}", userId, avatarUrl);
        return avatarUrl;
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