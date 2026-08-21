import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post, put, delete: del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('./api', () => ({ api: { get, post, put, delete: del } }));

import { authService } from './authService';

describe('authService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('getConfig calls GET /auth/config', async () => {
    get.mockResolvedValueOnce({ data: { googleOAuthEnabled: true } });
    const result = await authService.getConfig();
    expect(get).toHaveBeenCalledWith('/auth/config');
    expect(result.googleOAuthEnabled).toBe(true);
  });

  it('login calls POST /auth/login', async () => {
    const creds = { email: 'a@b.c', password: 'pass' };
    const response = { token: 'jwt', user: { id: '1' } };
    post.mockResolvedValueOnce({ data: response });
    const result = await authService.login(creds);
    expect(post).toHaveBeenCalledWith('/auth/login', creds);
    expect(result.token).toBe('jwt');
  });

  it('register calls POST /auth/register', async () => {
    const data = { email: 'a@b.c', password: 'pass', role: 'CANDIDATE' as const };
    post.mockResolvedValueOnce({ data: { token: null, user: { id: '1' } } });
    const result = await authService.register(data);
    expect(post).toHaveBeenCalledWith('/auth/register', data);
    expect(result.token).toBeNull();
  });

  it('forgotPassword calls POST /auth/forgot-password', async () => {
    post.mockResolvedValueOnce({ data: { message: 'sent' } });
    const result = await authService.forgotPassword({ email: 'a@b.c' });
    expect(post).toHaveBeenCalledWith('/auth/forgot-password', { email: 'a@b.c' });
    expect(result.message).toBe('sent');
  });

  it('resetPassword calls POST /auth/reset-password', async () => {
    post.mockResolvedValueOnce({ data: { message: 'ok' } });
    await authService.resetPassword({ token: 't', password: 'p' });
    expect(post).toHaveBeenCalledWith('/auth/reset-password', { token: 't', password: 'p' });
  });

  it('verifyEmail calls POST /auth/verify-email', async () => {
    post.mockResolvedValueOnce({ data: { id: '1' } });
    const result = await authService.verifyEmail('token-abc');
    expect(post).toHaveBeenCalledWith('/auth/verify-email', { token: 'token-abc' });
    expect(result.id).toBe('1');
  });

  it('resendVerification calls POST /auth/resend-verification', async () => {
    post.mockResolvedValueOnce({ data: { message: 'sent' } });
    await authService.resendVerification('a@b.c');
    expect(post).toHaveBeenCalledWith('/auth/resend-verification', { email: 'a@b.c' });
  });

  it('completeOauthRole calls POST /auth/oauth/complete-role', async () => {
    post.mockResolvedValueOnce({ data: { token: 'jwt', user: {} } });
    await authService.completeOauthRole('token', 'CANDIDATE');
    expect(post).toHaveBeenCalledWith('/auth/oauth/complete-role', { token: 'token', role: 'CANDIDATE' });
  });

  it('logout calls POST /auth/logout', async () => {
    post.mockResolvedValueOnce({});
    await authService.logout();
    expect(post).toHaveBeenCalledWith('/auth/logout');
  });

  it('getCurrentUser calls GET /auth/me', async () => {
    get.mockResolvedValueOnce({ data: { id: '1', email: 'a@b.c' } });
    const result = await authService.getCurrentUser();
    expect(get).toHaveBeenCalledWith('/auth/me');
    expect(result.email).toBe('a@b.c');
  });

  it('getAccount calls GET /auth/account', async () => {
    get.mockResolvedValueOnce({ data: { email: 'a@b.c' } });
    const result = await authService.getAccount();
    expect(get).toHaveBeenCalledWith('/auth/account');
    expect(result.email).toBe('a@b.c');
  });

  it('changePassword calls PUT /auth/account/password', async () => {
    put.mockResolvedValueOnce({ data: { message: 'ok' } });
    await authService.changePassword({ currentPassword: 'old', newPassword: 'new' });
    expect(put).toHaveBeenCalledWith('/auth/account/password', { currentPassword: 'old', newPassword: 'new' });
  });

  it('deactivateAccount calls POST /auth/account/deactivate', async () => {
    post.mockResolvedValueOnce({ data: { message: 'ok' } });
    await authService.deactivateAccount('password123');
    expect(post).toHaveBeenCalledWith('/auth/account/deactivate', { currentPassword: 'password123' });
  });
});
