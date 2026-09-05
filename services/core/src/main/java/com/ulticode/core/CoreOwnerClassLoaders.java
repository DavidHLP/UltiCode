package com.ulticode.core;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds an isolated {@link URLClassLoader} per Owner Module so that
 * {@code @ComponentScan} in one child context cannot discover sibling
 * Owner classes from another Owner's JAR on the shared Core classpath.
 *
 * <p>The Core JAR carries every Owner implementation JAR at compile time
 * (see {@code services/core/pom.xml}). Without isolation, shared base
 * packages such as {@code com.ulticode.common} and
 * {@code com.ulticode.modules.event.inbox} are scanned by multiple children,
 * producing bean-definition conflicts and cross-owner leakage.
 *
 * <p>The isolation scheme resolves JAR URLs from the Core thread context
 * classloader and partitions them into:
 * <ul>
 *   <li><b>Shared platform</b> — contract, common, and framework JARs that
 *       every child needs (backend-common, backend-web-security, backend-api,
 *       backend-integration-inbox, Spring, MyBatis, Hikari, etc.)</li>
 *   <li><b>Owner-specific</b> — the single Owner implementation JAR (e.g.
 *       backend-auth, backend-submission) that only that child may load</li>
 * </ul>
 *
 * <p>Framework/system classes are never included in the per-owner URL list;
 * they resolve from the parent classloader (the Core application classloader).
 */
final class CoreOwnerClassLoaders {

    /** Owner artifact → its implementation JAR name suffix. */
    private static final List<String> OWNER_JARS = List.of(
            "backend-auth",
            "backend-admin",
            "backend-app-web",
            "backend-submission",
            "backend-notification",
            "backend-search");

    /** Maven artifact base names that belong to the shared platform layer. */
    private static final Set<String> SHARED_ARTIFACT_PREFIXES = Set.of(
            "backend-common",
            "backend-observability",
            "backend-web-security",
            "backend-integration-inbox",
            "backend-rpc-resilience",
            "backend-domain-types");

    /**
     * Maven artifact base names that are API-contract JARs — always shared
     * across every child because they carry no implementation classes.
     */
    private static final Set<String> API_ARTIFACTS = Set.of(
            "backend-auth-api",
            "backend-app-api",
            "backend-submission-api",
            "backend-notification-api",
            "backend-judge-api");

    private final List<URL> sharedUrls;

    CoreOwnerClassLoaders() {
        this.sharedUrls = discoverSharedUrls();
    }

    /**
     * Create an isolated classloader for one Owner child context.
     *
     * @param ownerArtifactId the Maven artifactId of the Owner implementation
     *                        (e.g. {@code backend-auth})
     * @return a URLClassLoader whose classpath is shared-platform + that Owner only
     */
    URLClassLoader createOwnerClassLoader(String ownerArtifactId) {
        List<URL> ownerUrls = discoverOwnerUrls(ownerArtifactId);
        List<URL> childUrls = new ArrayList<>(sharedUrls);
        childUrls.addAll(ownerUrls);
        ClassLoader parent = CoreOwnerClassLoaders.class.getClassLoader();
        return new URLClassLoader(
                childUrls.toArray(URL[]::new),
                parent);
    }

    private List<URL> discoverSharedUrls() {
        List<URL> urls = new ArrayList<>();
        collectUrlsFromClasspath(urls, SHARED_ARTIFACT_PREFIXES, API_ARTIFACTS);
        return Collections.unmodifiableList(urls);
    }

    private List<URL> discoverOwnerUrls(String ownerArtifactId) {
        List<URL> urls = new ArrayList<>();
        Set<String> ownerOnly = new HashSet<>(Set.of(ownerArtifactId));
        collectUrlsFromClasspath(urls, ownerOnly, Collections.emptySet());
        return urls;
    }

    /**
     * Walks the thread context classloader's resources, finding JAR files whose
     * Maven artifact name matches any prefix in {@code artifactPrefixes} or
     * exactly matches any name in {@code exactArtifacts}.
     */
    private static void collectUrlsFromClasspath(
            List<URL> out,
            Set<String> artifactPrefixes,
            Set<String> exactArtifacts) {
        Set<ClassLoader> seen = new HashSet<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = CoreOwnerClassLoaders.class.getClassLoader();
        }
        while (cl != null && seen.add(cl)) {
            if (cl instanceof URLClassLoader urlCl) {
                for (URL url : urlCl.getURLs()) {
                    String name = jarArtifactName(url);
                    if (name == null) {
                        continue;
                    }
                    if (exactArtifacts.contains(name)) {
                        out.add(url);
                    } else {
                        for (String prefix : artifactPrefixes) {
                            if (name.startsWith(prefix)) {
                                out.add(url);
                                break;
                            }
                        }
                    }
                }
            }
            cl = cl.getParent();
        }
    }

    /**
     * Extracts the Maven artifact base name from a JAR URL.
     * Handles both normal JARs (e.g.
     * {@code backend-auth-1.0.0.jar}) and Spring Boot repackaged JARs
     * (e.g. {@code auth-app-exec.jar}).
     */
    private static String jarArtifactName(URL url) {
        String path = url.getPath();
        int slash = path.lastIndexOf('/');
        String filename = slash >= 0 ? path.substring(slash + 1) : path;
        if (!filename.endsWith(".jar")) {
            return null;
        }
        String base = filename.substring(0, filename.length() - ".jar".length());
        // Strip version suffix: backend-auth-1.0.0 -> backend-auth
        int versionStart = base.indexOf('-');
        if (versionStart > 0) {
            // Find the version boundary: artifactId-version, version starts
            // with a digit after the last non-digit segment
            String candidate = base;
            while (candidate.lastIndexOf('-') > 0) {
                int lastDash = candidate.lastIndexOf('-');
                String afterDash = candidate.substring(lastDash + 1);
                if (afterDash.isEmpty() || !Character.isDigit(afterDash.charAt(0))) {
                    break;
                }
                candidate = candidate.substring(0, lastDash);
            }
            base = candidate;
        }
        return base;
    }

    /** Returns the list of owner JAR names that this classloaders can isolate. */
    static List<String> ownerArtifactIds() {
        return OWNER_JARS;
    }
}
