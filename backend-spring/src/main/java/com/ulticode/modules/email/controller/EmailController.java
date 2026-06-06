package com.ulticode.modules.email.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.email.dto.*;
import com.ulticode.modules.email.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for email operations.
 */
@Tag(name = "Email", description = "Email management APIs")
@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class EmailController {

    private final EmailService emailService;

    /**
     * Send an email
     */
    @Operation(summary = "Send an email")
    @RateLimit(key = "email:send", limit = 5, period = 60)
    @PostMapping("/send")
    public Result<EmailLogDTO> sendEmail(@Valid @RequestBody SendEmailDTO dto) {
        EmailLogDTO result = emailService.sendEmail(dto);
        return Result.success(result);
    }

    /**
     * Get all email templates
     */
    @Operation(summary = "Get all email templates")
    @GetMapping("/templates")
    public Result<List<EmailTemplateDTO>> getAllTemplates() {
        List<EmailTemplateDTO> templates = emailService.getAllTemplates();
        return Result.success(templates);
    }

    /**
     * Get a template by ID
     */
    @Operation(summary = "Get email template by ID")
    @GetMapping("/templates/{id}")
    public Result<EmailTemplateDTO> getTemplateById(@PathVariable String id) {
        EmailTemplateDTO template = emailService.getTemplateById(id);
        return Result.success(template);
    }

    /**
     * Create a new email template
     */
    @Operation(summary = "Create a new email template")
    @RateLimit(key = "email:create-template", limit = 30, period = 60)
    @PostMapping("/templates")
    public Result<EmailTemplateDTO> createTemplate(@Valid @RequestBody CreateTemplateDTO dto) {
        EmailTemplateDTO template = emailService.createTemplate(dto);
        return Result.success(template);
    }

    /**
     * Update an email template
     */
    @Operation(summary = "Update an email template")
    @RateLimit(key = "email:update-template", limit = 30, period = 60)
    @PutMapping("/templates/{id}")
    public Result<EmailTemplateDTO> updateTemplate(
            @PathVariable String id,
            @Valid @RequestBody UpdateTemplateDTO dto) {
        EmailTemplateDTO template = emailService.updateTemplate(id, dto);
        return Result.success(template);
    }

    /**
     * Delete an email template
     */
    @Operation(summary = "Delete an email template")
    @RateLimit(key = "email:delete-template", limit = 30, period = 60)
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable String id) {
        emailService.deleteTemplate(id);
        return Result.success();
    }

    /**
     * Get email logs with filtering and pagination
     */
    @Operation(summary = "Get email logs")
    @GetMapping("/logs")
    public Result<PageResult<EmailLogDTO>> getEmailLogs(EmailLogQueryDTO query) {
        PageResult<EmailLogDTO> result = emailService.getEmailLogs(query);
        return Result.success(result);
    }

    /**
     * Get email statistics
     */
    @Operation(summary = "Get email statistics")
    @GetMapping("/stats")
    public Result<EmailStatsDTO> getEmailStats() {
        EmailStatsDTO stats = emailService.getEmailStats();
        return Result.success(stats);
    }
}
