package com.ulticode.modules.admin.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.service.impl.ExportPayload;
import com.ulticode.app.api.dto.ProblemAdminQueryDTO;

/**
 * Deep module that owns problem export shaping.
 *
 * <p>Lifted out of {@code AdminProblemController.exportProblems} so the
 * controller is left with nothing but response-writing. Format validation,
 * export size cap, CSV header literal, CSV field escaping and the
 * {@code LocalDate.now()} time-leak (the {@link java.time.Clock} seam was
 * added to remove exactly that call) all live here, behind a pure payload
 * the controller streams to the response.
 *
 * <p>Reads flow through the public {@code ProblemAdminReadPort} contract;
 * the App-private problem projection is no longer imported.
 *
 * @author ulticode
 */
public interface ProblemExportService {

    /**
     * Maximum number of rows export will emit, regardless of query result size.
     */
    int MAX_EXPORT_SIZE = 10000;

    /**
     * Build the export payload for a query and format.
     *
     * @param query  problem filters
     * @param format {@code "json"} or {@code "csv"} (case-insensitive, whitespace-trimmed);
     *               any other value throws {@link BusinessException}
     *               {@link com.ulticode.admin.error.AdminErrorCode#BAD_REQUEST}
     * @return the export payload
     */
    ExportPayload export(ProblemAdminQueryDTO query, String format);
}
