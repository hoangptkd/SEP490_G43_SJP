import { beforeEach, describe, expect, it, vi } from 'vitest';

const memory = vi.hoisted(() => {
  const data: Record<string, string> = {};
  return {
    data,
    localStorage: {
      getItem: (key: string) => data[key] ?? null,
      setItem: (key: string, value: string) => {
        data[key] = String(value);
      },
      removeItem: (key: string) => {
        delete data[key];
      },
      clear: () => {
        Object.keys(data).forEach((key) => delete data[key]);
      },
    },
  };
});

Object.defineProperty(globalThis, 'localStorage', {
  value: memory.localStorage,
  configurable: true,
});

import { clearAuthSession, getStoredUser, getToken, setAuthSession } from './authStorage';
import { getSubscriptionPlansPath, parseApiError } from './planLimits';

const user = {
  id: 'u1',
  email: 'employer@srp.test',
  role: 'EMPLOYER' as const,
  status: 'ACTIVE' as const,
  emailVerified: true,
};

describe('authStorage', () => {
  beforeEach(() => {
    memory.localStorage.clear();
  });

  it('returns null token and user when empty', () => {
    expect(getToken()).toBeNull();
    expect(getStoredUser()).toBeNull();
  });

  it('persists token, user and role', () => {
    setAuthSession('jwt-token', user);
    expect(getToken()).toBe('jwt-token');
    expect(getStoredUser()).toEqual(user);
    expect(memory.data.role).toBe('EMPLOYER');
  });

  it('returns null for malformed stored user json', () => {
    memory.localStorage.setItem('user', '{not-json');
    expect(getStoredUser()).toBeNull();
  });

  it('clears the whole auth session', () => {
    setAuthSession('jwt-token', user);
    clearAuthSession();
    expect(getToken()).toBeNull();
    expect(getStoredUser()).toBeNull();
    expect(memory.data.role).toBeUndefined();
  });
});

describe('planLimits', () => {
  beforeEach(() => {
    memory.localStorage.clear();
  });

  it('marks 402 and PLAN_LIMIT_REACHED as upgrade errors', () => {
    expect(parseApiError({
      response: { status: 402, data: { message: 'Hết lượt', code: 'PLAN_LIMIT_REACHED' } },
    }).isPlanLimit).toBe(true);
  });

  it('returns a generic message for unknown errors', () => {
    expect(parseApiError('boom')).toEqual({ message: 'Có lỗi xảy ra', isPlanLimit: false });
  });

  it('keeps the backend message for ordinary api errors', () => {
    const parsed = parseApiError({
      response: { status: 400, data: { message: 'Email đã tồn tại', code: 'EMAIL_EXISTS' } },
    });
    expect(parsed.message).toBe('Email đã tồn tại');
    expect(parsed.code).toBe('EMAIL_EXISTS');
    expect(parsed.isPlanLimit).toBe(false);
  });

  it('routes employers to employer plans', () => {
    setAuthSession('jwt-token', user);
    expect(getSubscriptionPlansPath()).toBe('/employer/subscription/plans');
  });

  it('routes candidates to candidate plans', () => {
    setAuthSession('jwt-token', { ...user, role: 'CANDIDATE' });
    expect(getSubscriptionPlansPath()).toBe('/candidate/subscription/plans');
  });
});
