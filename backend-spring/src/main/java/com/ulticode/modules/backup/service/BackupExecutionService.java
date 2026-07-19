package com.ulticode.modules.backup.service;

/**
 * Async backup execution lifecycle &mdash; a deep module that owns the
 * {@code PENDING &rarr; IN_PROGRESS &rarr; COMPLETED / FAILED} state
 * machine, the {@code mysqldump} outcome hand-off, file-size / metadata
 * recording and failure capture for a single backup run.
 *
 * <p><strong>Why this is a separate bean from {@link BackupService}.</strong>
 * Spring's {@code @Async} only takes effect when the call crosses the AOP
 * proxy. When {@code createBackup} lived next to {@code executeBackup} on
 * the same {@link BackupServiceImpl} class, the dispatch was a
 * self-invocation ({@code this.executeBackup(id)}) &mdash; it bypassed the
 * proxy and ran synchronously on the controller's request thread, blocking
 * the HTTP request until {@code mysqldump} finished and silently
 * defeating the {@code @Async} annotation. Moving the lifecycle into its
 * own injected bean forces every dispatch through the proxy, restoring the
 * async contract; the wiring test in
 * {@code BackupExecutionServiceTest} pins the separation.
 *
 * <p><strong>Why this is the deep module.</strong> The previous shape mixed
 * request orchestration (create / restore / delete / file download), the
 * async dispatch decision, lifecycle transitions, process I/O and the
 * public job method in one class. Concentrating the lifecycle here means
 * the invariants &mdash; status only moves forward, every run records a
 * terminal state, every failure captures an error string &mdash; live in
 * one file. {@link BackupService} keeps only request orchestration; reads
 * stay behind {@link com.ulticode.modules.backup.projection.BackupReadProjection}.
 *
 * <p>Process I/O itself is still delegated to
 * {@link com.ulticode.modules.backup.port.BackupProcessPort}; this service
 * is the lifecycle owner, not the subprocess spawner.
 *
 * @author ulticode
 */
public interface BackupExecutionService {

    /**
     * Execute the backup process asynchronously.
     *
     * <p>Implementations are annotated {@code @Async}; callers must invoke
     * this through the Spring bean (never via {@code this.}) so the proxy
     * is crossed and dispatch actually leaves the caller's thread.
     *
     * @param backupId the backup ID to execute
     */
    void executeBackup(String backupId);
}
