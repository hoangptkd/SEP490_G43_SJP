import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post, put, delete: del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('./api', () => ({ api: { get, post, put, delete: del } }));

import { adminService } from './adminService';

describe('adminService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('getDashboardStats calls GET /admin/dashboard', async () => {
    get.mockResolvedValueOnce({ data: { totalUsers: 100 } });
    const result = await adminService.getDashboardStats();
    expect(get).toHaveBeenCalledWith('/admin/dashboard');
    expect(result.totalUsers).toBe(100);
  });

  it('getStatistics calls GET /admin/dashboard/statistics', async () => {
    get.mockResolvedValueOnce({ data: { period: 'month' } });
    await adminService.getStatistics('month', 2025, 6);
    expect(get).toHaveBeenCalledWith('/admin/dashboard/statistics', {
      params: { period: 'month', year: 2025, month: 6, date: undefined },
    });
  });

  it('listUsers calls GET /admin/users', async () => {
    get.mockResolvedValueOnce({ data: [{ id: '1' }] });
    const result = await adminService.listUsers('all', 'all');
    expect(get).toHaveBeenCalledWith('/admin/users', { params: { role: 'all', status: 'all' } });
    expect(result).toHaveLength(1);
  });

  it('suspendUser calls POST /admin/users/:id/suspend', async () => {
    post.mockResolvedValueOnce({ data: { status: 'suspended' } });
    const result = await adminService.suspendUser('u-1');
    expect(post).toHaveBeenCalledWith('/admin/users/u-1/suspend');
    expect(result.status).toBe('suspended');
  });

  it('activateUser calls POST /admin/users/:id/activate', async () => {
    post.mockResolvedValueOnce({ data: { status: 'active' } });
    const result = await adminService.activateUser('u-1');
    expect(post).toHaveBeenCalledWith('/admin/users/u-1/activate');
    expect(result.status).toBe('active');
  });
});
