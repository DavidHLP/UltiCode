package com.ulticode.modules.user.port;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarUrlsTest {

    @Test
    void mismatchedAvatarKeyIsNotExposed() {
        assertThat(AvatarUrls.resolve("user-1", "app/avatars/user-2/avatar.png")).isNull();
    }
}
