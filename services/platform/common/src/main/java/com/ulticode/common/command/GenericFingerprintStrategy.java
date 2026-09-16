package com.ulticode.common.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The reflection-based SHA-256 fingerprint used by App, Submission, and
 * Notification. Its field order and length-prefix encoding are compatibility
 * behavior for rows already stored by those owners.
 */
public final class GenericFingerprintStrategy implements ReceiptFingerprintStrategy<WriteCommand> {

    private static final char FIELD_SEPARATOR = '\u001f';

    @Override
    public String fingerprint(WriteCommand command) {
        Objects.requireNonNull(command, "command");
        var components = command.getClass().getRecordComponents();
        String payload = components == null
                ? command.getClass().getName()
                : Arrays.stream(components)
                .filter(component -> !switch (component.getName()) {
                    case "commandId", "idempotency", "trace" -> true;
                    default -> false;
                })
                .map(component -> {
                    try {
                        return component.getName() + "="
                                + encode(component.getAccessor().invoke(command));
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException(
                                "Unable to fingerprint " + command.getClass().getName(), exception);
                    }
                })
                .collect(Collectors.joining("|"));
        return sha256(command.getClass().getName() + FIELD_SEPARATOR
                + join(command.actor().actorType(), command.actor().actorId(),
                command.actor().delegatorId(), payload));
    }

    private static String join(Object... values) {
        return Arrays.stream(values)
                .map(GenericFingerprintStrategy::encode)
                .collect(Collectors.joining("|"));
    }

    private static String encode(Object value) {
        if (value == null) {
            return "-1:";
        }
        String text = String.valueOf(value);
        return text.length() + ":" + text;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
