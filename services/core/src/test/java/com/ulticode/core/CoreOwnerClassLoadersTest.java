package com.ulticode.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class CoreOwnerClassLoadersTest {

    @Test
    void jarArtifactNameSupportsReactorDependencyStyleUrls() throws Exception {
        assertThat(jarArtifactName("file:/workspace/backend-auth-1.2.3.jar"))
                .isEqualTo("backend-auth");
        assertThat(jarArtifactName("file:/workspace/backend-notification-0.0.1-SNAPSHOT.jar"))
                .isEqualTo("backend-notification-0.0.1-SNAPSHOT");
    }

    @Test
    void jarArtifactNameSupportsBootRepackagedAppJarNames() throws Exception {
        assertThat(jarArtifactName("file:/workspace/auth-app-exec.jar"))
                .isEqualTo("auth-app-exec");
        assertThat(jarArtifactName("jar:file:/workspace/backend-app-exec.jar!/BOOT-INF/lib/backend-app-1.2.3.jar"))
                .isEqualTo("backend-app");
    }

    @Test
    void jarArtifactNameIgnoresNonJarPaths() throws Exception {
        assertThat(jarArtifactName("file:/workspace/classes/"))
                .isNull();
        assertThat(jarArtifactName("file:/workspace/application.properties"))
                .isNull();
    }

    private static String jarArtifactName(String urlText) throws Exception {
        Method method = CoreOwnerClassLoaders.class
                .getDeclaredMethod("jarArtifactName", URL.class);
        method.setAccessible(true);
        return (String) method.invoke(null, new URL(urlText));
    }
}
