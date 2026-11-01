package com.ulticode.nacoscompat;

import static org.junit.jupiter.api.Assertions.*;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.google.protobuf.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;

class ApiCompatibilityTest {
    private static final Set<String> FAMILIES = Set.of("Metadata", "MetadataOrBuilder", "Payload", "PayloadOrBuilder", "NacosGrpcService");
    private static boolean replaced(String name) {
        if (!name.startsWith("com/alibaba/nacos/api/grpc/auto/") || !name.endsWith(".class")) return false;
        String simple = name.substring(name.lastIndexOf('/') + 1).split("\\$")[0].replace(".class", "");
        return FAMILIES.contains(simple);
    }
    @Test void strictRuntimeAndNormalReflectionRemainCompatible() throws Exception {
        assertTrue(Boolean.getBoolean("com.google.protobuf.error_on_unsafe_pre22_gencode"));
        Metadata metadata = Metadata.newBuilder().putHeaders("key", "value").build();
        Descriptors.FieldDescriptor headers = Metadata.getDescriptor().findFieldByName("headers");
        assertEquals(metadata, Metadata.newBuilder().setField(headers, metadata.getField(headers)).build());
        Payload payload = Payload.newBuilder().setMetadata(metadata).build();
        assertEquals(payload, Payload.parseFrom(payload.toByteArray()));
        assertEquals(GeneratedMessageV3.class, Payload.class.getSuperclass());
    }
    @Test void publishedDescriptorIsPreserved() throws Exception {
        Descriptors.FileDescriptor current = com.alibaba.nacos.api.grpc.auto.NacosGrpcService.getDescriptor();
        DescriptorProtos.FileDescriptorSet.Builder set = DescriptorProtos.FileDescriptorSet.newBuilder();
        for (Descriptors.FileDescriptor dependency : current.getDependencies()) set.addFile(dependency.toProto());
        set.addFile(current.toProto());
        try (InputStream expected = getClass().getResourceAsStream("/nacos-published.desc")) {
            assertNotNull(expected); assertArrayEquals(expected.readAllBytes(), set.build().toByteArray());
        }
    }
    @Test void repeatedMetadataUsesBoundedFixtureAndRetainsMergeSemantics() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        for (int i = 0; i < 1600; i++) out.writeMessage(2, Metadata.newBuilder().putHeaders("key" + i, "v").build());
        out.flush();
        assertEquals(1600, Payload.parseFrom(bytes.toByteArray()).getMetadata().getHeadersCount());
    }
    @Test void malformedWireInputsAreRejected() {
        for (byte[] input : new byte[][]{ {18,127,1}, {18,3,26,1,(byte)255}, {(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,0}})
            assertThrows(InvalidProtocolBufferException.class, () -> Payload.parseFrom(input));
        byte[] groups = new byte[240];
        Arrays.fill(groups, 0, 120, (byte)27); Arrays.fill(groups, 120, 240, (byte)28);
        assertThrows(InvalidProtocolBufferException.class, () -> Payload.parseFrom(groups));
    }
    @Test void everyNonMessageClassIsUnchangedAndNoUnexpectedClassIsPackaged() throws Exception {
        Path output = Path.of("target/classes");
        Set<String> expected = new HashSet<>();
        try (ZipFile upstream = new ZipFile("target/upstream/nacos-api.jar")) {
            Enumeration<? extends ZipEntry> entries = upstream.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                expected.add(entry.getName());
                if (!replaced(entry.getName()))
                    assertArrayEquals(upstream.getInputStream(entry).readAllBytes(), Files.readAllBytes(output.resolve(entry.getName())), entry.getName());
            }
        }
        Set<String> actual = new HashSet<>();
        try (var files = Files.walk(output)) {
            files.filter(p -> p.toString().endsWith(".class")).forEach(p -> actual.add(output.relativize(p).toString().replace(File.separatorChar, '/')));
        }
        assertEquals(expected, actual);
        assertEquals(1, Collections.list(getClass().getClassLoader().getResources("com/alibaba/nacos/api/NacosFactory.class")).size());
    }
}
