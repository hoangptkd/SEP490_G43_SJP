import { getStoredUser } from './authStorage';

export type ApiErrorInfo = {
  message: string;
  code?: string;
  upgradeHint?: string;
  isPlanLimit: boolean;
};

export function parseApiError(error: unknown): ApiErrorInfo {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as {
      response?: {
        status?: number;
        data?: { message?: string; code?: string; upgradeHint?: string };
      };
    }).response;
    const code = response?.data?.code;
    const message = response?.data?.message || 'Có lỗi xảy ra';
    const isPlanLimit = code === 'PLAN_LIMIT_REACHED' || response?.status === 402;
    return {
      message,
      code,
      upgradeHint: response?.data?.upgradeHint,
      isPlanLimit,
    };
  }
  return { message: 'Có lỗi xảy ra', isPlanLimit: false };
}

export function getSubscriptionPlansPath(): string {
  const user = getStoredUser();
  if (user?.role === 'EMPLOYER') {
    return '/employer/subscription/plans';
  }
  return '/candidate/subscription/plans';
}
