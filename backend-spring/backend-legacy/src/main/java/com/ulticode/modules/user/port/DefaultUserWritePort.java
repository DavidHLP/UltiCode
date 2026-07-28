package com.ulticode.modules.user.port;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.account.AuthAccountPort;
import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Default adapter for {@link UserWritePort} (P3-OWNER-002).
 *
 * <p>Delegates profile updates to {@link UserProfilePort} and account/password
 * updates to {@link AuthAccountPort}, maintaining distinct boundaries between
 * App profile management and Auth credential management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultUserWritePort implements UserWritePort {

    private final UserProfilePort userProfilePort;
    private final AuthAccountPort authAccountPort;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    @CacheEvict(value = "userStats", allEntries = true)
    public UserVO updateCurrentUser(UpdateUserDTO updateDTO) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userProfilePort.updateProfile(userId, updateDTO);
    }

    @Override
    @Transactional
    public void updateLastLoginAt(String userId) {
        authAccountPort.updateLastLoginAt(userId);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordDTO changePasswordDTO) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = authAccountPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(changePasswordDTO.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_INCORRECT);
        }

        if (!changePasswordDTO.getNewPassword().equals(changePasswordDTO.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_MISMATCH);
        }

        String hashedPassword = passwordEncoder.encode(changePasswordDTO.getNewPassword());
        authAccountPort.updatePassword(userId, hashedPassword);

        log.info("Password changed for user: {}", userId);
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userProfilePort.uploadAvatar(userId, file);
    }
}
