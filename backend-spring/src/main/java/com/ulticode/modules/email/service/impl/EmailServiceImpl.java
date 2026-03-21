package com.ulticode.modules.email.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.email.constants.EmailStatus;
import com.ulticode.modules.email.dto.*;
import com.ulticode.modules.email.entity.EmailLog;
import com.ulticode.modules.email.entity.EmailTemplate;
import com.ulticode.modules.email.mapper.EmailLogMapper;
import com.ulticode.modules.email.mapper.EmailTemplateMapper;
import com.ulticode.modules.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of EmailService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final EmailTemplateMapper templateMapper;
    private final EmailLogMapper logMapper;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@example.com}")
    private String fromEmail;

    @Value("${app.email.from-name:UltiCode}")
    private String fromName;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    @Override
    public EmailLogDTO sendEmail(SendEmailDTO dto) {
        // Validate recipient
        if (!isValidEmail(dto.getTo())) {
            throw new BusinessException(ErrorCode.EMAIL_INVALID_RECIPIENT);
        }

        String subject = dto.getSubject();
        String html = dto.getHtml();
        String text = dto.getText();
        String templateId = dto.getTemplateId();
        Map<String, Object> variables = dto.getVariables();

        // If template is specified, use it
        if (StringUtils.hasText(templateId)) {
            EmailTemplate template = templateMapper.selectById(templateId);
            if (template == null) {
                throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
            }
            subject = renderTemplate(template.getSubject(), variables != null ? variables : Collections.emptyMap());
            html = renderTemplate(template.getBody(), variables != null ? variables : Collections.emptyMap());
        }

        // Create log entry with PENDING status
        EmailLog emailLog = new EmailLog();
        emailLog.setTemplateId(templateId);
        emailLog.setRecipient(dto.getTo());
        emailLog.setSubject(subject);
        emailLog.setStatus(EmailStatus.PENDING);
        logMapper.insert(emailLog);

        try {
            if (emailEnabled && mailSender != null) {
                sendMailViaSmtp(dto.getTo(), subject, html, text);
            } else {
                // Log only mode
                log.info("[EMAIL] To: {}, Subject: {}", dto.getTo(), subject);
            }

            // Update log as sent
            emailLog.setStatus(EmailStatus.SENT);
            emailLog.setSentAt(LocalDateTime.now());
            logMapper.updateById(emailLog);

            return toLogDTO(emailLog);
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            log.error("Failed to send email to {}: {}", dto.getTo(), errorMessage);

            // Update log as failed
            emailLog.setStatus(EmailStatus.FAILED);
            emailLog.setError(errorMessage);
            logMapper.updateById(emailLog);

            return toLogDTO(emailLog);
        }
    }

    @Override
    public EmailTemplateDTO createTemplate(CreateTemplateDTO dto) {
        EmailTemplate template = new EmailTemplate();
        template.setName(dto.getName());
        template.setSubject(dto.getSubject());
        template.setBody(dto.getBody());
        template.setVariables(dto.getVariables() != null ? dto.getVariables() : Collections.emptyList());

        templateMapper.insert(template);
        return toTemplateDTO(template);
    }

    @Override
    public List<EmailTemplateDTO> getAllTemplates() {
        LambdaQueryWrapper<EmailTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(EmailTemplate::getName);

        List<EmailTemplate> templates = templateMapper.selectList(wrapper);
        return templates.stream()
                .map(this::toTemplateDTO)
                .collect(Collectors.toList());
    }

    @Override
    public EmailTemplateDTO getTemplateById(String id) {
        EmailTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
        }
        return toTemplateDTO(template);
    }

    @Override
    public EmailTemplateDTO updateTemplate(String id, UpdateTemplateDTO dto) {
        EmailTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
        }

        template.setName(dto.getName());
        template.setSubject(dto.getSubject());
        template.setBody(dto.getBody());
        template.setVariables(dto.getVariables() != null ? dto.getVariables() : Collections.emptyList());

        templateMapper.updateById(template);
        return toTemplateDTO(template);
    }

    @Override
    public void deleteTemplate(String id) {
        EmailTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
        }
        templateMapper.deleteById(id);
    }

    @Override
    public PageResult<EmailLogDTO> getEmailLogs(EmailLogQueryDTO query) {
        LambdaQueryWrapper<EmailLog> wrapper = new LambdaQueryWrapper<>();

        if (query.getStatus() != null) {
            wrapper.eq(EmailLog::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getRecipient())) {
            wrapper.like(EmailLog::getRecipient, query.getRecipient());
        }

        wrapper.orderByDesc(EmailLog::getCreatedAt);

        Page<EmailLog> page = new Page<>(query.getPage(), query.getLimit());
        Page<EmailLog> result = logMapper.selectPage(page, wrapper);

        List<EmailLogDTO> voList = result.getRecords().stream()
                .map(this::toLogDTO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public EmailStatsDTO getEmailStats() {
        EmailStatsDTO stats = new EmailStatsDTO();

        long total = logMapper.selectCount(null);
        long sent = logMapper.selectCount(
                new LambdaQueryWrapper<EmailLog>().eq(EmailLog::getStatus, EmailStatus.SENT)
        );
        long pending = logMapper.selectCount(
                new LambdaQueryWrapper<EmailLog>().eq(EmailLog::getStatus, EmailStatus.PENDING)
        );
        long failed = logMapper.selectCount(
                new LambdaQueryWrapper<EmailLog>().eq(EmailLog::getStatus, EmailStatus.FAILED)
        );

        stats.setTotal(total);
        stats.setSent(sent);
        stats.setPending(pending);
        stats.setFailed(failed);

        return stats;
    }

    /**
     * Render a template by replacing {{variable}} placeholders with actual values.
     *
     * @param template  the template string
     * @param variables the variable values
     * @return the rendered string
     */
    private String renderTemplate(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * Send email via SMTP.
     *
     * @param to      recipient email
     * @param subject email subject
     * @param html    HTML body
     * @param text    plain text body
     */
    private void sendMailViaSmtp(String to, String subject, String html, String text) throws MessagingException {
        if (StringUtils.hasText(html)) {
            // Send HTML email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(String.format("%s <%s>", fromName, fromEmail));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
        } else if (StringUtils.hasText(text)) {
            // Send plain text email
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        }
    }

    /**
     * Validate email address format.
     *
     * @param email the email to validate
     * @return true if valid
     */
    private boolean isValidEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Convert EmailLog entity to DTO.
     */
    private EmailLogDTO toLogDTO(EmailLog log) {
        EmailLogDTO dto = new EmailLogDTO();
        dto.setId(log.getId());
        dto.setTemplateId(log.getTemplateId());
        dto.setRecipient(log.getRecipient());
        dto.setSubject(log.getSubject());
        dto.setStatus(log.getStatus());
        dto.setSentAt(log.getSentAt());
        dto.setError(log.getError());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    /**
     * Convert EmailTemplate entity to DTO.
     */
    private EmailTemplateDTO toTemplateDTO(EmailTemplate template) {
        EmailTemplateDTO dto = new EmailTemplateDTO();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setSubject(template.getSubject());
        dto.setBody(template.getBody());
        dto.setVariables(template.getVariables());
        dto.setCreatedAt(template.getCreatedAt());
        dto.setUpdatedAt(template.getUpdatedAt());
        return dto;
    }
}
