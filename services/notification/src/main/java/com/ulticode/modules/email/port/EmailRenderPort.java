package com.ulticode.modules.email.port;

import java.util.Map;

/**
 * Template-rendering port (seam at the email module's external interface).
 *
 * <p>The legacy {@code EmailServiceImpl.renderTemplate} was a private
 * {@code String.replace("{{var}}", value)} loop — usable but locked in:
 * future template formats (Thymeleaf, Mustache, FreeMarker) would require
 * editing the dispatcher. After the deepening, the rendering rule lives
 * behind this port so a future template engine is a new adapter rather than
 * a refactor of the dispatch sequence.
 *
 * <p><b>Dependency category:</b> in-process (L1). One adapter for now; the
 * seam is justified by the dispatcher testability win alone (the dispatcher
 * can be tested without binding a template engine).
 */
public interface EmailRenderPort {

    /**
     * Render a template body / subject by substituting placeholders with
     * variable values. Placeholder syntax is adapter-defined; the production
     * adapter uses {@code {{variableName}}}.
     *
     * @param template  the template string; may be {@code null} (returned as-is)
     * @param variables the variable values; missing keys should be treated as
     *                  empty string
     * @return the rendered string; {@code null} iff {@code template} was null
     */
    String render(String template, Map<String, Object> variables);
}
