package com.ulticode.modules.backup.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.backup.dto.BackupQueryDTO;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import com.ulticode.modules.backup.projection.BackupReadProjection;
import com.ulticode.modules.backup.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("BackupController (Standalone MockMvc & Security Inspection)")
class BackupControllerTest {

    @Mock
    private BackupService backupService;

    @Mock
    private BackupReadProjection backupReadProjection;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private BackupController backupController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String BACKUP_ID = "b-1001";
    private static final String USER_ID = "u-2002";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(backupController)
                .setValidator(new LocalValidatorFactoryBean())
                .build();
        objectMapper = new ObjectMapper();
    }

    private BackupVO createTestVO() {
        BackupVO vo = new BackupVO();
        vo.setId(BACKUP_ID);
        vo.setFilename("backup_20260731.sql");
        vo.setSize(1024L);
        vo.setType(BackupType.FULL);
        vo.setStatus(BackupStatus.COMPLETED);
        vo.setCreatedBy(USER_ID);
        vo.setCreatedByName("Admin User");
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    @Nested
    @DisplayName("HTTP Endpoint Mapping & Contract Tests (6 Endpoints)")
    class EndpointContractTests {

        @Test
        @DisplayName("POST /admin/backups -> createBackup returns Result<BackupVO>")
        void createBackupReturnsResultVO() throws Exception {
            CreateBackupDTO dto = new CreateBackupDTO();
            dto.setType(BackupType.FULL);
            BackupVO vo = createTestVO();

            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(backupService.createBackup(eq(USER_ID), any(CreateBackupDTO.class))).thenReturn(vo);

            mockMvc.perform(post("/admin/backups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(BACKUP_ID))
                    .andExpect(jsonPath("$.data.filename").value("backup_20260731.sql"));

            verify(backupService).createBackup(eq(USER_ID), any(CreateBackupDTO.class));
        }

        @Test
        @DisplayName("GET /admin/backups -> getBackups returns Result<PageResult<BackupVO>>")
        void getBackupsReturnsPageResult() throws Exception {
            BackupVO vo = createTestVO();
            PageResult<BackupVO> page = PageResult.of(List.of(vo), 1L, 1, 10);

            when(backupReadProjection.listBackups(any(BackupQueryDTO.class))).thenReturn(page);

            mockMvc.perform(get("/admin/backups"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(BACKUP_ID));
            verify(backupReadProjection).listBackups(any(BackupQueryDTO.class));
        }

        @Test
        @DisplayName("GET /admin/backups rejects invalid pagination")
        void getBackupsRejectsInvalidPagination() throws Exception {
            mockMvc.perform(get("/admin/backups").param("page", "0"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/admin/backups").param("limit", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(backupReadProjection);
        }

        @Test
        @DisplayName("GET /admin/backups/{id} -> getBackupById returns Result<BackupVO>")
        void getBackupByIdReturnsVO() throws Exception {
            BackupVO vo = createTestVO();

            when(backupReadProjection.getById(BACKUP_ID)).thenReturn(vo);

            mockMvc.perform(get("/admin/backups/{id}", BACKUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(BACKUP_ID));

            verify(backupReadProjection).getById(BACKUP_ID);
        }

        @Test
        @DisplayName("GET /admin/backups/{id}/download -> downloadBackup returns file attachment")
        void downloadBackupReturnsFileAttachment(@TempDir Path tempDir) throws Exception {
            Path file = tempDir.resolve("backup_20260731.sql");
            Files.writeString(file, "-- SQL Backup Dump");

            when(backupService.getBackupFile(BACKUP_ID)).thenReturn(file.toFile());

            mockMvc.perform(get("/admin/backups/{id}/download", BACKUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''backup_20260731.sql"));

            verify(backupService).getBackupFile(BACKUP_ID);
        }

        @Test
        @DisplayName("POST /admin/backups/{id}/restore -> restoreBackup triggers database restore")
        void restoreBackupTriggersRestore() throws Exception {
            BackupVO vo = createTestVO();

            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(backupService.restoreBackup(BACKUP_ID, USER_ID)).thenReturn(vo);

            mockMvc.perform(post("/admin/backups/{id}/restore", BACKUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(BACKUP_ID));

            verify(backupService).restoreBackup(BACKUP_ID, USER_ID);
        }

        @Test
        @DisplayName("DELETE /admin/backups/{id} -> deleteBackup removes backup")
        void deleteBackupDeletesFile() throws Exception {
            mockMvc.perform(delete("/admin/backups/{id}", BACKUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(backupService).deleteBackup(BACKUP_ID);
        }
    }

    @Nested
    @DisplayName("Security & Route Annotation Inspection")
    class SecurityAnnotationInspectionTests {

        @Test
        @DisplayName("BackupController has @RequestMapping('/admin/backups')")
        void classHasRequestMappingAdminBackups() {
            RequestMapping reqMapping = BackupController.class.getAnnotation(RequestMapping.class);
            assertNotNull(reqMapping, "BackupController must have @RequestMapping");
            assertEquals(1, reqMapping.value().length);
            assertEquals("/admin/backups", reqMapping.value()[0]);
        }

        @Test
        @DisplayName("All 6 controller methods have @PreAuthorize requiring ADMIN or SUPER_ADMIN role")
        void allEndpointsHavePreAuthorizeAdminRole() throws NoSuchMethodException {
            Method[] methods = new Method[]{
                    BackupController.class.getMethod("createBackup", CreateBackupDTO.class),
                    BackupController.class.getMethod("getBackups", BackupQueryDTO.class),
                    BackupController.class.getMethod("getBackupById", String.class),
                    BackupController.class.getMethod("downloadBackup", String.class),
                    BackupController.class.getMethod("restoreBackup", String.class),
                    BackupController.class.getMethod("deleteBackup", String.class)
            };

            for (Method method : methods) {
                PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);
                assertNotNull(preAuth, "Method " + method.getName() + " must have @PreAuthorize annotation");
                String value = preAuth.value();
                assertTrue(value.contains("ADMIN") || value.contains("SUPER_ADMIN"),
                        "Method " + method.getName() + " @PreAuthorize must require ADMIN or SUPER_ADMIN role (actual: " + value + ")");
            }
        }
    }
}
