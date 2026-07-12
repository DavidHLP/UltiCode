package com.ulticode.modules.admin.service.impl;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Payload returned by {@code ProblemExportService} describing exactly what
 * the controller must write to the {@code HttpServletResponse}.
 *
 * <p>One of {@link #jsonBytes()} or {@link #csvBody()} is populated,
 * depending on the requested format. The controller does not inspect the
 * body &mdash; {@link #writeTo(HttpServletResponse)} streams whichever
 * field is non-null. Format selection lives entirely inside the payload,
 * so the controller stays a one-call dispatcher.
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

    /**
     * Stream this payload to {@code response}. Selects the writer
     * internally based on which body field is populated, so callers
     * never need to branch on format.
     *
     * @throws IOException if the underlying stream/buffer cannot be written
     */
    public void writeTo(HttpServletResponse response) throws IOException {
        if (jsonBytes != null) {
            try (OutputStream out = response.getOutputStream()) {
                out.write(jsonBytes);
                out.flush();
            }
        } else {
            try (PrintWriter writer = response.getWriter()) {
                writer.write(csvBody);
                writer.flush();
            }
        }
    }
}
