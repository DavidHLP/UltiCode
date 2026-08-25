package com.ulticode.admin.config;

import com.ulticode.common.storage.AvatarStorage;
import com.ulticode.common.storage.LocalAvatarStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Review 2026-08-25 FINAL P2: admin-side avatar storage wiring, mirroring the
 * App service so both writers persist through the same seam and directory
 * convention ({@code app.storage.local.dir}).
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "local", matchIfMissing = true)
public class AdminStorageConfig {

    @Bean
    public AvatarStorage avatarStorage(
            @Value("${app.storage.local.dir:uploads/avatars}") String dir) {
        return new LocalAvatarStorage(dir);
    }
}
