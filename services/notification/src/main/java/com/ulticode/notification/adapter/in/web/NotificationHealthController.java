package com.ulticode.notification.adapter.in.web;

import com.ulticode.common.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public process probe for the notification owner. */
@RestController
@RequestMapping("/api/v1/notification")
public class NotificationHealthController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("backend-notification up");
    }
}
