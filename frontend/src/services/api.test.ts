import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import api from './api';

type MemoryStorage = {
  data: Record<string, string>;
  getItem: (key: string) => string | null;
  setItem: (key: string, value: string) => void;
  removeItem: (key: string) => void;
  clear: () => void;
};

function memoryStorage(): MemoryStorage {
  const data: Record<string, string> = {};
  return {
    data,
    getItem: (key) => data[key] ?? null,
    setItem: (key, value) => {
      data[key] = String(value);
    },
    removeItem: (key) => {
      delete data[key];
    },
    clear: () => {
      Object.keys(data).forEach((key) => delete data[key]);
    },
  };
}

describe('axiosInterceptors', () => {
  const storage = memoryStorage();
  const location = { href: 'http://localhost/jobs', pathname: '/jobs' };

  beforeEach(() => {
    storage.clear();
    location.href = 'http://localhost/jobs';
    location.pathname = '/jobs';
    Object.defineProperty(globalThis, 'localStorage', { value: storage, configurable: true });
    Object.defineProperty(globalThis, 'window', { value: { location }, configurable: true });
  });

  afterEach(() => {
    storage.clear();
  });

  it('injects bearer token when one is stored', async () => {
    storage.setItem('token', 'jwt-token');
    const config = await requestFulfilled({ headers: {} as Record<string, string>, url: '/jobs' });
    expect(config.headers.Authorization).toBe('Bearer jwt-token');
  });

  it('leaves headers unchanged when no token exists', async () => {
    const config = await requestFulfilled({ headers: {} as Record<string, string>, url: '/jobs' });
    expect(config.headers.Authorization).toBeUndefined();
  });

  it('rejects request interceptor errors', async () => {
    await expect(requestRejected(new Error('network'))).rejects.toThrow('network');
  });

  it('passes through successful responses', async () => {
    const response = { data: { ok: true }, status: 200 };
    await expect(responseFulfilled(response)).resolves.toBe(response);
  });

  it('does not logout on 401 from login', async () => {
    storage.setItem('token', 'jwt-token');
    await expect(responseRejected({
      config: { url: '/auth/login' },
      response: { status: 401 },
    })).rejects.toBeTruthy();
    expect(storage.getItem('token')).toBe('jwt-token');
    expect(location.href).toBe('http://localhost/jobs');
  });

  it('clears session and redirects to login on 401', async () => {
    storage.setItem('token', 'jwt-token');
    storage.setItem('user', '{}');
    storage.setItem('role', 'CANDIDATE');
    await expect(responseRejected({
      config: { url: '/jobs' },
      response: { status: 401 },
    })).rejects.toBeTruthy();
    expect(storage.getItem('token')).toBeNull();
    expect(location.href).toBe('/login');
  });

  it('redirects admin routes to admin login', async () => {
    storage.setItem('token', 'jwt-token');
    location.pathname = '/admin/users';
    await expect(responseRejected({
      config: { url: '/admin/users' },
      response: { status: 401 },
    })).rejects.toBeTruthy();
    expect(location.href).toBe('/admin/login');
  });

  it('does not redirect when already on an auth route', async () => {
    storage.setItem('token', 'jwt-token');
    location.pathname = '/login';
    await expect(responseRejected({
      config: { url: '/jobs' },
      response: { status: 401 },
    })).rejects.toBeTruthy();
    expect(storage.getItem('token')).toBeNull();
    expect(location.href).toBe('http://localhost/jobs');
  });

  it('ignores non-401 errors', async () => {
    storage.setItem('token', 'jwt-token');
    await expect(responseRejected({
      config: { url: '/jobs' },
      response: { status: 500 },
    })).rejects.toBeTruthy();
    expect(storage.getItem('token')).toBe('jwt-token');
  });
});

function interceptorHandlers(manager: { handlers?: Array<{ fulfilled?: Function; rejected?: Function }> }) {
  return manager.handlers ?? [];
}

async function requestFulfilled(config: { headers: Record<string, string>; url: string }) {
  const handler = interceptorHandlers((api.interceptors.request as unknown) as { handlers?: Array<{ fulfilled?: Function }> })[0];
  return handler.fulfilled!(config);
}

async function requestRejected(error: Error) {
  const handler = interceptorHandlers((api.interceptors.request as unknown) as { handlers?: Array<{ rejected?: Function }> })[0];
  return handler.rejected!(error);
}

async function responseFulfilled(response: unknown) {
  const handler = interceptorHandlers((api.interceptors.response as unknown) as { handlers?: Array<{ fulfilled?: Function }> })[0];
  return handler.fulfilled!(response);
}

async function responseRejected(error: unknown) {
  const handler = interceptorHandlers((api.interceptors.response as unknown) as { handlers?: Array<{ rejected?: Function }> })[0];
  return handler.rejected!(error);
}
