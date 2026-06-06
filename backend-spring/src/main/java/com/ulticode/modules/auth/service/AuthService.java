package com.ulticode.modules.auth.service;

import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.dto.RegisterDTO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Service interface for authentication operations.
 */
public interface AuthService {

    /**
     * Authenticate a user and return tokens.
     *
     * @param loginDTO the login credentials
     * @param response the HTTP response to set cookies
     * @return login response with CSRF token and user info
     */
    LoginResponse login(LoginDTO loginDTO, HttpServletResponse response);

    /**
     * Register a new user.
     *
     * @param registerDTO the registration data
     * @param response    the HTTP response to set cookies
     * @return login response with CSRF token and user info
     */
    LoginResponse register(RegisterDTO registerDTO, HttpServletResponse response);

    /**
     * Refresh the access token using a valid refresh token.
     *
     * @param refreshToken the refresh token
     * @param response     the HTTP response to set cookies
     * @return login response with CSRF token and user info
     */
    LoginResponse refresh(String refreshToken, HttpServletResponse response);

    /**
     * Logout the current user by clearing the auth cookie.
     *
     * @param response the HTTP response to clear cookies
     */
    void logout(String refreshToken, HttpServletResponse response);
}
