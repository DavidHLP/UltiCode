package com.ulticode.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Reset password request DTO.
 */
@Data
public class ResetPasswordDTO {

    /**
     * Password reset token from email
     */
    @NotBlank(message = "Token不能为空")
    private String token;

    /**
     * New password
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度6-20位")
    private String newPassword;
}
