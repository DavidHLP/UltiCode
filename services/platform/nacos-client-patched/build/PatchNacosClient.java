import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Build-only JDK 17 tool; never packaged or placed on the application classpath. */
public final class PatchNacosClient {
    private static final String PACKAGE = "com/alibaba/nacos/client/config/impl/";
    private static final String UPSTREAM_METADATA = "META-INF/maven/com.alibaba.nacos/nacos-client/";
    private static final Set<String> SOURCES = Set.of("ClientWorker.java");
    private static final Set<String> REPLACED = Set.of(
            "ClientWorker.class", "ClientWorker$ConfigRpcTransportClient.class",
            "ClientWorker$ConfigRpcTransportClient$1.class", "ClientWorker$ConfigRpcTransportClient$2.class",
            "ClientWorker$ConfigRpcTransportClient$3.class");
    private static final String UPSTREAM_SHA256 =
            "a991843c4d991fc68d4acb82c3bd9a43fe1deee3a76f1bd66260d81d08d03fa6";
    private static final String POM_SHA256 =
            "09064d5e849eac2c6e1f210670afca20645cb52c04efa45a512fc7220a8e463f";

    public static void main(String[] args) throws Exception {
        Path base = Path.of(args[1]).toAbsolutePath().normalize();
        Path build = Path.of(args[2]).toAbsolutePath().normalize();
        if (!build.startsWith(base) || build.equals(base)) {
            throw new IOException("Build directory must be inside this module: " + build);
        }
        verifyInputs(base);
        switch (args[0]) {
            case "prepare" -> prepare(base, build);
            case "verify" -> verify(base, build, Path.of(args[3]));
            default -> throw new IllegalArgumentException("Unknown operation: " + args[0]);
        }
    }

    private static void verifyInputs(Path base) throws Exception {
        Properties hashes = properties(base.resolve("build/inputs.sha256.properties"));
        for (String file : hashes.stringPropertyNames()) {
            checkHash(base.resolve(file), hashes.getProperty(file));
        }
        Set<String> actual = new TreeSet<>();
        try (var paths = Files.walk(base.resolve("src/main/java"))) {
            paths.filter(Files::isRegularFile).forEach(path -> actual.add(
                    base.resolve("src/main/java").relativize(path).toString().replace('\\', '/')));
        }
        Set<String> expected = new TreeSet<>();
        SOURCES.forEach(name -> expected.add(PACKAGE + name));
        require(actual.equals(expected), "Only the audited ClientWorker source is allowed");
    }

    private static Map<String, byte[]> upstream(Path build) throws Exception {
        Path jar = build.resolve("upstream/nacos-client-2.5.3-pure.jar");
        checkHash(jar, UPSTREAM_SHA256);
        checkHash(build.resolve("upstream/nacos-client-2.5.3.pom"), POM_SHA256);
        return entries(jar);
    }

    private static void prepare(Path base, Path build) throws Exception {
        Map<String, byte[]> original = upstream(build);
        Path output = build.resolve("classes");
        // A non-clean build must not keep stale classes from a previous patch revision.
        remove(output);
        Set<String> replaced = new TreeSet<>();
        for (var entry : original.entrySet()) {
            String name = entry.getKey();
            if (isReplaced(name)) {
                replaced.add(name.substring(PACKAGE.length()));
                continue;
            }
            if (name.equals("META-INF/MANIFEST.MF")) {
                name = "META-INF/nacos-client-upstream/MANIFEST.MF";
            } else if (name.startsWith(UPSTREAM_METADATA)) {
                name = "META-INF/nacos-client-upstream/" + name.substring(UPSTREAM_METADATA.length());
            }
            write(output, name, entry.getValue());
        }
        require(replaced.equals(REPLACED), "Upstream ClientWorker class set differs from audited baseline");
        write(output, "META-INF/nacos-client-upstream/published-pom.xml",
                Files.readAllBytes(build.resolve("upstream/nacos-client-2.5.3.pom")));
        write(output, "META-INF/nacos-client-patch/inputs.sha256.properties",
                Files.readAllBytes(base.resolve("build/inputs.sha256.properties")));
        write(output, "META-INF/nacos-client-patch/ClientWorker.upstream.java",
                Files.readAllBytes(base.resolve("build/upstream/ClientWorker.java")));
        write(output, "META-INF/nacos-client-patch/listener-lifecycle.patch",
                Files.readAllBytes(base.resolve("build/listener-lifecycle.patch")));
        System.out.println("Verified upstream SHA-256; preserving all classes except the five ClientWorker family classes");
    }

    private static void verify(Path base, Path build, Path patchedJar) throws Exception {
        Map<String, byte[]> original = upstream(build);
        Map<String, byte[]> patched = entries(patchedJar);
        Set<String> oldClasses = new TreeSet<>();
        Set<String> newClasses = new TreeSet<>();
        original.keySet().stream().filter(name -> name.endsWith(".class")).forEach(oldClasses::add);
        patched.keySet().stream().filter(name -> name.endsWith(".class")).forEach(newClasses::add);
        require(oldClasses.equals(newClasses), "Patched artifact must have exactly the upstream class names");
        for (var entry : original.entrySet()) {
            String name = entry.getKey();
            if (isReplaced(name)) {
                require(!Arrays.equals(entry.getValue(), patched.get(name)), "Unpatched client class: " + name);
                continue;
            }
            if (name.equals("META-INF/MANIFEST.MF")) {
                name = "META-INF/nacos-client-upstream/MANIFEST.MF";
            } else if (name.startsWith(UPSTREAM_METADATA)) {
                name = "META-INF/nacos-client-upstream/" + name.substring(UPSTREAM_METADATA.length());
            }
            require(Arrays.equals(entry.getValue(), patched.get(name)), "Changed or missing upstream entry: " + name);
        }
        require(patched.keySet().stream().noneMatch(name -> name.startsWith(UPSTREAM_METADATA)),
                "Artifact must not masquerade as the upstream Maven artifact");
        require(patched.containsKey("META-INF/maven/com.ulticode/nacos-client-patched/pom.properties"),
                "Missing project-owned Maven identity");
        Properties identity = new Properties();
        identity.load(new java.io.ByteArrayInputStream(
                patched.get("META-INF/maven/com.ulticode/nacos-client-patched/pom.properties")));
        require("com.ulticode".equals(identity.getProperty("groupId"))
                        && "nacos-client-patched".equals(identity.getProperty("artifactId"))
                        && "2.5.3-ulticode-2".equals(identity.getProperty("version")),
                "Incorrect owned Maven identity");
        require(patched.containsKey("META-INF/nacos-client-patch/bom.json"), "Missing upstream SBOM pedigree");
        try (var paths = Files.walk(base.resolve("src/main/resources"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = base.resolve("src/main/resources").relativize(path).toString().replace('\\', '/');
                require(Arrays.equals(Files.readAllBytes(path), patched.get(name)),
                        "Changed or missing audited artifact resource: " + name);
            }
        }
        for (var entry : Map.of(
                "META-INF/nacos-client-patch/inputs.sha256.properties", base.resolve("build/inputs.sha256.properties"),
                "META-INF/nacos-client-patch/ClientWorker.upstream.java", base.resolve("build/upstream/ClientWorker.java"),
                "META-INF/nacos-client-patch/listener-lifecycle.patch", base.resolve("build/listener-lifecycle.patch"),
                "META-INF/nacos-client-upstream/published-pom.xml", build.resolve("upstream/nacos-client-2.5.3.pom")).entrySet()) {
            require(Arrays.equals(Files.readAllBytes(entry.getValue()), patched.get(entry.getKey())),
                    "Changed or missing copied provenance entry: " + entry.getKey());
        }
        System.out.println("Verified packaged jar: exactly five replaced ClientWorker family classes; all other upstream content preserved");
    }

    private static boolean isReplaced(String name) {
        return name.startsWith(PACKAGE) && REPLACED.contains(name.substring(PACKAGE.length()));
    }

    private static Map<String, byte[]> entries(Path jar) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    try (InputStream stream = zip.getInputStream(entry)) {
                        require(result.put(entry.getName(), stream.readAllBytes()) == null,
                                "Duplicate jar entry: " + entry.getName());
                    }
                }
            }
        }
        return result;
    }

    private static void write(Path root, String name, byte[] bytes) throws IOException {
        Path target = root.resolve(name).normalize();
        require(target.startsWith(root), "Invalid upstream entry: " + name);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    private static void remove(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static Properties properties(Path path) throws IOException {
        Properties result = new Properties();
        try (InputStream stream = Files.newInputStream(path)) {
            result.load(stream);
        }
        return result;
    }

    private static void checkHash(Path file, String expected) throws Exception {
        require(sha256(Files.readAllBytes(file)).equals(expected), "SHA-256 mismatch: " + file);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }
}
