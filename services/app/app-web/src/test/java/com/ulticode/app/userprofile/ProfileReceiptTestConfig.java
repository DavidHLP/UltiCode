package com.ulticode.app.userprofile;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
class ProfileReceiptTestConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
