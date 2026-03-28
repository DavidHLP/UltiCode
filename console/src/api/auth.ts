import { apiGet, apiPost } from "@/utils/request";

export interface LoginCredentials {
  username: string;
  password: string;
}

export interface RegisterDto {
  username: string;
  password: string;
  email?: string;
  name?: string;
}

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

export interface LoginResponse {
  code: number;
  message: string;
  data: {
    csrfToken: string;
    user: {
      id: string;
      username: string;
      name: string;
      email: string;
      avatar?: string;
      role: string;
      isActive: boolean;
      joinedAt: string;
      lastLoginAt?: string;
    };
  };
  traceId: string;
}

export const authApi = {
  async login(
    credentials: LoginCredentials,
  ): Promise<{ csrfToken: string; user: User }> {
    return apiPost<{ csrfToken: string; user: User }>(
      "/auth/login",
      credentials,
    );
  },

  async register(
    data: RegisterDto,
  ): Promise<{ csrfToken: string; user: User }> {
    return apiPost<{ csrfToken: string; user: User }>("/auth/register", data);
  },

  async logout(): Promise<void> {
    return apiPost("/auth/logout");
  },

  async getCurrentUser(): Promise<User> {
    const response = await apiGet<{ csrfToken?: string; user: User }>(
      "/auth/me",
    );
    return response.user;
  },

  async forgotPassword(email: string): Promise<{ message: string }> {
    return apiPost("/auth/forgot-password", { email });
  },

  async resetPassword(
    token: string,
    newPassword: string,
  ): Promise<{ message: string }> {
    return apiPost("/auth/reset-password", { token, newPassword });
  },
};
