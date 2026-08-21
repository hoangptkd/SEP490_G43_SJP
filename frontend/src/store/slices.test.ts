import { beforeEach, describe, expect, it, vi } from 'vitest';

const memory = vi.hoisted(() => {
  const data: Record<string, string> = {};
  const localStorage = {
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
  };
  Object.defineProperty(globalThis, 'localStorage', {
    value: localStorage,
    configurable: true,
  });
  return { data, localStorage };
});

import authReducer, { clearError as clearAuthError, login, logout, register } from '../store/slices/authSlice';
import jobReducer, { clearCurrentJob, clearError as clearJobError, fetchJobById, fetchJobs, setFilters } from '../store/slices/jobSlice';
import interviewReducer, { clearError as clearInterviewError, createInterview, fetchInterviewsByApplication, setCurrentInterview } from '../store/slices/interviewSlice';
import type { Interview } from '@/types/interview';
import type { Job } from '@/types/job';

const sampleUser = {
  id: 'u1',
  email: 'candidate@srp.test',
  role: 'CANDIDATE' as const,
  status: 'ACTIVE' as const,
  emailVerified: true,
};

const sampleJob = {
  id: 'j1',
  title: 'Java Developer',
  description: 'Backend',
  location: 'Hanoi',
  requirements: [],
  skills: [],
  company: { id: 'c1', name: 'SRP' },
} as Job;

const sampleInterview: Interview = {
  id: 1,
  applicationId: 9,
  type: 'AI_INTERVIEW',
  scheduledAt: '2026-08-20T09:00:00',
  durationMinutes: 30,
  status: 'SCHEDULED',
  createdAt: '2026-08-16T09:00:00',
};

describe('reduxReducers', () => {
  beforeEach(() => {
    memory.localStorage.clear();
  });

  it('starts auth as logged out when no token is stored', () => {
    const state = authReducer(undefined, { type: 'unknown' });
    expect(state.isAuthenticated).toBe(false);
    expect(state.user).toBeNull();
    expect(state.token).toBeNull();
  });

  it('sets loading on login pending and stores token on success', () => {
    const pending = authReducer(undefined, { type: login.pending.type });
    expect(pending.loading).toBe(true);

    const fulfilled = authReducer(pending, {
      type: login.fulfilled.type,
      payload: { user: sampleUser, token: 'jwt-token' },
    });
    expect(fulfilled.isAuthenticated).toBe(true);
    expect(fulfilled.token).toBe('jwt-token');
    expect(memory.data.token).toBe('jwt-token');
  });

  it('stores login error on rejection', () => {
    const state = authReducer(undefined, {
      type: login.rejected.type,
      payload: 'Email hoặc mật khẩu không đúng',
    });
    expect(state.loading).toBe(false);
    expect(state.error).toBe('Email hoặc mật khẩu không đúng');
  });

  it('register without token keeps the user unauthenticated', () => {
    const state = authReducer(undefined, {
      type: register.fulfilled.type,
      payload: { user: sampleUser, token: null },
    });
    expect(state.user).toEqual(sampleUser);
    expect(state.isAuthenticated).toBe(false);
  });

  it('logout clears session storage', () => {
    memory.localStorage.setItem('token', 'jwt-token');
    const state = authReducer(
      {
        user: sampleUser,
        token: 'jwt-token',
        isAuthenticated: true,
        loading: false,
        error: null,
      },
      { type: logout.fulfilled.type }
    );
    expect(state.isAuthenticated).toBe(false);
    expect(state.token).toBeNull();
    expect(memory.data.token).toBeUndefined();
  });

  it('clearError resets auth error', () => {
    const state = authReducer(
      { user: null, token: null, isAuthenticated: false, loading: false, error: 'fail' },
      clearAuthError()
    );
    expect(state.error).toBeNull();
  });

  it('stores job filters', () => {
    const state = jobReducer(undefined, setFilters({ search: 'java' }));
    expect(state.filters).toEqual({ search: 'java' });
  });

  it('loads job list pagination on fetch success', () => {
    const state = jobReducer(undefined, {
      type: fetchJobs.fulfilled.type,
      payload: {
        content: [sampleJob],
        page: 1,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      },
    });
    expect(state.jobs).toHaveLength(1);
    expect(state.pagination.totalElements).toBe(1);
    expect(state.loading).toBe(false);
  });

  it('stores fetchJobs error', () => {
    const state = jobReducer(
      { ...jobReducer(undefined, { type: fetchJobs.pending.type }) },
      { type: fetchJobs.rejected.type, error: { message: 'Failed' } }
    );
    expect(state.loading).toBe(false);
    expect(state.error).toBe('Failed');
  });

  it('sets and clears the current job', () => {
    const withJob = jobReducer(undefined, {
      type: fetchJobById.fulfilled.type,
      payload: sampleJob,
    });
    expect(withJob.currentJob?.id).toBe('j1');
    expect(jobReducer(withJob, clearCurrentJob()).currentJob).toBeNull();
  });

  it('clears job error', () => {
    const errored = jobReducer(undefined, { type: fetchJobs.rejected.type, error: { message: 'x' } });
    expect(jobReducer(errored, clearJobError()).error).toBeNull();
  });

  it('loads interviews by application', () => {
    const state = interviewReducer(undefined, {
      type: fetchInterviewsByApplication.fulfilled.type,
      payload: [sampleInterview],
    });
    expect(state.interviews).toHaveLength(1);
    expect(state.loading).toBe(false);
  });

  it('appends a created interview', () => {
    const state = interviewReducer(
      {
        interviews: [],
        currentInterview: null,
        loading: false,
        error: null,
      },
      { type: createInterview.fulfilled.type, payload: sampleInterview }
    );
    expect(state.interviews[0].id).toBe(1);
  });

  it('sets current interview and clears interview error', () => {
    const withCurrent = interviewReducer(undefined, setCurrentInterview(sampleInterview));
    expect(withCurrent.currentInterview?.id).toBe(1);
    const errored = interviewReducer(undefined, {
      type: fetchInterviewsByApplication.rejected.type,
      error: { message: 'timeout' },
    });
    expect(interviewReducer(errored, clearInterviewError()).error).toBeNull();
  });
});
