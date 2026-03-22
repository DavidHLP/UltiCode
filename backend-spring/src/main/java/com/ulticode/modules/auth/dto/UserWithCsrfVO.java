package com.ulticode.modules.auth.dto;

import com.ulticode.modules.user.dto.UserVO;
import lombok.Data;

@Data
public class UserWithCsrfVO {
    private UserVO user;
    private String csrfToken;
}
