package com.ulticode.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Size(min = 8, max = 128, message = "密码长度8-128位")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[\\S]+$",
            message = "密码必须包含至少一个大写字母、一个小写字母和一个数字，且不能包含空格")
    private String newPassword;
}
