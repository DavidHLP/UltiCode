package com.ulticode.modules.backup.port.adapter;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.backup.port.BackupProcessPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Production {@link BackupProcessPort} — shells out to the real
 * {@code mysqldump} and {@code mysql} binaries. Credentials are read
 * once at boot via {@code @Value}; URL parsing is owned here (the
 * adapter) instead of leaking into {@code BackupServiceImpl}.
 *
 * <p>The two methods on this class are now the only place the JVM
 * spawns these processes, the only place the {@code MYSQL_PWD}
 * environment variable is set, and the only place the JDBC URL is
 * parsed by hand. The service-layer test can stub
 * {@link BackupProcessPort} with an in-memory adapter and exercise
 * the entire {@code @Async executeBackup()} path without a real MySQL
 * install.
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

    @Override
    public boolean dump(Path targetFile) {
        DatabaseConnectionInfo info = parseDatasourceUrl(datasourceUrl);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "mysqldump",
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

            Process process = processBuilder.start();
            String output = drainOutput(process);
            int exitCode = process.waitFor();

            if (exitCode == 0 && Files.exists(targetFile)) {
                return true;
            }
            log.error("mysqldump failed: exit={} output={}", exitCode, output);
            return false;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("mysqldump process failed", e);
            return false;
        }
    }

    @Override
    public boolean restore(Path sourceFile) {
        DatabaseConnectionInfo info = parseDatasourceUrl(datasourceUrl);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "mysql",
                    "--host=" + info.host,
                    "--port=" + info.port,
                    "--user=" + datasourceUsername,
                    info.database);
            processBuilder.environment().put("MYSQL_PWD", datasourcePassword);
            processBuilder.redirectInput(sourceFile.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            String output = drainOutput(process);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return true;
            }
            log.error("mysql restore failed: exit={} output={}", exitCode, output);
            return false;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("mysql restore process failed", e);
            return false;
        }
    }

    private String drainOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
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
}
