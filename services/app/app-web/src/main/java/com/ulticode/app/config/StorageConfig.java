package com.ulticode.app.config;

import com.ulticode.common.storage.AvatarStorage;
import com.ulticode.common.storage.LocalAvatarStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Review 2026-08-25 FINAL P2: avatar storage wiring. {@code app.storage.mode}
 * selects the implementation; "local" (default) stores into the directory
 * given by {@code app.storage.local.dir}, which deployments back with a
 * shared volume so multi-replica rollouts stay correct.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "local", matchIfMissing = true)
public class StorageConfig {

    @Bean
    public AvatarStorage avatarStorage(
            @Value("${app.storage.local.dir:uploads/avatars}") String dir) {
        return new LocalAvatarStorage(dir);
    }
}
