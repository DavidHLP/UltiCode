package com.ulticode.modules.admin.service.impl;

/**
 * Payload returned by {@code ProblemExportService} describing exactly what
 * the controller must write to the {@code HttpServletResponse}.
 *
 * <p>One of {@link #jsonBytes()} or {@link #csvBody()} is populated,
 * depending on the requested format. The controller does not inspect the
 * body &mdash; it streams whichever field is non-null.
 *
 * @param contentType the HTTP {@code Content-Type} header value
 * @param filename    the {@code Content-Disposition} attachment filename
 * @param jsonBytes   the JSON body, or {@code null} for CSV
 * @param csvBody     the CSV body, or {@code null} for JSON
 *
 * @author ulticode
 */
public record ExportPayload(String contentType, String filename, byte[] jsonBytes, String csvBody) {

    /** Factory for a JSON payload. */
    public static ExportPayload json(String filename, byte[] body) {
        return new ExportPayload("application/json", filename, body, null);
    }

    /** Factory for a CSV payload. */
    public static ExportPayload csv(String filename, String body) {
        return new ExportPayload("text/csv; charset=UTF-8", filename, null, body);
    }
}
