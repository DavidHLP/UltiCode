package com.ulticode.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.port.UserWritePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route regression test for {@link UserCredentialController}
 * (P7-RELOCATE-USER-REMAINDER-001).
 *
 * <p>Ensures the {@code /users/me/password} route survives the split
 * from the relocated app-side {@code UserController}. This endpoint is
 * Auth-owned and must not be silently dropped.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserCredentialController route regression")
class UserCredentialControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserWritePort userWritePort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        UserCredentialController controller = new UserCredentialController(userWritePort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("PATCH /users/me/password delegates to UserWritePort.changePassword")
    void changePasswordRouteExists() throws Exception {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setCurrentPassword("oldPass123");
        dto.setNewPassword("newPass456");
        dto.setConfirmPassword("newPass456");

        doNothing().when(userWritePort).changePassword(any(ChangePasswordDTO.class));

        mockMvc.perform(patch("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(userWritePort).changePassword(any(ChangePasswordDTO.class));
    }
}
