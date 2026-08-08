package com.ulticode.modules.backup.controller;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.backup.dto.BackupQueryDTO;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.projection.BackupReadProjection;
import com.ulticode.modules.backup.service.BackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(BackupControllerAuthorizationTest.TestConfig.class)
@DisplayName("BackupController Live Method Security Authorization Test")
class BackupControllerAuthorizationTest {

    @jakarta.annotation.Resource
    private BackupController backupController;

    @jakarta.annotation.Resource
    private BackupService backupService;

    @jakarta.annotation.Resource
    private BackupReadProjection backupReadProjection;

    // 1. Non-admin denial tests (USER role) for all 6 route families
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER role denied createBackup -> throws AccessDeniedException (403)")
    void userDeniedCreateBackup() {
        assertThatThrownBy(() -> backupController.createBackup(new CreateBackupDTO()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER role denied getBackups -> throws AccessDeniedException (403)")
    void userDeniedGetBackups() {
        assertThatThrownBy(() -> backupController.getBackups(new BackupQueryDTO()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER role denied getBackupById -> throws AccessDeniedException (403)")
    void userDeniedGetBackupById() {
        assertThatThrownBy(() -> backupController.getBackupById("b1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER role denied downloadBackup -> throws AccessDeniedException (403)")
    void userDeniedDownloadBackup() {
        assertThatThrownBy(() -> backupController.downloadBackup("b1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER role denied restoreBackup -> throws AccessDeniedException (403)")
    void userDeniedRestoreBackup() {
        assertThatThrownBy(() -> backupController.restoreBackup("b1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER role denied deleteBackup -> throws AccessDeniedException (403)")
    void userDeniedDeleteBackup() {
        assertThatThrownBy(() -> backupController.deleteBackup("b1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // 2. ADMIN role access tests for all 6 route families
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN role allowed all 6 endpoints")
    void adminAllowedAllEndpoints() {
        when(backupService.createBackup(any(), any())).thenReturn(new BackupVO());
        when(backupService.restoreBackup(anyString(), any())).thenReturn(new BackupVO());

        assertThatCode(() -> backupController.createBackup(new CreateBackupDTO())).doesNotThrowAnyException();
        assertThatCode(() -> backupController.getBackups(new BackupQueryDTO())).doesNotThrowAnyException();
        assertThatCode(() -> backupController.getBackupById("b1")).doesNotThrowAnyException();
        assertThatCode(() -> backupController.restoreBackup("b1")).doesNotThrowAnyException();
        assertThatCode(() -> backupController.deleteBackup("b1")).doesNotThrowAnyException();
    }

    // 3. SUPER_ADMIN role access tests for all 6 route families
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("SUPER_ADMIN role allowed all 6 endpoints")
    void superAdminAllowedAllEndpoints() {
        when(backupService.createBackup(any(), any())).thenReturn(new BackupVO());
        when(backupService.restoreBackup(anyString(), any())).thenReturn(new BackupVO());

        assertThatCode(() -> backupController.createBackup(new CreateBackupDTO())).doesNotThrowAnyException();
        assertThatCode(() -> backupController.getBackups(new BackupQueryDTO())).doesNotThrowAnyException();
        assertThatCode(() -> backupController.getBackupById("b1")).doesNotThrowAnyException();
        assertThatCode(() -> backupController.restoreBackup("b1")).doesNotThrowAnyException();
        assertThatCode(() -> backupController.deleteBackup("b1")).doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        public BackupService backupService() {
            return mock(BackupService.class);
        }

        @Bean
        public BackupReadProjection backupReadProjection() {
            return mock(BackupReadProjection.class);
        }

        @Bean
        public CurrentUserProvider currentUserProvider() {
            return mock(CurrentUserProvider.class);
        }

        @Bean
        public BackupController backupController(BackupService backupService,
                                                 BackupReadProjection backupReadProjection,
                                                 CurrentUserProvider currentUserProvider) {
            return new BackupController(backupService, backupReadProjection, currentUserProvider);
        }
    }
}
