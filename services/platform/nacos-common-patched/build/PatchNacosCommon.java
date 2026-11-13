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
public final class PatchNacosCommon {
    private static final String PACKAGE = "com/alibaba/nacos/common/remote/client/";
    private static final String UPSTREAM_METADATA = "META-INF/maven/com.alibaba.nacos/nacos-common/";
    private static final Set<String> SOURCES = Set.of("RpcClient.java", "grpc/GrpcClient.java");
    private static final Set<String> REPLACED = Set.of(
            "RpcClient.class", "RpcClient$ReconnectContext.class", "RpcClient$ServerInfo.class",
            "RpcClient$ConnectResetRequestHandler.class", "RpcClient$ConnectionEvent.class",
            "grpc/GrpcClient.class", "grpc/GrpcClient$1.class", "grpc/GrpcClient$RecAbilityContext.class",
            "grpc/GrpcClient$SetupRequestHandler.class");
    private static final String UPSTREAM_SHA256 =
            "c943d01cc2a6791fabc2f862a8456501b6ffc934d5a915fd9b413fddd08e1d46";
    private static final String POM_SHA256 =
            "985ce3b5620dc860f6ac959392cda22ecf1048fd57524d75104f2c5e4a47a72b";

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
        Set<String> locked = new TreeSet<>(hashes.stringPropertyNames());
        Set<String> inputs = new TreeSet<>();
        for (String root : Set.of("src/main/java", "src/main/resources", "build")) {
            try (var paths = Files.walk(base.resolve(root))) {
                paths.filter(Files::isRegularFile).forEach(path -> inputs.add(base.relativize(path).toString().replace('\\', '/')));
            }
        }
        inputs.remove("build/inputs.sha256.properties");
        inputs.add("pom.xml");
        require(inputs.equals(locked), "Input lock must cover exactly all build, source and resource inputs");
        Set<String> permittedInputs = new TreeSet<>(Set.of("pom.xml", "build/PatchNacosCommon.java",
                "build/rpc-lifecycle.patch", "build/upstream/RpcClient.java", "build/upstream/GrpcClient.java",
                "build/upstream/source-metadata.json", "src/main/resources/META-INF/LICENSE",
                "src/main/resources/META-INF/NOTICE", "src/main/resources/META-INF/nacos-common-patch/UPSTREAM-NOTICE",
                "src/main/resources/META-INF/nacos-common-patch/bom.json",
                "src/main/resources/META-INF/nacos-common-patch/provenance.json"));
        SOURCES.forEach(name -> permittedInputs.add("src/main/java/" + PACKAGE + name));
        require(inputs.equals(permittedInputs), "Unrelated build inputs or resources are not allowed");
        for (String source : SOURCES) {
            require(!Arrays.equals(Files.readAllBytes(base.resolve("src/main/java/" + PACKAGE + source)),
                    Files.readAllBytes(base.resolve("build/upstream/" + Path.of(source).getFileName()))),
                    "Lifecycle source is still unpatched: " + source);
        }
        verifyPatch(base);
        Set<String> actual = new TreeSet<>();
        try (var paths = Files.walk(base.resolve("src/main/java"))) {
            paths.filter(Files::isRegularFile).forEach(path -> actual.add(
                    base.resolve("src/main/java").relativize(path).toString().replace('\\', '/')));
        }
        Set<String> expected = new TreeSet<>();
        SOURCES.forEach(name -> expected.add(PACKAGE + name));
        require(actual.equals(expected), "Only the two audited RPC lifecycle sources are allowed");
    }

    private static void verifyPatch(Path base) throws Exception {
        // The embedded unified diff must reconstruct both locked patched source files exactly.
        java.util.List<String> lines = Files.readAllLines(base.resolve("build/rpc-lifecycle.patch"));
        int index = 0;
        Set<String> seen = new TreeSet<>();
        while (index < lines.size()) {
            String header = lines.get(index++);
            require(header.startsWith("--- a/"), "Invalid patch source header");
            String source = header.substring(6);
            require(SOURCES.contains(source) && seen.add(source), "Unexpected patch source: " + source);
            require(index < lines.size() && lines.get(index++).equals("+++ b/" + source), "Invalid patch destination");
            java.util.List<String> original = Files.readAllLines(base.resolve("build/upstream/" + Path.of(source).getFileName()));
            java.util.List<String> result = new java.util.ArrayList<>();
            int cursor = 0;
            while (index < lines.size() && !lines.get(index).startsWith("--- a/")) {
                var matcher = java.util.regex.Pattern.compile("@@ -(\\d+)(?:,(\\d+))? [+](\\d+)(?:,(\\d+))? @@.*").matcher(lines.get(index++));
                require(matcher.matches(), "Invalid patch hunk");
                int start = Integer.parseInt(matcher.group(1)) - 1;
                require(start >= cursor && start <= original.size(), "Invalid patch offset");
                result.addAll(original.subList(cursor, start)); cursor = start;
                while (index < lines.size() && !lines.get(index).startsWith("@@ ") && !lines.get(index).startsWith("--- a/")) {
                    String line = lines.get(index++);
                    require(!line.isEmpty(), "Invalid patch line");
                    char kind = line.charAt(0); String content = line.substring(1);
                    require(kind == ' ' || kind == '-' || kind == '+', "Invalid patch operation");
                    if (kind != '+') {
                        require(cursor < original.size() && original.get(cursor).equals(content), "Patch context mismatch"); cursor++;
                    }
                    if (kind != '-') result.add(content);
                }
            }
            result.addAll(original.subList(cursor, original.size()));
            require((String.join("\n", result) + "\n").equals(Files.readString(base.resolve("src/main/java/" + PACKAGE + source))),
                    "Patch does not reconstruct audited source: " + source);
        }
        require(seen.equals(SOURCES), "Patch must modify both audited sources");
    }

    private static Map<String, byte[]> upstream(Path build) throws Exception {
        Path jar = build.resolve("upstream/nacos-common-2.5.3.jar");
        checkHash(jar, UPSTREAM_SHA256);
        checkHash(build.resolve("upstream/nacos-common-2.5.3.pom"), POM_SHA256);
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
                name = "META-INF/nacos-common-upstream/MANIFEST.MF";
            } else if (name.startsWith(UPSTREAM_METADATA)) {
                name = "META-INF/nacos-common-upstream/" + name.substring(UPSTREAM_METADATA.length());
            }
            write(output, name, entry.getValue());
        }
        require(replaced.equals(REPLACED), "Upstream RPC lifecycle class set differs from audited baseline");
        write(output, "META-INF/nacos-common-upstream/published-pom.xml",
                Files.readAllBytes(build.resolve("upstream/nacos-common-2.5.3.pom")));
        write(output, "META-INF/nacos-common-patch/inputs.sha256.properties",
                Files.readAllBytes(base.resolve("build/inputs.sha256.properties")));
        write(output, "META-INF/nacos-common-patch/RpcClient.upstream.java",
                Files.readAllBytes(base.resolve("build/upstream/RpcClient.java")));
        write(output, "META-INF/nacos-common-patch/GrpcClient.upstream.java",
                Files.readAllBytes(base.resolve("build/upstream/GrpcClient.java")));
        write(output, "META-INF/nacos-common-patch/rpc-lifecycle.patch",
                Files.readAllBytes(base.resolve("build/rpc-lifecycle.patch")));
        System.out.println("Verified upstream SHA-256; preserving all classes except the two RPC lifecycle class families");
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
                if (name.equals(PACKAGE + "RpcClient.class") || name.equals(PACKAGE + "grpc/GrpcClient.class")) {
                    require(!Arrays.equals(entry.getValue(), patched.get(name)), "Unpatched lifecycle class: " + name);
                }
                continue;
            }
            if (name.equals("META-INF/MANIFEST.MF")) {
                name = "META-INF/nacos-common-upstream/MANIFEST.MF";
            } else if (name.startsWith(UPSTREAM_METADATA)) {
                name = "META-INF/nacos-common-upstream/" + name.substring(UPSTREAM_METADATA.length());
            }
            require(Arrays.equals(entry.getValue(), patched.get(name)), "Changed or missing upstream entry: " + name);
        }
        require(patched.keySet().stream().noneMatch(name -> name.startsWith(UPSTREAM_METADATA)),
                "Artifact must not masquerade as the upstream Maven artifact");
        require(patched.containsKey("META-INF/maven/com.ulticode/nacos-common-patched/pom.properties"),
                "Missing project-owned Maven identity");
        Properties identity = new Properties();
        identity.load(new java.io.ByteArrayInputStream(
                patched.get("META-INF/maven/com.ulticode/nacos-common-patched/pom.properties")));
        require("com.ulticode".equals(identity.getProperty("groupId"))
                        && "nacos-common-patched".equals(identity.getProperty("artifactId"))
                        && "2.5.3-ulticode-1".equals(identity.getProperty("version")),
                "Incorrect owned Maven identity");
        require(patched.containsKey("META-INF/nacos-common-patch/bom.json"), "Missing upstream SBOM pedigree");
        try (var paths = Files.walk(base.resolve("src/main/resources"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = base.resolve("src/main/resources").relativize(path).toString().replace('\\', '/');
                require(Arrays.equals(Files.readAllBytes(path), patched.get(name)),
                        "Changed or missing audited artifact resource: " + name);
            }
        }
        for (var entry : Map.of(
                "META-INF/nacos-common-patch/inputs.sha256.properties", base.resolve("build/inputs.sha256.properties"),
                "META-INF/nacos-common-patch/RpcClient.upstream.java", base.resolve("build/upstream/RpcClient.java"),
                "META-INF/nacos-common-patch/GrpcClient.upstream.java", base.resolve("build/upstream/GrpcClient.java"),
                "META-INF/nacos-common-patch/rpc-lifecycle.patch", base.resolve("build/rpc-lifecycle.patch"),
                "META-INF/nacos-common-upstream/published-pom.xml", build.resolve("upstream/nacos-common-2.5.3.pom")).entrySet()) {
            require(Arrays.equals(Files.readAllBytes(entry.getValue()), patched.get(entry.getKey())),
                    "Changed or missing copied provenance entry: " + entry.getKey());
        }
        Set<String> expectedEntries = new TreeSet<>();
        for (String name : original.keySet()) {
            if (name.equals("META-INF/MANIFEST.MF")) name = "META-INF/nacos-common-upstream/MANIFEST.MF";
            else if (name.startsWith(UPSTREAM_METADATA)) name = "META-INF/nacos-common-upstream/" + name.substring(UPSTREAM_METADATA.length());
            expectedEntries.add(name);
        }
        expectedEntries.addAll(Set.of("META-INF/MANIFEST.MF",
                "META-INF/maven/com.ulticode/nacos-common-patched/pom.xml",
                "META-INF/maven/com.ulticode/nacos-common-patched/pom.properties",
                "META-INF/nacos-common-upstream/published-pom.xml",
                "META-INF/nacos-common-patch/inputs.sha256.properties",
                "META-INF/nacos-common-patch/RpcClient.upstream.java",
                "META-INF/nacos-common-patch/GrpcClient.upstream.java",
                "META-INF/nacos-common-patch/rpc-lifecycle.patch"));
        try (var paths = Files.walk(base.resolve("src/main/resources"))) {
            paths.filter(Files::isRegularFile).forEach(path -> expectedEntries.add(
                    base.resolve("src/main/resources").relativize(path).toString().replace('\\', '/')));
        }
        require(expectedEntries.equals(patched.keySet()), "Unexpected or missing artifact entries");
        for (String name : REPLACED) {
            String binaryName = (PACKAGE + name.substring(0, name.length() - 6)).replace('/', '.');
            require(descriptors(build.resolve("upstream/nacos-common-2.5.3.jar"), binaryName)
                    .equals(descriptors(patchedJar, binaryName)), "Changed public/protected descriptors: " + binaryName);
        }
        System.out.println("Verified packaged jar: exact upstream class set, two RPC lifecycle class families; all other upstream content preserved");
    }

    private static String descriptors(Path jar, String name) throws Exception {
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javap").toString(),
                "-protected", "-s", "-classpath", jar.toString(), name).redirectErrorStream(true).start();
        String text = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        require(process.waitFor() == 0, "javap failed: " + text);
        return text.replace("synchronized ", "");
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
