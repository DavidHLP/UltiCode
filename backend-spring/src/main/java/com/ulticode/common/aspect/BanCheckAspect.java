package com.ulticode.common.aspect;

import com.ulticode.common.annotation.CheckBan;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class BanCheckAspect {

    private final UserMapper userMapper;

    @Before("@annotation(com.ulticode.common.annotation.CheckBan)")
    public void checkBan(JoinPoint joinPoint) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId != null) {
            User user = userMapper.selectById(userId);
            if (user != null && Boolean.TRUE.equals(user.getIsBanned())) {
                throw new BusinessException(ErrorCode.USER_BANNED);
            }
        }
    }
}
