package com.ulticode.common.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DrainGate SIGTERM integration")
class DrainGateSignalIT {

    @Test
    @DisplayName("SIGTERM starts drain and releases admitted work")
    void sigtermStartsDrain() throws Exception {
        Path marker = Files.createTempFile("ulticode-drain-signal-", ".txt");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                DrainGateSignalHarness.class.getName(),
                marker.toString())
                .redirectErrorStream(true)
                .start();
        try {
            try (BufferedReader output = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                assertThat(output.readLine()).isEqualTo("READY");
            }
            process.destroy();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(Files.readString(marker))
                    .isEqualTo("draining=true inFlight=1 drained=true");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(marker);
        }
    }
}
