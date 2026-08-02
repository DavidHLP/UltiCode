package com.ulticode.app.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Explicit {@link MapperScan} for all relocated family mapper packages.
 *
 * <p>Profile-gated to activate only outside the test profile. The shell
 * smoke test ({@code BackendAppApplicationTest}) uses a test profile that
 * excludes {@code MybatisAutoConfiguration}; this scan would create
 * mapper factory beans that require a {@code SqlSessionFactory} which
 * is absent in that profile.
 *
 * <p>P7-RELOCATE-SOLUTION-001: added when the solution family relocated
 * from backend-legacy to backend-app.
 */
@Configuration
@Profile("!test")
@MapperScan({
        "com.ulticode.modules.follow.mapper",
        "com.ulticode.modules.bookmark.mapper",
        "com.ulticode.modules.solution.mapper",
        "com.ulticode.modules.forum.mapper",
        "com.ulticode.modules.problem.mapper",
        "com.ulticode.app.userprofile.mapper",
        "com.ulticode.app.i18n.mapper",
        "com.ulticode.app.idempotency.mapper"
})
public class MapperScanConfig {
}
