package com.ulticode.modules.submission.sandbox.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SandboxExecutorImpl#resolveSeccompProfile(String, Path)}.
 *
 * <p>The seccomp profile path is intentionally a <b>relative</b>,
 * repository-root-relative value (no absolute paths in config). The
 * resolver re-roots it by walking up from the JVM working directory so a
 * single config value resolves identically under every launch mode:
 * <ul>
 *   <li>{@code mvn spring-boot:run} forks a JVM whose {@code user.dir} is
 *       the module directory (e.g. {@code services/app/app-web});</li>
 *   <li>a packaged jar uses wherever it was launched from;</li>
 *   <li>PM2 sets {@code cwd = services/}.</li>
 * </ul>
 * A {@code ../}-prefixed path previously pointed at different (non-existent)
 * files per launch mode and silently broke every judge call with docker
 * exit 125, masked as a generic "Runtime Error". These tests pin the
 * launch-mode-independent resolution.
 */
@DisplayName("SandboxExecutorImpl seccomp profile resolution")
class SandboxExecutorImplSeccompResolutionTest {

    @TempDir
    Path repoRoot;

    private Path profileInRepo() throws IOException {
        Path profile = repoRoot.resolve("docker/sandbox/seccomp-profile.json");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, "{}");
        return profile;
    }

    @Test
    @DisplayName("absolute configured path is returned verbatim (no re-root)")
    void absolutePath_returnedVerbatim() throws IOException {
        Path profile = profileInRepo();
        assertThat(SandboxExecutorImpl.resolveSeccompProfile(
                profile.toString(), repoRoot.resolve("anywhere")))
                .isEqualTo(profile.toString());
    }

    @Test
    @DisplayName("relative path resolves directly when user.dir IS the repo root (fast path)")
    void relativePath_userDirAtRepoRoot_fastPath() throws IOException {
        Path profile = profileInRepo();
        assertThat(SandboxExecutorImpl.resolveSeccompProfile(
                "docker/sandbox/seccomp-profile.json", repoRoot))
                .isEqualTo(profile.toString());
    }

    @Test
    @DisplayName("relative path is re-rooted when user.dir is a deep module dir (mvn spring-boot:run)")
    void relativePath_userDirAtModuleDir_reRooted() throws IOException {
        Path profile = profileInRepo();
        // Simulates spring-boot:run: user.dir = services/app/app-web while
        // the profile lives at the repository root.
        Path moduleDir = repoRoot.resolve("services/app/app-web");
        Files.createDirectories(moduleDir);
        assertThat(SandboxExecutorImpl.resolveSeccompProfile(
                "docker/sandbox/seccomp-profile.json", moduleDir))
                .isEqualTo(profile.toString());
    }

    @Test
    @DisplayName("relative path is re-rooted when user.dir = services/ (PM2 cwd)")
    void relativePath_userDirAtServices_reRooted() throws IOException {
        Path profile = profileInRepo();
        Path servicesDir = repoRoot.resolve("services");
        Files.createDirectories(servicesDir);
        assertThat(SandboxExecutorImpl.resolveSeccompProfile(
                "docker/sandbox/seccomp-profile.json", servicesDir))
                .isEqualTo(profile.toString());
    }

    @Test
    @DisplayName("a stale ../ prefix no longer misleads: re-root still finds the profile "
            + "because the walk-up re-roots the bare tail of the path")
    void staleDotDotPrefix_stillResolvesViaReRoot() throws IOException {
        Path profile = profileInRepo();
        Path moduleDir = repoRoot.resolve("services/app/app-web");
        Files.createDirectories(moduleDir);
        // An older .env wrote "../../docker/sandbox/seccomp-profile.json". That
        // exact string resolves to a non-existent file under moduleDir, but
        // the walk-up re-roots the same relative path at each ancestor until
        // the repo root matches.
        String result = SandboxExecutorImpl.resolveSeccompProfile(
                "../../docker/sandbox/seccomp-profile.json", moduleDir);
        assertThat(Path.of(result)).isEqualTo(profile);
    }

    @Test
    @DisplayName("when no ancestor contains the target, the user.dir-relative path is returned "
            + "so docker surfaces a clear 'no such file' error (never silently weakens isolation)")
    void notFound_returnsUserDirRelative_forClearDockerError() {
        Path elsewhere = repoRoot.resolve("elsewhere");
        String result = SandboxExecutorImpl.resolveSeccompProfile(
                "docker/sandbox/seccomp-profile.json", elsewhere);
        // No profile created under repoRoot this time → must fall back rather
        // than return empty / null / a guessed location.
        assertThat(result).isEqualTo(elsewhere.resolve(
                "docker/sandbox/seccomp-profile.json").toString());
    }
}
