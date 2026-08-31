package com.ulticode.modules.submission.sandbox.executor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.modules.submission.sandbox.SandboxOutcomeClassifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DockerProcessRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void timeoutRemovesContainerThroughCidfile() throws Exception {
        Path marker = tempDir.resolve("cleanup.marker");
        Path cidFile = tempDir.resolve("container.cid");
        Path fakeDocker = writeFakeDocker(marker);

        DFormRunOutcome outcome = new DockerProcessRunner(new SandboxOutcomeClassifier()).run(
                List.of(fakeDocker.toString(), "--cidfile", cidFile.toString(),
                        "--name", "ulticode-sandbox-test"),
                1,
                "test");

        assertThat(outcome.timedOut()).isTrue();
        assertThat(Files.readString(marker)).isEqualTo("fake-container");
    }

    @Test
    void interruptionAlsoRemovesContainerThroughNameFallback() throws Exception {
        Path marker = tempDir.resolve("interrupt-cleanup.marker");
        Path cidFile = tempDir.resolve("interrupt-container.cid");
        Path fakeDocker = writeFakeDocker(marker);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                new DockerProcessRunner(new SandboxOutcomeClassifier()).run(
                        List.of(fakeDocker.toString(), "--cidfile", cidFile.toString(),
                                "--name", "ulticode-sandbox-interrupted"),
                        10,
                        "interrupt");
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        runner.start();
        for (int attempt = 0; attempt < 100 && !Files.exists(cidFile); attempt++) {
            Thread.sleep(10);
        }
        runner.interrupt();
        runner.join(5000);

        assertThat(runner.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(InterruptedException.class);
        assertThat(Files.readString(marker)).isEqualTo("fake-container");
    }

    private Path writeFakeDocker(Path marker) throws IOException {
        Path fakeDocker = tempDir.resolve(marker.getFileName() + ".sh");
        Files.writeString(fakeDocker, "#!/bin/sh\n"
                + "if [ \"$1\" = \"rm\" ]; then\n"
                + "  printf '%s' \"$3\" > \"" + marker + "\"\n"
                + "  exit 0\n"
                + "fi\n"
                + "cidfile=\"\"\n"
                + "next=0\n"
                + "for arg in \"$@\"; do\n"
                + "  if [ \"$next\" = \"1\" ]; then cidfile=\"$arg\"; next=0; fi\n"
                + "  if [ \"$arg\" = \"--cidfile\" ]; then next=1; fi\n"
                + "done\n"
                + "printf '%s' fake-container > \"$cidfile\"\n"
                + "sleep 5\n", StandardCharsets.UTF_8);
        assertThat(fakeDocker.toFile().setExecutable(true)).isTrue();
        return fakeDocker;
    }
}
