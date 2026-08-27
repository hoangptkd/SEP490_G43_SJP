import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock('./api', () => ({ api: { get } }));

import { publicSettingsService } from './publicSettingsService';

describe('publicSettingsService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('get calls GET /settings/public and merges with defaults', async () => {
    get.mockResolvedValueOnce({ data: { siteName: 'Custom Site', aiInterviewEnabled: false } });
    const result = await publicSettingsService.get();
    expect(get).toHaveBeenCalledWith('/settings/public');
    expect(result.siteName).toBe('Custom Site');
    expect(result.aiInterviewEnabled).toBe(false);
    // Default values should be preserved for unset fields
    expect(result.supportEmail).toBe('support@sjp.local');
    expect(result.themeMode).toBe('light');
  });

  it('get returns defaults when server returns empty object', async () => {
    get.mockResolvedValueOnce({ data: {} });
    const result = await publicSettingsService.get();
    expect(result.siteName).toBe('Smart Recruitment Portal');
    expect(result.maintenanceMode).toBe(false);
    expect(result.paymentGatewayProvider).toBe('payos');
  });

  it('defaults are exposed as static property', () => {
    expect(publicSettingsService.defaults.siteName).toBe('Smart Recruitment Portal');
    expect(publicSettingsService.defaults.themePrimaryColor).toBe('#00507d');
  });
});
