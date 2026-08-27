import { api } from './api';
import type { CheckoutResult, FeatureUsage, PaymentStatus, PlanCatalogItem, BankTransferInfo } from '../types/billing';

export interface UserSubscription {
  planId?: string | null;
  planName: string;
  status: string;
  price: number;
  currency: string;
  benefits: string[];
  startedAt?: string | null;
  expiresAt?: string | null;
  usages?: FeatureUsage[];
}

export const billingService = {
  listPlans: async (): Promise<PlanCatalogItem[]> => {
    const response = await api.get<PlanCatalogItem[]>('/billing/plans');
    return response.data;
  },

  getMySubscription: async (): Promise<UserSubscription> => {
    const response = await api.get<UserSubscription>('/billing/me');
    return response.data;
  },

  checkout: async (planId: string, paymentMethod = 'payos'): Promise<CheckoutResult> => {
    const response = await api.post<CheckoutResult>('/billing/checkout', { planId, paymentMethod });
    return response.data;
  },

  getPaymentStatus: async (paymentId: string): Promise<PaymentStatus> => {
    const response = await api.get<PaymentStatus>(`/billing/payments/${paymentId}`);
    return response.data;
  },

  getBankTransfer: async (paymentId: string): Promise<BankTransferInfo> => {
    const response = await api.get<BankTransferInfo>(`/billing/payments/${paymentId}/bank-transfer`);
    return response.data;
  },

  getMyPaymentHistory: async (): Promise<PaymentStatus[]> => {
    const response = await api.get<PaymentStatus[]>('/billing/history');
    return response.data;
  },
};
