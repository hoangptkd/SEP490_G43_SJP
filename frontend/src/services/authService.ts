import { api } from './api';
import type { AccountView, User } from '../types/auth';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  role: 'CANDIDATE' | 'EMPLOYER';
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AuthResponse {
  token: string | null;
  user: User;
}

export interface AuthConfigResponse {
  googleOAuthEnabled: boolean;
}

export const authService = {
  getConfig: async (): Promise<AuthConfigResponse> => {
    const response = await api.get<AuthConfigResponse>('/auth/config');
    return response.data;
  },

  login: async (credentials: LoginRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/login', credentials);
    return response.data;
  },

  register: async (userData: RegisterRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/register', userData);
    return response.data;
  },

  forgotPassword: async (data: ForgotPasswordRequest): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>('/auth/forgot-password', data);
    return response.data;
  },

  resetPassword: async (data: ResetPasswordRequest): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>('/auth/reset-password', data);
    return response.data;
  },

  verifyEmail: async (token: string): Promise<User> => {
    const response = await api.post<User>('/auth/verify-email', { token });
    return response.data;
  },

  completeOauthRole: async (token: string, role: 'CANDIDATE' | 'EMPLOYER'): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/oauth/complete-role', { token, role });
    return response.data;
  },

  logout: async (): Promise<void> => {
    await api.post('/auth/logout');
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await api.get<User>('/auth/me');
    return response.data;
  },

  getAccount: async (): Promise<AccountView> => {
    const response = await api.get<AccountView>('/auth/account');
    return response.data;
  },

  changePassword: async (data: ChangePasswordRequest): Promise<{ message: string }> => {
    const response = await api.put<{ message: string }>('/auth/account/password', data);
    return response.data;
  },

  updateAvatar: async (file: File): Promise<AccountView> => {
    const data = new FormData();
    data.append('file', file);
    const response = await api.put<AccountView>('/auth/account/avatar', data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  deactivateAccount: async (currentPassword: string): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>('/auth/account/deactivate', { currentPassword });
    return response.data;
  },
};
