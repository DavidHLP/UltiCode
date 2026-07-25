package com.ulticode.modules.email.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.email.dto.*;

/**
 * Service interface for email operations.
 */
public interface EmailService {

    /**
     * Send an email
     *
     * @param dto the email to send
     * @return the email log entry
     */
    EmailLogDTO sendEmail(SendEmailDTO dto);

    /**
     * Create a new email template
     *
     * @param dto the template to create
     * @return the created template
     */
    EmailTemplateDTO createTemplate(CreateTemplateDTO dto);

    /**
     * Get all email templates
     *
     * @return list of all templates
     */
    java.util.List<EmailTemplateDTO> getAllTemplates();

    /**
     * Get a template by ID
     *
     * @param id the template ID
     * @return the template
     */
    EmailTemplateDTO getTemplateById(String id);

    /**
     * Update a template
     *
     * @param id  the template ID
     * @param dto the updated template data
     * @return the updated template
     */
    EmailTemplateDTO updateTemplate(String id, UpdateTemplateDTO dto);

    /**
     * Delete a template
     *
     * @param id the template ID
     */
    void deleteTemplate(String id);

    /**
     * Get email logs with filtering and pagination
     *
     * @param query the query parameters
     * @return paginated email logs
     */
    PageResult<EmailLogDTO> getEmailLogs(EmailLogQueryDTO query);

    /**
     * Get email statistics
     *
     * @return email statistics
     */
    EmailStatsDTO getEmailStats();
}
