package com.ulticode.modules.backup.port.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MysqldumpBackupProcessAdapter} using fake shell
 * scripts instead of real mysqldump/mysql binaries. Mirrors the
 * real-subprocess testing pattern from {@code SandboxForkE2EIT}: a
 * temporary executable shell script stands in for the binary, letting
 * us prove exit code, stdout, timeout, interrupt, and malformed-URL
 * behaviour without a host MySQL install.
 *
 * <p>Coverage maps to {@code P7-ADMIN-BACKUP-RUNTIME-001} acceptance:
 * success, nonzero exit, timeout, interrupt, and malformed datasource
 * URL for both dump and restore paths. The adapter uses
 * {@code backup.process.mysqldump-path} / {@code backup.process.mysql-path}
 * to locate the fake scripts, so production default behaviour is
 * untouched while the test points at controlled executables.
 */
@DisplayName("MysqldumpBackupProcessAdapter (fake-subprocess)")
class MysqldumpBackupProcessAdapterTest {

    private MysqldumpBackupProcessAdapter adapter;

    @TempDir
    Path tempDir;

    private Path dumpFile;
    private Path restoreFile;

    @BeforeEach
    void setUp() {
        adapter = new MysqldumpBackupProcessAdapter();
        ReflectionTestUtils.setField(adapter, "datasourceUrl",
                "jdbc:mysql://testhost:13306/testdb?useSSL=false");
        ReflectionTestUtils.setField(adapter, "datasourceUsername", "testuser");
        ReflectionTestUtils.setField(adapter, "datasourcePassword", "testpass");
        ReflectionTestUtils.setField(adapter, "processTimeoutSeconds", 5);

        dumpFile = tempDir.resolve("dump.sql");
        restoreFile = tempDir.resolve("restore.sql");
    }

    // ------------------------------------------------------------------
    // Fake script helpers
    // ------------------------------------------------------------------

    /**
     * Write a fake executable shell script that acts as mysqldump/mysql.
     *
     * @param name    script filename (e.g. "fake-dump")
     * @param body    shell script body (shebang added automatically)
     * @return absolute path to the executable script
     */
    private Path writeFakeScript(String name, String body) throws IOException {
        Path script = tempDir.resolve(name);
        String content = "#!/bin/sh\n" + body + "\n";
        Files.write(script, content.getBytes());
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(script, perms);
        return script;
    }

    // ------------------------------------------------------------------
    // dump: success
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dump returns true when fake mysqldump exits 0 and creates the file")
    void dumpSucceedsWhenProcessExitsZero() throws IOException {
        Path fakeDump = writeFakeScript("fake-dump-success",
                "cat > /dev/null  # consume stdin\n" +
                "echo '-- fake dump' > \"$DUMPFILE\"\n" +
                "exit 0");
        // The adapter redirects mysqldump stdout to the target file, so
        // the fake script must write to stdout, not to a fixed path.
        // Rewrite: echo to stdout because redirectOutput(targetFile) is set.
        Files.write(fakeDump, ("#!/bin/sh\necho '-- fake dump'\nexit 0\n").getBytes());
        Files.setPosixFilePermissions(fakeDump,
                PosixFilePermissions.fromString("rwxr-xr-x"));

        ReflectionTestUtils.setField(adapter, "mysqldumpPath", fakeDump.toString());

        boolean result = adapter.dump(dumpFile);

        assertTrue(result, "dump must return true when exit 0 and file exists");
        assertTrue(Files.exists(dumpFile), "dump file must be created");
        assertEquals("-- fake dump", Files.readString(dumpFile).trim());
    }

    // ------------------------------------------------------------------
    // dump: nonzero exit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dump returns false when fake mysqldump exits nonzero")
    void dumpFailsWhenProcessExitsNonzero() throws IOException {
        Path fakeDump = writeFakeScript("fake-dump-fail",
                "echo 'connection refused' >&2\nexit 2");

        ReflectionTestUtils.setField(adapter, "mysqldumpPath", fakeDump.toString());

        boolean result = adapter.dump(dumpFile);

        assertFalse(result, "dump must return false on nonzero exit");
    }

    // ------------------------------------------------------------------
    // dump: timeout
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dump returns false when fake mysqldump exceeds the timeout")
    void dumpTimesOutAndDestroysProcess() throws IOException {
        // Sleep longer than the 5s timeout; the adapter must
        // destroyForcibly and return false.
        Path fakeDump = writeFakeScript("fake-dump-timeout",
                "sleep 30\nexit 0");

        ReflectionTestUtils.setField(adapter, "mysqldumpPath", fakeDump.toString());
        ReflectionTestUtils.setField(adapter, "processTimeoutSeconds", 2);

        long start = System.nanoTime();
        boolean result = adapter.dump(dumpFile);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(result, "dump must return false on timeout");
        // Should return shortly after the 2s timeout, not wait 30s.
        assertTrue(elapsedMs < 10_000, "timeout must destroy process promptly (elapsed=" + elapsedMs + "ms)");
    }

    // ------------------------------------------------------------------
    // restore: success
    // ------------------------------------------------------------------

    @Test
    @DisplayName("restore returns true when fake mysql exits 0")
    void restoreSucceedsWhenProcessExitsZero() throws IOException {
        Files.writeString(restoreFile, "-- fake restore input");
        Path fakeMysql = writeFakeScript("fake-restore-success",
                "cat > /dev/null  # consume stdin from redirectInput\nexit 0");

        ReflectionTestUtils.setField(adapter, "mysqlPath", fakeMysql.toString());

        boolean result = adapter.restore(restoreFile);

        assertTrue(result, "restore must return true when exit 0");
    }

    // ------------------------------------------------------------------
    // restore: nonzero exit
    // ------------------------------------------------------------------

    @Test
    @DisplayName("restore returns false when fake mysql exits nonzero")
    void restoreFailsWhenProcessExitsNonzero() throws IOException {
        Files.writeString(restoreFile, "-- fake restore input");
        Path fakeMysql = writeFakeScript("fake-restore-fail",
                "echo 'unknown database' >&2\nexit 1");

        ReflectionTestUtils.setField(adapter, "mysqlPath", fakeMysql.toString());

        boolean result = adapter.restore(restoreFile);

        assertFalse(result, "restore must return false on nonzero exit");
    }

    // ------------------------------------------------------------------
    // restore: timeout
    // ------------------------------------------------------------------

    @Test
    @DisplayName("restore returns false when fake mysql exceeds the timeout")
    void restoreTimesOutAndDestroysProcess() throws IOException {
        Files.writeString(restoreFile, "-- fake restore input");
        Path fakeMysql = writeFakeScript("fake-restore-timeout",
                "sleep 30\nexit 0");

        ReflectionTestUtils.setField(adapter, "mysqlPath", fakeMysql.toString());
        ReflectionTestUtils.setField(adapter, "processTimeoutSeconds", 2);

        long start = System.nanoTime();
        boolean result = adapter.restore(restoreFile);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(result, "restore must return false on timeout");
        assertTrue(elapsedMs < 10_000, "timeout must destroy process promptly (elapsed=" + elapsedMs + "ms)");
    }

    // ------------------------------------------------------------------
    // malformed datasource URL
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dump throws BusinessException on malformed datasource URL")
    void dumpThrowsOnMalformedDatasourceUrl() {
        ReflectionTestUtils.setField(adapter, "datasourceUrl", "not-a-jdbc-url");

        assertThrows(RuntimeException.class, () -> adapter.dump(dumpFile));
    }

    @Test
    @DisplayName("restore throws BusinessException on malformed datasource URL") 
    void restoreThrowsOnMalformedDatasourceUrl() {
        ReflectionTestUtils.setField(adapter, "datasourceUrl", "not-a-jdbc-url");

        assertThrows(RuntimeException.class, () -> adapter.restore(restoreFile));
    }

    // ------------------------------------------------------------------
    // process not found (IOException on start)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dump returns false when the configured binary does not exist")
    void dumpReturnsFalseWhenBinaryMissing() {
        ReflectionTestUtils.setField(adapter, "mysqldumpPath",
                tempDir.resolve("nonexistent-binary").toString());

        boolean result = adapter.dump(dumpFile);

        assertFalse(result, "dump must return false when the binary cannot be started");
    }

    @Test
    @DisplayName("restore returns false when the configured binary does not exist")
    void restoreReturnsFalseWhenBinaryMissing() {
        ReflectionTestUtils.setField(adapter, "mysqlPath",
                tempDir.resolve("nonexistent-binary").toString());

        boolean result = adapter.restore(restoreFile);

        assertFalse(result, "restore must return false when the binary cannot be started");
    }
    // ------------------------------------------------------------------
    // interrupt status (InterruptedException → Thread.currentThread().interrupt())
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dump restores interrupt flag when the calling thread is interrupted mid-wait")
    void dumpRestoresInterruptStatusWhenInterrupted() throws Exception {
        // Use a fake script that sleeps so waitFor is in progress when we
        // interrupt from another thread. But the simpler proof: pre-set
        // the interrupt flag so process.waitFor(timeout) throws
        // InterruptedException immediately, then assert the adapter
        // restores the flag and returns false without throwing.
        //
        // We need a process that starts successfully (so we reach
        // waitFor) but does not exit before the interrupt is delivered.
        // A sleep 30 script guarantees the process is alive.
        Path fakeDump = writeFakeScript("fake-dump-interrupt",
                "sleep 30\nexit 0");
        ReflectionTestUtils.setField(adapter, "mysqldumpPath", fakeDump.toString());
        ReflectionTestUtils.setField(adapter, "processTimeoutSeconds", 60);

        // Interrupt the current thread before calling dump so
        // process.waitFor(60, SECONDS) observes the interrupt and throws
        // InterruptedException. The adapter must catch it, re-set the
        // interrupt flag, and return false.
        Thread.currentThread().interrupt();
        try {
            boolean result = adapter.dump(dumpFile);
            assertFalse(result, "dump must return false when interrupted");
            assertTrue(Thread.currentThread().isInterrupted(),
                    "interrupt flag must be restored after InterruptedException");
        } finally {
            // Clear any residual interrupt to avoid polluting other tests.
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("restore restores interrupt flag when the calling thread is interrupted mid-wait")
    void restoreRestoresInterruptStatusWhenInterrupted() throws Exception {
        Files.writeString(restoreFile, "-- fake restore input");
        Path fakeMysql = writeFakeScript("fake-restore-interrupt",
                "sleep 30\nexit 0");
        ReflectionTestUtils.setField(adapter, "mysqlPath", fakeMysql.toString());
        ReflectionTestUtils.setField(adapter, "processTimeoutSeconds", 60);

        Thread.currentThread().interrupt();
        try {
            boolean result = adapter.restore(restoreFile);
            assertFalse(result, "restore must return false when interrupted");
            assertTrue(Thread.currentThread().isInterrupted(),
                    "interrupt flag must be restored after InterruptedException");
        } finally {
            Thread.interrupted();
        }
    }
}
