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
  role: string;
  is_active: boolean;
  is_banned: boolean;
  joined_at: string;
}

export interface LoginResponse {
  access_token: string;
  csrf_token: string;
  user: {
    id: string;
    username: string;
    name: string;
    role: string;
  };
}

export const authApi = {
  async login(credentials: LoginCredentials): Promise<LoginResponse> {
    return apiPost<LoginResponse>("/auth/login", credentials);
  },

  async register(data: RegisterDto): Promise<LoginResponse> {
    return apiPost<LoginResponse>("/auth/register", data);
  },

  async logout(): Promise<void> {
    return apiPost("/auth/logout");
  },

  async getCurrentUser(): Promise<User> {
    return apiGet<User>("/auth/me");
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
