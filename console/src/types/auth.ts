/**
 * Authentication types aligned with backend API
 *
 * Backend reference:
 * - LoginDTO, RegisterDTO, LoginResponse, UserVO, UserWithCsrfVO
 * - in backend-spring/src/main/java/com/ulticode/modules/auth/
 */

/**
 * User information (matches backend UserVO)
 */
export interface User {
  id: string;
  username: string;
  name: string;
  email: string;
  avatar?: string;
  bio?: string;
  company?: string;
  github?: string;
  location?: string;
  twitter?: string;
  website?: string;
  preferredLanguage?: string;
  role: string;
  isActive: boolean;
  joinedAt: string; // ISO 8601 format from LocalDateTime
  lastLoginAt?: string; // ISO 8601 format from LocalDateTime
}

/**
 * Login request (matches backend LoginDTO)
 */
export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * Registration request (matches backend RegisterDTO)
 */
export interface RegisterRequest {
  username: string;
  password: string;
  email?: string;
  name?: string;
}

/**
 * Login/Registration response (matches backend LoginResponse)
 * Note: This is the inner data field from Result<LoginResponse>
 */
export interface LoginResponse {
  csrfToken: string;
  user: User;
}

/**
 * /auth/me response (matches backend UserWithCsrfVO)
 * Note: This is the inner data field from Result<UserWithCsrfVO>
 */
export interface UserWithCsrfResponse {
  user: User;
  csrfToken: string;
}

/**
 * Forgot password request
 */
export interface ForgotPasswordRequest {
  email: string;
}

/**
 * Reset password request
 */
export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}
