package com.ulticode.modules.user.controller;

import com.ulticode.common.storage.FileStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserAvatarControllerTest {

    @Mock
    private FileStoragePort fileStorage;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserAvatarController(fileStorage)).build();
    }

    @Test
    void absentAvatarReturnsNotFound() throws Exception {
        when(fileStorage.get("app/avatars/user-1/missing.png")).thenReturn(Optional.empty());

        mockMvc.perform(get("/users/avatars/user-1/missing.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedAvatarKeyReturnsNotFoundWithoutStorageAccess() throws Exception {
        mockMvc.perform(get("/users/avatars/user-1/."))
                .andExpect(status().isNotFound());
    }

    @Test
    void presentAvatarReturnsBytesAndStorageContentType() throws Exception {
        String key = "app/avatars/user-1/avatar.png";
        byte[] bytes = {1, 2, 3};
        when(fileStorage.get(key)).thenReturn(
                Optional.of(new FileStoragePort.StoredObject(bytes, "image/png")));

        mockMvc.perform(get("/users/avatars/user-1/avatar.png"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + key + "\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=300"));
    }
}
