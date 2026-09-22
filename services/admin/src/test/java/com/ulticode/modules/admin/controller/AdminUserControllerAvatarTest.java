package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.query.AdminUserDetailQuery;
import com.ulticode.modules.admin.service.UserManagementService;
import com.ulticode.modules.admin.service.UserPermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerAvatarTest {

    @Mock
    private UserManagementService userManagementService;
    @Mock
    private UserPermissionService userPermissionService;
    @Mock
    private AdminUserDetailQuery adminUserDetailQuery;
    @Mock
    private AdminUserProjection adminUserProjection;

    @Test
    void uploadsAvatarThroughAuditedUserManagementServiceAndReturnsDisplayUrl() {
        AdminUserController controller = new AdminUserController(
                userManagementService, userPermissionService, adminUserDetailQuery, adminUserProjection);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});
        when(userManagementService.uploadAvatar("user-1", file))
                .thenReturn("/api/users/avatars/user-1/uuid.png");

        Result<String> result = controller.uploadAvatar("user-1", file);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isEqualTo("/api/users/avatars/user-1/uuid.png");
        verify(userManagementService).uploadAvatar("user-1", file);
    }

    @Test
    void endpointRequiresAdminRolesAndMultipartFile() throws NoSuchMethodException {
        var method = AdminUserController.class.getMethod(
                "uploadAvatar", String.class, org.springframework.web.multipart.MultipartFile.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMIN', 'SUPER_ADMIN')");
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping.consumes()).containsExactly("multipart/form-data");
    }
}
