package com.ulticode.modules.user.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultUserProfileAdapterTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UuidGenerator uuidGenerator;

    @InjectMocks
    private DefaultUserProfileAdapter profileAdapter;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("user-1");
        testUser.setUsername("testuser");
        testUser.setName("Original Name");
        testUser.setEmail("test@example.com");
    }

    @Test
    @DisplayName("should update profile attributes")
    void shouldUpdateProfileAttributes() {
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setName("New Name");
        dto.setBio("New Bio");

        when(userMapper.selectById("user-1")).thenReturn(testUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UserVO vo = profileAdapter.updateProfile("user-1", dto);

        assertNotNull(vo);
        assertEquals("New Name", vo.getName());
        assertEquals("New Bio", vo.getBio());
        verify(userMapper).updateById(testUser);
    }

    @Test
    @DisplayName("should throw USER_NOT_FOUND when user does not exist")
    void shouldThrowUserNotFound() {
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setName("New Name");

        when(userMapper.selectById("nonexistent")).thenReturn(null);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> profileAdapter.updateProfile("nonexistent", dto));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("should update avatar URL directly")
    void shouldUpdateAvatarUrl() {
        profileAdapter.updateAvatarUrl("user-1", "/uploads/avatars/new.png");
        verify(userMapper).updateById(any(User.class));
    }
}
