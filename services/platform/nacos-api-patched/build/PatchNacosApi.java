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
public final class PatchNacosApi {
    private static final String PACKAGE = "com/alibaba/nacos/api/grpc/auto/";
    private static final String UPSTREAM_METADATA = "META-INF/maven/com.alibaba.nacos/nacos-api/";
    private static final Set<String> SOURCES = Set.of(
            "Metadata.java", "MetadataOrBuilder.java", "Payload.java",
            "PayloadOrBuilder.java", "NacosGrpcService.java");
    private static final Set<String> REPLACED = Set.of(
            "Metadata.class", "Metadata$1.class", "Metadata$Builder.class",
            "Metadata$HeadersDefaultEntryHolder.class", "MetadataOrBuilder.class",
            "Payload.class", "Payload$1.class", "Payload$Builder.class",
            "PayloadOrBuilder.class", "NacosGrpcService.class");
    private static final String UPSTREAM_SHA256 =
            "8a0485b4c9ec3df44555c947163421df52824faae17906112651873076fdcee0";
    private static final String POM_SHA256 =
            "3c5689beecff65cc8ac15aec73af58bfd9acf1cf15d61be62976fa2cbe2a082d";

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
            case "regenerate" -> regenerate(base, build, Path.of(args[3]));
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
        require(actual.equals(expected), "Only the five audited generated Java sources are allowed");
    }

    private static Map<String, byte[]> upstream(Path build) throws Exception {
        Path jar = build.resolve("upstream/nacos-api-2.5.3.jar");
        checkHash(jar, UPSTREAM_SHA256);
        checkHash(build.resolve("upstream/nacos-api-2.5.3.pom"), POM_SHA256);
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
                name = "META-INF/nacos-upstream/MANIFEST.MF";
            } else if (name.startsWith(UPSTREAM_METADATA)) {
                name = "META-INF/nacos-upstream/" + name.substring(UPSTREAM_METADATA.length());
            }
            write(output, name, entry.getValue());
        }
        require(replaced.equals(REPLACED), "Upstream generated class set differs from audited baseline");
        write(output, "META-INF/nacos-upstream/published-pom.xml",
                Files.readAllBytes(build.resolve("upstream/nacos-api-2.5.3.pom")));
        write(output, "META-INF/nacos-patch/inputs.sha256.properties",
                Files.readAllBytes(base.resolve("build/inputs.sha256.properties")));
        write(output, "META-INF/nacos-patch/protoc.sha256.properties",
                Files.readAllBytes(base.resolve("build/protoc.sha256.properties")));
        // Include the exact schema and imported descriptor licenses with provenance.
        try (var paths = Files.walk(base.resolve("src/main/proto"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                write(output, "META-INF/nacos-patch/proto/"
                        + base.resolve("src/main/proto").relativize(path).toString().replace('\\', '/'),
                        Files.readAllBytes(path));
            }
        }
        System.out.println("Verified upstream SHA-256; preserving all classes except 10 regenerated classes");
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
                require(!Arrays.equals(entry.getValue(), patched.get(name)), "Unpatched generated class: " + name);
                continue;
            }
            if (name.equals("META-INF/MANIFEST.MF")) {
                name = "META-INF/nacos-upstream/MANIFEST.MF";
            } else if (name.startsWith(UPSTREAM_METADATA)) {
                name = "META-INF/nacos-upstream/" + name.substring(UPSTREAM_METADATA.length());
            }
            require(Arrays.equals(entry.getValue(), patched.get(name)), "Changed or missing upstream entry: " + name);
        }
        require(patched.keySet().stream().noneMatch(name -> name.startsWith(UPSTREAM_METADATA)),
                "Artifact must not masquerade as the upstream Maven artifact");
        require(patched.containsKey("META-INF/maven/com.ulticode/nacos-api-patched/pom.properties"),
                "Missing project-owned Maven identity");
        require(patched.containsKey("META-INF/nacos-patch/bom.json"), "Missing upstream SBOM pedigree");
        try (var paths = Files.walk(base.resolve("src/main/resources"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = base.resolve("src/main/resources").relativize(path).toString().replace('\\', '/');
                require(Arrays.equals(Files.readAllBytes(path), patched.get(name)),
                        "Changed or missing audited artifact resource: " + name);
            }
        }
        System.out.println("Verified packaged jar: exactly 10 replaced classes; all other upstream content preserved");
    }

    private static void regenerate(Path base, Path build, Path compiler) throws Exception {
        Properties tools = properties(base.resolve("build/protoc.sha256.properties"));
        String hash = sha256(Files.readAllBytes(compiler));
        require(tools.containsValue(hash), "Unrecognized protoc binary; expected pinned official protoc 3.25.6");
        require(compiler.toFile().setExecutable(true) || Files.isExecutable(compiler),
                "Cannot execute verified protoc binary");
        Path generated = build.resolve("regenerated");
        remove(generated);
        Files.createDirectories(generated);
        Process process = new ProcessBuilder(compiler.toAbsolutePath().toString(),
                "--proto_path=" + base.resolve("src/main/proto"),
                "--java_out=" + generated, "nacos_grpc_service.proto")
                .directory(base.toFile()).inheritIO().start();
        require(process.waitFor() == 0, "protoc failed");
        Map<String, byte[]> actual = new LinkedHashMap<>();
        try (var paths = Files.walk(generated)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                actual.put(generated.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        require(actual.size() == SOURCES.size(), "protoc produced an unexpected number of sources");
        for (String name : SOURCES) {
            require(Arrays.equals(Files.readAllBytes(base.resolve("src/main/java/" + PACKAGE + name)),
                    actual.get(PACKAGE + name)), "Generated source differs: " + name);
        }
        System.out.println("Verified byte-identical regeneration with official protoc 3.25.6");
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
