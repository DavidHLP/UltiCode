package com.ulticode.security.csrf;

import cn.hutool.core.util.IdUtil;
import org.springframework.stereotype.Service;

/**
 * CSRF Token 服务（临时实现，Phase 2 完善）
 */
@Service
public class CsrfService {

    /**
     * 生成 CSRF Token（临时实现）
     */
    public String generateToken(String userId) {
        // Phase 2 将使用 Redis 存储
        return IdUtil.simpleUUID();
    }

    /**
     * 验证 CSRF Token（临时实现）
     */
    public boolean validateToken(String userId, String token) {
        // Phase 2 将实现完整验证
        return token != null && !token.isEmpty();
    }
}
