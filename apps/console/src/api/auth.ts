/**
 * Authentication API
 *
 * All API calls use the unified types from @/types/auth
 * The request.ts utility automatically unwraps the backend Result<T> envelope
 */

import { apiGet, apiPost } from "@/utils/request";
import type {
  User,
  LoginRequest,
  RegisterRequest,
  LoginResponse,
  UserWithCsrfResponse,
  ForgotPasswordRequest,
  ResetPasswordRequest,
} from "@/types/auth";

/**
 * Authentication API methods
 */
export const authApi = {
  /**
   * Login with username and password
   * POST /auth/login → Result<LoginResponse>
   * Returns: { csrfToken: string, user: User }
   */
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    return apiPost<LoginResponse>("/auth/login", credentials);
  },

  /**
   * Register a new user account
   * POST /auth/register → Result<LoginResponse>
   * Returns: { csrfToken: string, user: User }
   */
  async register(data: RegisterRequest): Promise<LoginResponse> {
    return apiPost<LoginResponse>("/auth/register", data);
  },

  /**
   * Logout current user
   * POST /auth/logout → Result<Void>
   */
  async logout(): Promise<void> {
    return apiPost<void>("/auth/logout");
  },

  /**
   * Get current authenticated user
   * GET /auth/me → Result<UserWithCsrfResponse>
   * Returns: { user: User, csrfToken: string }
   *
   * Note: We return only the User part for simplicity
   * The CSRF token is automatically stored by the auth store
   */
  async getCurrentUser(): Promise<User> {
    // /auth/me returns { user: User, csrfToken: string }
    const response = await apiGet<UserWithCsrfResponse>("/auth/me");
    return response.user;
  },

  /**
   * Send password reset email
   * POST /auth/forgot-password → Result<Void>
   */
  async forgotPassword(request: ForgotPasswordRequest): Promise<void> {
    return apiPost<void>("/auth/forgot-password", request);
  },

  /**
   * Reset password with token from email
   * POST /auth/reset-password → Result<Void>
   */
  async resetPassword(request: ResetPasswordRequest): Promise<void> {
    return apiPost<void>("/auth/reset-password", request);
  },
};

// Re-export types for convenience
export type {
  User,
  LoginRequest,
  RegisterRequest,
  LoginResponse,
  UserWithCsrfResponse,
  ForgotPasswordRequest,
  ResetPasswordRequest,
};
