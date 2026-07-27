package com.ulticode.auth.service;

import com.ulticode.auth.dto.LoginDTO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.dto.RegisterDTO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Service interface for authentication operations inside backend-auth.
 */
public interface AuthService {

    LoginResponse login(LoginDTO loginDTO, HttpServletResponse response);

    LoginResponse register(RegisterDTO registerDTO, HttpServletResponse response);

    LoginResponse refresh(String refreshToken, HttpServletResponse response);

    void logout(String refreshToken, HttpServletResponse response);
}
