package com.ulticode.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * View object for unread notification count.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountVO {
    private Long count;
}
