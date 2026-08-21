import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./authStorage', () => ({
  getStoredUser: vi.fn(),
}));

import { parseApiError, getSubscriptionPlansPath } from './planLimits';
import { getStoredUser } from './authStorage';

describe('planLimits', () => {
  beforeEach(() => vi.clearAllMocks());

  describe('parseApiError', () => {
    it('extracts message and code from axios error shape', () => {
      const error = {
        response: {
          status: 403,
          data: { message: 'Limit reached', code: 'PLAN_LIMIT_REACHED', upgradeHint: 'Upgrade to Pro' },
        },
      };
      const result = parseApiError(error);
      expect(result.message).toBe('Limit reached');
      expect(result.code).toBe('PLAN_LIMIT_REACHED');
      expect(result.upgradeHint).toBe('Upgrade to Pro');
      expect(result.isPlanLimit).toBe(true);
    });

    it('detects plan limit by 402 status code', () => {
      const error = {
        response: { status: 402, data: { message: 'Payment required' } },
      };
      const result = parseApiError(error);
      expect(result.isPlanLimit).toBe(true);
    });

    it('returns default for non-API errors', () => {
      const result = parseApiError(new Error('network'));
      expect(result.message).toBe('Có lỗi xảy ra');
      expect(result.isPlanLimit).toBe(false);
    });

    it('returns default for null/undefined', () => {
      expect(parseApiError(null).isPlanLimit).toBe(false);
      expect(parseApiError(undefined).isPlanLimit).toBe(false);
    });

    it('returns default message when response.data.message is missing', () => {
      const error = { response: { status: 500, data: {} } };
      const result = parseApiError(error);
      expect(result.message).toBe('Có lỗi xảy ra');
    });
  });

  describe('getSubscriptionPlansPath', () => {
    it('returns employer path for EMPLOYER role', () => {
      (getStoredUser as any).mockReturnValue({ role: 'EMPLOYER' });
      expect(getSubscriptionPlansPath()).toBe('/employer/subscription/plans');
    });

    it('returns candidate path for CANDIDATE role', () => {
      (getStoredUser as any).mockReturnValue({ role: 'CANDIDATE' });
      expect(getSubscriptionPlansPath()).toBe('/candidate/subscription/plans');
    });

    it('returns candidate path when no user', () => {
      (getStoredUser as any).mockReturnValue(null);
      expect(getSubscriptionPlansPath()).toBe('/candidate/subscription/plans');
    });
  });
});
