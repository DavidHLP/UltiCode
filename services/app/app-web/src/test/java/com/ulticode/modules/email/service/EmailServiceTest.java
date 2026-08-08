package com.ulticode.modules.email.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.EmailErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.email.constants.EmailStatus;
import com.ulticode.modules.email.dto.*;
import com.ulticode.modules.email.entity.EmailLog;
import com.ulticode.modules.email.entity.EmailTemplate;
import com.ulticode.modules.email.intake.EmailIntake;
import com.ulticode.modules.email.mapper.EmailLogMapper;
import com.ulticode.modules.email.mapper.EmailTemplateMapper;
import com.ulticode.modules.email.port.EmailRenderPort;
import com.ulticode.modules.email.port.SmtpSenderPort;
import com.ulticode.modules.email.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.mail.MessagingException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EmailServiceImpl} (admin facade) and the
 * {@link EmailIntake} send pipeline.
 *
 * <p>After the deepening, the SMTP transport lives behind
 * {@link SmtpSenderPort} and template rendering behind {@link EmailRenderPort}.
 * Tests inject mocks for both ports so the intake's behaviour is exercised
 * end-to-end without a real JavaMail session.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private EmailTemplateMapper templateMapper;

    @Mock
    private EmailLogMapper logMapper;

    @Mock
    private EmailRenderPort emailRenderPort;

    @Mock
    private SmtpSenderPort smtpSenderPort;

    private EmailIntake emailIntake;

    @InjectMocks
    private EmailServiceImpl emailService;

    private static final String TEMPLATE_ID = "test-template-id";
    private static final String LOG_ID = "test-log-id";
    private static final String RECIPIENT = "test@example.com";

    private EmailTemplate createTestTemplate() {
        EmailTemplate template = new EmailTemplate();
        template.setId(TEMPLATE_ID);
        template.setName("Test Template");
        template.setSubject("Hello {{name}}");
        template.setBody("<p>Hello {{name}}, welcome to {{app}}!</p>");
        template.setVariables(Arrays.asList("name", "app"));
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        return template;
    }

    private EmailLog createTestLog() {
        EmailLog log = new EmailLog();
        log.setId(LOG_ID);
        log.setRecipient(RECIPIENT);
        log.setSubject("Test Subject");
        log.setStatus(EmailStatus.SENT);
        log.setSentAt(LocalDateTime.now());
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    @BeforeEach
    void setUp() {
        // Wire the intake against the mocked ports. In production wiring,
        // Spring injects StringReplaceEmailRenderAdapter + (JavaMail or
        // Logging) SmtpSenderAdapter.
        emailIntake = new EmailIntake(Clock.systemDefaultZone(), templateMapper, logMapper, emailRenderPort, smtpSenderPort);
        // Re-wire the service facade's intake field via reflection because
        // @InjectMocks cannot reach the new collaborator (it was injected
        // post-construction when @RequiredArgsConstructor ran). For tests
        // we set it explicitly so the facade delegates to our intake.
        org.springframework.test.util.ReflectionTestUtils.setField(emailService, "emailIntake", emailIntake);
    }

    // ==================== sendEmail Tests ====================

    @Nested
    @DisplayName("sendEmail")
    class SendEmailTests {

        @Test
        @DisplayName("should send email with template successfully")
        void shouldSendEmailWithTemplateSuccessfully() throws MessagingException {
            // Arrange
            SendEmailDTO dto = new SendEmailDTO();
            dto.setTo(RECIPIENT);
            dto.setTemplateId(TEMPLATE_ID);
            Map<String, Object> variables = new HashMap<>();
            variables.put("name", "John");
            variables.put("app", "UltiCode");
            dto.setVariables(variables);

            EmailTemplate template = createTestTemplate();

            when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(template);
            when(emailRenderPort.render(eq("Hello {{name}}"), any())).thenReturn("Hello John");
            when(emailRenderPort.render(eq("<p>Hello {{name}}, welcome to {{app}}!</p>"), any()))
                    .thenReturn("<p>Hello John, welcome to UltiCode!</p>");
            when(logMapper.insert(any(EmailLog.class))).thenAnswer(invocation -> {
                EmailLog log = invocation.getArgument(0);
                log.setId(LOG_ID);
                return 1;
            });
            when(logMapper.updateById(any(EmailLog.class))).thenReturn(1);

            // Act
            EmailLogDTO result = emailService.sendEmail(dto);

            // Assert
            assertNotNull(result);
            assertEquals(RECIPIENT, result.getRecipient());
            verify(logMapper).insert(any(EmailLog.class));
            verify(smtpSenderPort).send(eq(RECIPIENT), eq("Hello John"),
                    eq("<p>Hello John, welcome to UltiCode!</p>"), isNull());
        }

        @Test
        @DisplayName("should send email without template successfully")
        void shouldSendEmailWithoutTemplateSuccessfully() throws MessagingException {
            // Arrange
            SendEmailDTO dto = new SendEmailDTO();
            dto.setTo(RECIPIENT);
            dto.setSubject("Custom Subject");
            dto.setHtml("<p>Custom body</p>");
            dto.setText("Custom text");

            when(logMapper.insert(any(EmailLog.class))).thenAnswer(invocation -> {
                EmailLog log = invocation.getArgument(0);
                log.setId(LOG_ID);
                return 1;
            });
            when(logMapper.updateById(any(EmailLog.class))).thenReturn(1);

            // Act
            EmailLogDTO result = emailService.sendEmail(dto);

            // Assert
            assertNotNull(result);
            assertEquals(RECIPIENT, result.getRecipient());
            assertEquals("Custom Subject", result.getSubject());
            verify(smtpSenderPort).send(RECIPIENT, "Custom Subject", "<p>Custom body</p>", "Custom text");
        }

        @Test
        @DisplayName("should throw EMAIL_TEMPLATE_NOT_FOUND when template not found")
        void shouldThrowEmailTemplateNotFound() {
            // Arrange
            SendEmailDTO dto = new SendEmailDTO();
            dto.setTo(RECIPIENT);
            dto.setTemplateId("non-existent-template");

            when(templateMapper.selectById("non-existent-template")).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> emailService.sendEmail(dto)
            );
            assertEquals(EmailErrorCode.EMAIL_TEMPLATE_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("should render template variables correctly")
        void shouldRenderTemplateVariablesCorrectly() {
            // Arrange
            SendEmailDTO dto = new SendEmailDTO();
            dto.setTo(RECIPIENT);
            dto.setTemplateId(TEMPLATE_ID);
            Map<String, Object> variables = new HashMap<>();
            variables.put("name", "Alice");
            variables.put("app", "TestApp");
            dto.setVariables(variables);

            EmailTemplate template = createTestTemplate();

            when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(template);
            when(emailRenderPort.render(eq("Hello {{name}}"), any())).thenReturn("Hello Alice");
            when(logMapper.insert(any(EmailLog.class))).thenAnswer(invocation -> {
                EmailLog log = invocation.getArgument(0);
                log.setId(LOG_ID);
                return 1;
            });
            when(logMapper.updateById(any(EmailLog.class))).thenReturn(1);

            // Act
            EmailLogDTO result = emailService.sendEmail(dto);

            // Assert
            assertNotNull(result);
            assertEquals("Hello Alice", result.getSubject());
        }

        @Test
        @DisplayName("should mark log FAILED when SMTP transport throws")
        void shouldMarkLogFailedWhenSmtpThrows() throws MessagingException {
            SendEmailDTO dto = new SendEmailDTO();
            dto.setTo(RECIPIENT);
            dto.setSubject("Subject");
            dto.setHtml("<p>body</p>");

            when(logMapper.insert(any(EmailLog.class))).thenAnswer(invocation -> {
                EmailLog log = invocation.getArgument(0);
                log.setId(LOG_ID);
                return 1;
            });
            when(logMapper.updateById(any(EmailLog.class))).thenReturn(1);
            doThrow(new RuntimeException("SMTP server unreachable"))
                    .when(smtpSenderPort).send(any(), any(), any(), any());

            EmailLogDTO result = emailService.sendEmail(dto);

            assertNotNull(result);
            assertEquals(EmailStatus.FAILED, result.getStatus());
            assertNotNull(result.getError());
        }
    }

    // ==================== Template CRUD Tests ====================

    @Nested
    @DisplayName("Template CRUD")
    class TemplateTests {

        @Test
        @DisplayName("should create template successfully")
        void shouldCreateTemplateSuccessfully() {
            // Arrange
            CreateTemplateDTO dto = new CreateTemplateDTO();
            dto.setName("Welcome Email");
            dto.setSubject("Welcome {{name}}");
            dto.setBody("<p>Welcome, {{name}}!</p>");
            dto.setVariables(Arrays.asList("name"));

            when(templateMapper.insert(any(EmailTemplate.class))).thenAnswer(invocation -> {
                EmailTemplate template = invocation.getArgument(0);
                template.setId("new-template-id");
                return 1;
            });

            // Act
            EmailTemplateDTO result = emailService.createTemplate(dto);

            // Assert
            assertNotNull(result);
            assertEquals(dto.getName(), result.getName());
            verify(templateMapper).insert(any(EmailTemplate.class));
        }

        @Test
        @DisplayName("should get all templates")
        void shouldGetAllTemplates() {
            // Arrange
            EmailTemplate template1 = createTestTemplate();
            EmailTemplate template2 = new EmailTemplate();
            template2.setId("template-2");
            template2.setName("Another Template");

            when(templateMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(template1, template2));

            // Act
            List<EmailTemplateDTO> result = emailService.getAllTemplates();

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should get template by ID")
        void shouldGetTemplateById() {
            // Arrange
            EmailTemplate template = createTestTemplate();
            when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(template);

            // Act
            EmailTemplateDTO result = emailService.getTemplateById(TEMPLATE_ID);

            // Assert
            assertNotNull(result);
            assertEquals(TEMPLATE_ID, result.getId());
        }

        @Test
        @DisplayName("should throw EMAIL_TEMPLATE_NOT_FOUND when template not found")
        void shouldThrowEmailTemplateNotFoundWhenTemplateNotFound() {
            // Arrange
            when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> emailService.getTemplateById(TEMPLATE_ID)
            );
            assertEquals(EmailErrorCode.EMAIL_TEMPLATE_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("should update template successfully")
        void shouldUpdateTemplateSuccessfully() {
            // Arrange
            EmailTemplate template = createTestTemplate();
            UpdateTemplateDTO dto = new UpdateTemplateDTO();
            dto.setName("Updated Template");
            dto.setSubject("Updated subject");
            dto.setBody("<p>Updated body</p>");

            when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(template);
            when(templateMapper.updateById(any(EmailTemplate.class))).thenReturn(1);

            // Act
            EmailTemplateDTO result = emailService.updateTemplate(TEMPLATE_ID, dto);

            // Assert
            assertNotNull(result);
            assertEquals("Updated Template", result.getName());
        }

        @Test
        @DisplayName("should delete template successfully")
        void shouldDeleteTemplateSuccessfully() {
            // Arrange
            EmailTemplate template = createTestTemplate();
            when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(template);
            when(templateMapper.deleteById(TEMPLATE_ID)).thenReturn(1);

            // Act
            emailService.deleteTemplate(TEMPLATE_ID);

            // Assert
            verify(templateMapper).deleteById(TEMPLATE_ID);
        }

        @Test
        @DisplayName("should throw EMAIL_TEMPLATE_NOT_FOUND when deleting non-existent template")
        void shouldThrowWhenDeletingNonExistentTemplate() {
            // Arrange
            when(templateMapper.selectById(TEMPLATE_ID)).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> emailService.deleteTemplate(TEMPLATE_ID)
            );
            assertEquals(EmailErrorCode.EMAIL_TEMPLATE_NOT_FOUND, exception.getErrorCode());
        }
    }

    // ==================== Email Logs Tests ====================

    @Nested
    @DisplayName("Email Logs")
    class EmailLogsTests {

        @Test
        @DisplayName("should get email logs with pagination")
        void shouldGetEmailLogsWithPagination() {
            // Arrange
            EmailLog log1 = createTestLog();
            EmailLog log2 = new EmailLog();
            log2.setId("log-2");
            log2.setRecipient("another@example.com");
            log2.setSubject("Another Subject");
            log2.setStatus(EmailStatus.PENDING);

            EmailLogQueryDTO query = new EmailLogQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            Page<EmailLog> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Arrays.asList(log1, log2));
            mockPage.setTotal(2);

            when(logMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // Act
            PageResult<EmailLogDTO> result = emailService.getEmailLogs(query);

            // Assert
            assertNotNull(result);
            assertEquals(2L, result.getTotal());
            assertEquals(2, result.getItems().size());
        }

        @Test
        @DisplayName("should filter logs by status")
        void shouldFilterLogsByStatus() {
            // Arrange
            EmailLog log = createTestLog();
            EmailLogQueryDTO query = new EmailLogQueryDTO();
            query.setStatus(EmailStatus.SENT);

            Page<EmailLog> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Arrays.asList(log));
            mockPage.setTotal(1);

            when(logMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // Act
            PageResult<EmailLogDTO> result = emailService.getEmailLogs(query);

            // Assert
            assertEquals(EmailStatus.SENT, result.getItems().get(0).getStatus());
        }
    }

    // ==================== Email Stats Tests ====================

    @Nested
    @DisplayName("Email Stats")
    class EmailStatsTests {

        @Test
        @DisplayName("should get email statistics")
        void shouldGetEmailStatistics() {
            // Arrange
            when(logMapper.selectCount(isNull())).thenReturn(100L);
            when(logMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L);

            // Act
            EmailStatsDTO stats = emailService.getEmailStats();

            // Assert
            assertNotNull(stats);
            assertEquals(100L, stats.getTotal());
            // The stats queries use different wrappers, so we verify the calls
            verify(logMapper, times(4)).selectCount(any());
        }
    }

    // ==================== Template Rendering Tests ====================

    @Nested
    @DisplayName("Template Rendering (port adapter)")
    class TemplateRenderingTests {

        @Test
        @DisplayName("StringReplaceEmailRenderAdapter substitutes {{var}} placeholders")
        void stringReplaceAdapter_substitutesPlaceholders() {
            EmailRenderPort adapter = new com.ulticode.modules.email.port.adapter.StringReplaceEmailRenderAdapter();
            String template = "Hello {{name}}, welcome to {{app}}!";
            Map<String, Object> variables = new HashMap<>();
            variables.put("name", "John");
            variables.put("app", "UltiCode");

            String result = adapter.render(template, variables);

            assertEquals("Hello John, welcome to UltiCode!", result);
        }

        @Test
        @DisplayName("StringReplaceEmailRenderAdapter leaves unknown placeholders untouched")
        void stringReplaceAdapter_leavesUnknownPlaceholders() {
            EmailRenderPort adapter = new com.ulticode.modules.email.port.adapter.StringReplaceEmailRenderAdapter();
            String template = "Hello {{name}}, your code is {{code}}";
            Map<String, Object> variables = new HashMap<>();
            variables.put("name", "John");
            // Note: "code" variable is missing — placeholder stays as-is.

            String result = adapter.render(template, variables);

            assertEquals("Hello John, your code is {{code}}", result);
        }

        @Test
        @DisplayName("StringReplaceEmailRenderAdapter returns template verbatim for empty variables")
        void stringReplaceAdapter_emptyVariables() {
            EmailRenderPort adapter = new com.ulticode.modules.email.port.adapter.StringReplaceEmailRenderAdapter();
            String result = adapter.render("Hello World!", Collections.emptyMap());
            assertEquals("Hello World!", result);
        }

        @Test
        @DisplayName("StringReplaceEmailRenderAdapter returns null for null template")
        void stringReplaceAdapter_nullTemplate() {
            EmailRenderPort adapter = new com.ulticode.modules.email.port.adapter.StringReplaceEmailRenderAdapter();
            Map<String, Object> variables = new HashMap<>();
            variables.put("name", "John");
            String result = adapter.render(null, variables);
            assertNull(result);
        }
    }

    // ==================== Email Validation Tests ====================

    @Nested
    @DisplayName("Email Validation")
    class EmailValidationTests {

        @Test
        @DisplayName("should throw EMAIL_INVALID_RECIPIENT for invalid email")
        void shouldThrowEmailInvalidRecipient() {
            // Arrange
            SendEmailDTO dto = new SendEmailDTO();
            dto.setTo("invalid-email");
            dto.setSubject("Test Subject");

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> emailService.sendEmail(dto)
            );
            assertEquals(EmailErrorCode.EMAIL_INVALID_RECIPIENT, exception.getErrorCode());
        }

        @Test
        @DisplayName("should throw EMAIL_INVALID_RECIPIENT for empty email")
        void shouldThrowEmailInvalidRecipientForEmptyEmail() {
            // Arrange
            SendEmailDTO dto = new SendEmailDTO();
            dto.setTo("");
            dto.setSubject("Test Subject");

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> emailService.sendEmail(dto)
            );
            assertEquals(EmailErrorCode.EMAIL_INVALID_RECIPIENT, exception.getErrorCode());
        }
    }
}
