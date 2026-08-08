package com.ulticode.modules.email.port.adapter;

import com.ulticode.modules.email.port.EmailRenderPort;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default adapter of {@link EmailRenderPort} — substitutes
 * {@code {{variableName}}} placeholders with the supplied values.
 *
 * <p>Mirrors the legacy {@code EmailServiceImpl.renderTemplate} behaviour
 * (String.replace loop, missing values default to empty string, null input
 * returned as null). A future Thymeleaf / Mustache adapter drops in without
 * touching the dispatcher.
 */
@Component
public class StringReplaceEmailRenderAdapter implements EmailRenderPort {

    @Override
    public String render(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}