import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock('./api', () => ({ api: { get, post } }));

import { billingService } from './billingService';

describe('billingService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('listPlans calls GET /billing/plans', async () => {
    get.mockResolvedValueOnce({ data: [{ id: 'p1', name: 'Free' }] });
    const result = await billingService.listPlans();
    expect(get).toHaveBeenCalledWith('/billing/plans');
    expect(result).toHaveLength(1);
    expect(result[0].name).toBe('Free');
  });

  it('getMySubscription calls GET /billing/me', async () => {
    get.mockResolvedValueOnce({ data: { planName: 'Pro', status: 'active' } });
    const result = await billingService.getMySubscription();
    expect(get).toHaveBeenCalledWith('/billing/me');
    expect(result.planName).toBe('Pro');
  });

  it('checkout calls POST /billing/checkout', async () => {
    post.mockResolvedValueOnce({ data: { paymentUrl: 'https://pay.test' } });
    const result = await billingService.checkout('plan-1', 'payos');
    expect(post).toHaveBeenCalledWith('/billing/checkout', { planId: 'plan-1', paymentMethod: 'payos' });
    expect(result.paymentUrl).toBe('https://pay.test');
  });

  it('checkout uses default paymentMethod', async () => {
    post.mockResolvedValueOnce({ data: {} });
    await billingService.checkout('plan-1');
    expect(post).toHaveBeenCalledWith('/billing/checkout', { planId: 'plan-1', paymentMethod: 'payos' });
  });

  it('getPaymentStatus calls GET /billing/payments/:id', async () => {
    get.mockResolvedValueOnce({ data: { status: 'COMPLETED' } });
    const result = await billingService.getPaymentStatus('pay-1');
    expect(get).toHaveBeenCalledWith('/billing/payments/pay-1');
    expect(result.status).toBe('COMPLETED');
  });

  it('getBankTransfer calls GET /billing/payments/:id/bank-transfer', async () => {
    get.mockResolvedValueOnce({ data: { bankName: 'VCB', accountNumber: '123' } });
    const result = await billingService.getBankTransfer('pay-1');
    expect(get).toHaveBeenCalledWith('/billing/payments/pay-1/bank-transfer');
    expect(result.bankName).toBe('VCB');
  });

  it('getMyPaymentHistory calls GET /billing/history', async () => {
    get.mockResolvedValueOnce({ data: [{ id: 'p1' }] });
    const result = await billingService.getMyPaymentHistory();
    expect(get).toHaveBeenCalledWith('/billing/history');
    expect(result).toHaveLength(1);
  });
});
