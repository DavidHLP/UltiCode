package com.ulticode.modules.backup.port.adapter;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.backup.port.BackupProcessPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Production {@link BackupProcessPort} — shells out to the real
 * {@code mysqldump} and {@code mysql} binaries. Credentials are read
 * once at boot via {@code @Value}; URL parsing is owned here (the
 * adapter) instead of leaking into {@link com.ulticode.modules.backup.service.BackupService}.
 *
 * <p>The two methods on this class are the only place the JVM spawns
 * these processes, the only place the {@code MYSQL_PWD} environment
 * variable is set, and the only place the JDBC URL is parsed by hand.
 * The service-layer test can stub {@link BackupProcessPort} with an
 * in-memory adapter and exercise the entire {@code @Async executeBackup()}
 * path without a real MySQL install.
 *
 * <p><strong>Bounded execution.</strong> Both dump and restore enforce a
 * configurable wall-clock timeout ({@code backup.process.timeout-seconds},
 * default 1800&nbsp;s). stdout is drained in a background daemon thread to
 * avoid the 64&nbsp;KiB Linux pipe-buffer deadlock, and the process is
 * {@link Process#destroyForcibly()} 'd on timeout. This mirrors the
 * {@code DockerProcessRunner.run()} pattern proven in the judge sandbox
 * ({@code backend-legacy/.../sandbox/executor/DockerProcessRunner}).
 *
 * <p>Credentials are passed via the {@code MYSQL_PWD} environment variable
 * and never appear in argv or logs.
 *
 * @author ulticode
 */
@Slf4j
@Component
public class MysqldumpBackupProcessAdapter implements BackupProcessPort {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${backup.process.timeout-seconds:1800}")
    private int processTimeoutSeconds;

    @Value("${backup.process.mysqldump-path:mysqldump}")
    private String mysqldumpPath;

    @Value("${backup.process.mysql-path:mysql}")
    private String mysqlPath;

    @Override
    public boolean dump(Path targetFile) {
        DatabaseConnectionInfo info = parseDatasourceUrl(datasourceUrl);
        ProcessBuilder processBuilder = new ProcessBuilder(
                mysqldumpPath,
                "--host=" + info.host,
                "--port=" + info.port,
                "--user=" + datasourceUsername,
                "--single-transaction",
                "--routines",
                "--triggers",
                "--add-drop-table",
                info.database);
        processBuilder.environment().put("MYSQL_PWD", datasourcePassword);
        processBuilder.redirectOutput(targetFile.toFile());
        processBuilder.redirectErrorStream(true);

        BoundedProcessResult result = runBounded(processBuilder, processTimeoutSeconds, "mysqldump");
        if (result.timedOut()) {
            log.error("mysqldump timed out after {}s", processTimeoutSeconds);
            return false;
        }
        if (result.exitCode() == 0 && Files.exists(targetFile)) {
            return true;
        }
        log.error("mysqldump failed: exit={} output={}", result.exitCode(), result.output());
        return false;
    }

    @Override
    public boolean restore(Path sourceFile) {
        DatabaseConnectionInfo info = parseDatasourceUrl(datasourceUrl);
        ProcessBuilder processBuilder = new ProcessBuilder(
                mysqlPath,
                "--host=" + info.host,
                "--port=" + info.port,
                "--user=" + datasourceUsername,
                info.database);
        processBuilder.environment().put("MYSQL_PWD", datasourcePassword);
        processBuilder.redirectInput(sourceFile.toFile());
        processBuilder.redirectErrorStream(true);

        BoundedProcessResult result = runBounded(processBuilder, processTimeoutSeconds, "mysql-restore");
        if (result.timedOut()) {
            log.error("mysql restore timed out after {}s", processTimeoutSeconds);
            return false;
        }
        if (result.exitCode() == 0) {
            return true;
        }
        log.error("mysql restore failed: exit={} output={}", result.exitCode(), result.output());
        return false;
    }

    /**
     * Spawn a process, drain its stdout in a background daemon thread, and
     * enforce a hard wall-clock timeout. Mirrors the
     * {@code DockerProcessRunner.run()} pattern from the judge sandbox:
     * draining in a background thread avoids the 64&nbsp;KiB Linux
     * pipe-buffer deadlock, and {@link Process#destroyForcibly()} on
     * timeout prevents indefinite hangs.
     *
     * @param processBuilder configured ProcessBuilder (not yet started)
     * @param timeoutSeconds hard wall-clock budget
     * @param runId          stable id used to name the drainer thread
     * @return the captured outcome (exit code, stdout, timed-out flag)
     */
    private BoundedProcessResult runBounded(ProcessBuilder processBuilder,
                                            int timeoutSeconds, String runId) {
        try {
            Process process = processBuilder.start();

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream in = process.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) != -1) {
                        buf.write(chunk, 0, n);
                    }
                } catch (IOException ignored) {
                    /* process closed */
                }
            }, "backup-stdout-" + runId);
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                // Join the stdout drainer on the timeout path so the
                // daemon thread and its buffer do not leak after a hard
                // timeout.
                reader.join(TimeUnit.SECONDS.toMillis(Math.min(2, timeoutSeconds)));
                return new BoundedProcessResult(-1, buf.toString(StandardCharsets.UTF_8), true);
            }
            // Give the reader a brief grace window for last bytes.
            reader.join(TimeUnit.SECONDS.toMillis(Math.min(2, timeoutSeconds)));
            return new BoundedProcessResult(process.exitValue(),
                    buf.toString(StandardCharsets.UTF_8), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("backup process interrupted: {}", runId);
            return new BoundedProcessResult(-1, "", false);
        } catch (IOException e) {
            log.error("backup process failed to start: {}", runId, e);
            return new BoundedProcessResult(-1, "", false);
        }
    }

    private DatabaseConnectionInfo parseDatasourceUrl(String url) {
        DatabaseConnectionInfo info = new DatabaseConnectionInfo();
        info.port = 3306;
        try {
            String connectionPart = url.substring("jdbc:mysql://".length());
            int slashIndex = connectionPart.indexOf('/');
            if (slashIndex > 0) {
                String hostPort = connectionPart.substring(0, slashIndex);
                String databasePart = connectionPart.substring(slashIndex + 1);
                int queryIndex = databasePart.indexOf('?');
                info.database = queryIndex > 0
                        ? databasePart.substring(0, queryIndex)
                        : databasePart;
                int colonIndex = hostPort.indexOf(':');
                if (colonIndex > 0) {
                    info.host = hostPort.substring(0, colonIndex);
                    info.port = Integer.parseInt(hostPort.substring(colonIndex + 1));
                } else {
                    info.host = hostPort;
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse datasource URL: {}", url, e);
            throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR,
                    "Failed to parse database connection configuration");
        }
        return info;
    }

    private static class DatabaseConnectionInfo {
        String host;
        int port;
        String database;
    }

    /**
     * Immutable outcome of a bounded process run.
     */
    private static final class BoundedProcessResult {
        private final int exitCode;
        private final String output;
        private final boolean timedOut;

        BoundedProcessResult(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }

        int exitCode() {
            return exitCode;
        }

        String output() {
            return output;
        }

        boolean timedOut() {
            return timedOut;
        }
    }
}
