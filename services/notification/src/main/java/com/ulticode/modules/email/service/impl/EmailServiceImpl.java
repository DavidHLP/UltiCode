package com.ulticode.modules.email.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.notification.error.EmailErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.email.constants.EmailStatus;
import com.ulticode.modules.email.dto.*;
import com.ulticode.modules.email.entity.EmailLog;
import com.ulticode.modules.email.entity.EmailTemplate;
import com.ulticode.modules.email.intake.EmailIntake;
import com.ulticode.modules.email.mapper.EmailLogMapper;
import com.ulticode.modules.email.mapper.EmailTemplateMapper;
import com.ulticode.modules.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Email service — administrative facade for templates + logs + the send
 * pipeline.
 *
 * <p><b>Deepened.</b> The send pipeline (validate recipient, resolve template,
 * render variables, persist log, transport, mark sent/failed) used to live
 * inline in {@link #sendEmail} — a 56-line method doing 6 unrelated things.
 * After the deepening, that pipeline is the {@link EmailIntake} deep module
 * with three collaborators (render port, sender port, log mapper). This
 * facade keeps the public API stable so the controller and tests do not
 * change, and continues to own the template CRUD + log reads + stats
 * rollups (administrative reads that the controller serves directly).
 *
 * <p>Dependency category: in-process for the CRUD / read paths; the send
 * pipeline delegates to {@code EmailIntake} which is local-substitutable
 * (production JavaMail / dev logging adapter; both implemented).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final EmailTemplateMapper templateMapper;
    private final EmailLogMapper logMapper;
    private final EmailIntake emailIntake;

    @Override
    public EmailLogDTO sendEmail(SendEmailDTO dto) {
        return emailIntake.send(dto);
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
            throw new BusinessException(EmailErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
        }
        return toTemplateDTO(template);
    }

    @Override
    public EmailTemplateDTO updateTemplate(String id, UpdateTemplateDTO dto) {
        EmailTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(EmailErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
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
            throw new BusinessException(EmailErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
        }
        templateMapper.deleteById(id);
    }

    @Override
    public PageResult<EmailLogDTO> getEmailLogs(EmailLogQueryDTO query) {
        LambdaQueryWrapper<EmailLog> wrapper = new LambdaQueryWrapper<>();

        if (query.getStatus() != null) {
            wrapper.eq(EmailLog::getStatus, query.getStatus());
        }
        if (query.getRecipient() != null && !query.getRecipient().isBlank()) {
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
