package com.ulticode.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for WebSocket notification messages.
 * Represents the standard message format sent over WebSocket.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    /**
     * The event type (e.g., SUBMISSION_RESULT, CONTEST_UPDATE).
     */
    private String event;

    /**
     * The payload data specific to the event type.
     */
    private Object data;

    /**
     * Unix timestamp in milliseconds when the message was created.
     */
    private Long timestamp;

    /**
     * Create a notification message with the current timestamp.
     *
     * @param event the event type
     * @param data  the payload data
     * @return the notification message
     */
    public static NotificationMessage of(String event, Object data) {
        return NotificationMessage.builder()
                .event(event)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Create a notification message from a NotificationEvent enum.
     *
     * @param event the event type
     * @param data  the payload data
     * @return the notification message
     */
    public static NotificationMessage of(NotificationEvent event, Object data) {
        return of(event.name(), data);
    }
}
