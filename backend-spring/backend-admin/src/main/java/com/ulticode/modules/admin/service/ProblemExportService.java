package com.ulticode.modules.admin.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.service.impl.ExportPayload;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;

/**
 * Deep module that owns problem export shaping.
 *
 * <p>Lifted out of {@code AdminProblemController.exportProblems} so the
 * controller is left with nothing but response-writing. Format validation,
 * the export size cap, the CSV header literal, CSV field escaping, and the
 * {@code LocalDate.now()} time-leak (the {@link java.time.Clock} seam was
 * added to remove exactly this call) all live here, behind a pure payload
 * the controller streams to the response.
 *
 * <p>Unit-testable without a servlet container: hand in a {@link java.time.Clock}
 * and a {@link com.ulticode.modules.problem.projection.ProblemProjection}
 * double, assert the payload.
 *
 * @author ulticode
 */
public interface ProblemExportService {

    /** Maximum number of rows an export will emit, regardless of query result size. */
    int MAX_EXPORT_SIZE = 10000;

    /**
     * Build an export payload for the given query and format.
     *
     * @param query  the problem filters
     * @param format {@code "json"} or {@code "csv"} (case-insensitive, whitespace-trimmed);
     *               any other value throws {@link com.ulticode.common.exception.BusinessException}
     *               with {@link com.ulticode.common.error.BaseErrorCode#BAD_REQUEST}
     * @return the shaped payload (content type, filename, body)
     */
    ExportPayload export(ProblemQueryDTO query, String format);
}
