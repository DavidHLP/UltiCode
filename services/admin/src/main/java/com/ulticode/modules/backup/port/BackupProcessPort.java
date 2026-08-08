package com.ulticode.modules.backup.port;

import java.nio.file.Path;

/**
 * Port that abstracts the dump / restore process boundary for the backup
 * module. Hides the real {@code mysqldump} / {@code mysql} subprocess
 * spawns behind two typed methods.
 *
 * <p>Prior to this port, {@code BackupServiceImpl} embedded:
 * <ul>
 *   <li>ProcessBuilder for {@code mysqldump} (dump path)</li>
 *   <li>ProcessBuilder for {@code mysql} (restore path)</li>
 *   <li>JDBC URL parsing by hand</li>
 *   <li>{@code @Value}-injected datasource credentials</li>
 * </ul>
 * The {@code @Async executeBackup()} path was untestable without a real
 * MySQL install. See
 * {@code /tmp/architecture-review-1783485814.html} candidate 5.
 *
 * <p><strong>Seam justification — two adapters:</strong>
 * <ul>
 *   <li>{@link com.ulticode.modules.backup.port.adapter.MysqldumpBackupProcessAdapter}
 *       — production, shells out to the real binaries.</li>
 *   <li>An in-memory test adapter (in the test sources) — makes
 *       {@code executeBackup()} testable end-to-end without a MySQL install.</li>
 * </ul>
 *
 * @author ulticode
 */
public interface BackupProcessPort {

    /**
     * Run mysqldump to write a database backup to {@code targetFile}.
     *
     * @param targetFile where to write the dump
     * @return {@code true} if mysqldump exited 0 and the file was created
     */
    boolean dump(Path targetFile);

    /**
     * Run mysql to restore a database from the dump at {@code sourceFile}.
     *
     * @param sourceFile the file to read
     * @return {@code true} if mysql exited 0
     */
    boolean restore(Path sourceFile);
}
