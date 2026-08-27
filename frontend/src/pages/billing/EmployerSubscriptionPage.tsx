import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { billingService, type UserSubscription } from '../../services/billingService';
import type { PaymentStatus } from '../../types/billing';

function formatDate(value?: string | null) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN');
}

function formatMoney(value?: number, currency = 'VND') {
  if (value == null) return '—';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value);
}

function statusMeta(status?: string) {
  const value = (status || '').toLowerCase();
  if (value === 'active') return { label: 'Đang sử dụng', bg: '#dcfce7', color: '#166534' };
  if (value === 'pending') return { label: 'Chờ kích hoạt', bg: '#fef3c7', color: '#92400e' };
  if (value === 'expired') return { label: 'Hết hạn', bg: '#fee2e2', color: '#991b1b' };
  if (value === 'cancelled') return { label: 'Đã hủy', bg: '#fee2e2', color: '#991b1b' };
  if (value === 'free') return { label: 'Gói miễn phí', bg: '#f3f4f6', color: '#374151' };
  return { label: status || '—', bg: '#f3f4f6', color: '#374151' };
}

function paymentStatusMeta(status?: string) {
  const value = (status || '').toLowerCase();
  if (value === 'paid') return { label: 'Thành công', bg: '#dcfce7', color: '#166534' };
  if (value === 'pending') return { label: 'Chờ thanh toán', bg: '#fef3c7', color: '#92400e' };
  if (value === 'failed') return { label: 'Thất bại', bg: '#fee2e2', color: '#991b1b' };
  if (value === 'cancelled') return { label: 'Đã hủy', bg: '#f3f4f6', color: '#6b7280' };
  if (value === 'refunded') return { label: 'Hoàn tiền', bg: '#e0e7ff', color: '#3730a3' };
  return { label: status || '—', bg: '#f3f4f6', color: '#374151' };
}

function paymentMethodLabel(method?: string) {
  const value = (method || '').toLowerCase();
  if (value === 'bank_transfer') return 'Chuyển khoản VietQR';
  if (value === 'payos') return 'Cổng PayOS';
  if (value === 'vnpay') return 'VNPay';
  if (value === 'momo') return 'Ví MoMo';
  if (value === 'free') return 'Miễn phí';
  return method || '—';
}

export default function EmployerSubscriptionPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const [activeTab, setActiveTab] = useState<'subscription' | 'history'>(tabParam === 'history' ? 'history' : 'subscription');
  
  const [subscription, setSubscription] = useState<UserSubscription | null>(null);
  const [history, setHistory] = useState<PaymentStatus[]>([]);
  const [loadingSubscription, setLoadingSubscription] = useState(true);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [error, setError] = useState('');
  const [historyError, setHistoryError] = useState('');

  useEffect(() => {
    setLoadingSubscription(true);
    billingService.getMySubscription()
      .then(setSubscription)
      .catch(() => setError('Không tải được thông tin gói đang dùng.'))
      .finally(() => setLoadingSubscription(false));
  }, []);

  useEffect(() => {
    if (activeTab === 'history') {
      setLoadingHistory(true);
      billingService.getMyPaymentHistory()
        .then(setHistory)
        .catch(() => setHistoryError('Không tải được lịch sử thanh toán.'))
        .finally(() => setLoadingHistory(false));
    }
  }, [activeTab]);

  const handleTabChange = (tab: 'subscription' | 'history') => {
    setActiveTab(tab);
    if (tab === 'history') {
      setSearchParams({ tab: 'history' });
    } else {
      setSearchParams({});
    }
  };

  return (
    <section>
      <div className="page-header">
        <h1>Gói dịch vụ & Thanh toán</h1>
        <p>Quản lý gói dịch vụ đăng tin tuyển dụng và theo dõi lịch sử thanh toán của bạn</p>
      </div>

      <div style={{ background: '#fff', borderRadius: '12px', border: '1px solid #e2e8f0', overflow: 'hidden', boxShadow: '0 1px 3px rgba(0,0,0,0.05)', marginBottom: 24 }}>
        {/* Navigation Tabs */}
        <div style={{ display: 'flex', borderBottom: '1px solid #e2e8f0', background: '#f8fafc' }}>
          <button
            type="button"
            onClick={() => handleTabChange('subscription')}
            style={{
              padding: '14px 22px',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'subscription' ? '2px solid #2563eb' : '2px solid transparent',
              color: activeTab === 'subscription' ? '#2563eb' : '#64748b',
              fontWeight: activeTab === 'subscription' ? 600 : 500,
              fontSize: '0.95rem',
              cursor: 'pointer',
              transition: 'all 0.2s'
            }}
          >
            💎 Gói đang sử dụng
          </button>
          <button
            type="button"
            onClick={() => handleTabChange('history')}
            style={{
              padding: '14px 22px',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'history' ? '2px solid #2563eb' : '2px solid transparent',
              color: activeTab === 'history' ? '#2563eb' : '#64748b',
              fontWeight: activeTab === 'history' ? 600 : 500,
              fontSize: '0.95rem',
              cursor: 'pointer',
              transition: 'all 0.2s'
            }}
          >
            📜 Lịch sử thanh toán
          </button>
        </div>

        {/* Tab 1: Current Subscription */}
        {activeTab === 'subscription' && (
          <div style={{ padding: 24 }}>
            {error && <p className="error" style={{ marginBottom: 16 }}>{error}</p>}
            {loadingSubscription ? (
              <p className="loading">Đang tải gói dịch vụ...</p>
            ) : subscription ? (
              <article
                style={{
                  borderRadius: 12,
                  padding: 20,
                  border: Boolean(subscription.planId) && subscription.status?.toLowerCase() === 'active' 
                    ? '2px solid #16a34a' 
                    : '1px solid #e5e7eb',
                  background: Boolean(subscription.planId) && subscription.status?.toLowerCase() === 'active'
                    ? 'linear-gradient(180deg, #f0fdf4 0%, #fff 55%)'
                    : '#fff',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                  <div>
                    <p className="muted" style={{ margin: 0, textTransform: 'uppercase', letterSpacing: '0.04em', fontSize: '0.8rem' }}>
                      Gói đang sử dụng
                    </p>
                    <h2 style={{ margin: '6px 0 0', fontSize: '1.6rem' }}>{subscription.planName || 'Free'}</h2>
                  </div>
                  <span
                    style={{
                      display: 'inline-block',
                      padding: '6px 12px',
                      borderRadius: 999,
                      background: statusMeta(subscription.status).bg,
                      color: statusMeta(subscription.status).color,
                      fontWeight: 600,
                      fontSize: '0.85rem',
                    }}
                  >
                    {statusMeta(subscription.status).label}
                  </span>
                </div>

                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
                    gap: 14,
                    marginTop: 18,
                  }}
                >
                  <div>
                    <div className="muted" style={{ fontSize: '0.85rem' }}>Giá gói</div>
                    <strong>{formatMoney(subscription.price, subscription.currency)}</strong>
                  </div>
                  <div>
                    <div className="muted" style={{ fontSize: '0.85rem' }}>Ngày bắt đầu</div>
                    <strong>{formatDate(subscription.startedAt)}</strong>
                  </div>
                  <div>
                    <div className="muted" style={{ fontSize: '0.85rem' }}>Ngày hết hạn</div>
                    <strong>{formatDate(subscription.expiresAt)}</strong>
                  </div>
                </div>

                {subscription.benefits.length > 0 && (
                  <div style={{ marginTop: 18 }}>
                    <div className="muted" style={{ marginBottom: 8, fontSize: '0.85rem' }}>Quyền lợi đang có</div>
                    <div className="chip-row">
                      {subscription.benefits.map((benefit) => (
                        <span key={benefit} className="chip match">✓ {benefit}</span>
                      ))}
                    </div>
                  </div>
                )}

                {(subscription.usages?.length || 0) > 0 && (
                  <div style={{ marginTop: 18 }}>
                    <div className="muted" style={{ marginBottom: 8, fontSize: '0.85rem' }}>Hạn mức tính năng theo gói</div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
                      {subscription.usages!.map((item) => {
                        const remaining = Math.max(0, item.limit - item.used);
                        const over = item.used >= item.limit;
                        return (
                          <div
                            key={item.featureKey}
                            style={{
                              border: `1px solid ${over ? '#fecaca' : '#e5e7eb'}`,
                              background: over ? '#fef2f2' : '#f9fafb',
                              borderRadius: 10,
                              padding: '12px 14px',
                            }}
                          >
                            <div className="muted" style={{ fontSize: '0.8rem' }}>
                              {item.label}{item.daily ? ' / ngày' : ''}
                            </div>
                            <strong style={{ fontSize: '1.15rem' }}>
                              {item.used}/{item.limit}
                            </strong>
                            <div style={{ fontSize: '0.8rem', color: over ? '#b91c1c' : '#166534', marginTop: 4 }}>
                              {over ? 'Đã hết hạn mức' : `Còn ${remaining}`}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                <div style={{ marginTop: 24, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                  <Link to="/employer/subscription/plans" className="button-link">
                    {Boolean(subscription.planId) && subscription.status?.toLowerCase() === 'active' ? 'Đổi / gia hạn gói' : 'Mua gói dịch vụ'}
                  </Link>
                </div>
              </article>
            ) : null}
          </div>
        )}

        {/* Tab 2: Payment History */}
        {activeTab === 'history' && (
          <div style={{ padding: 24 }}>
            {historyError && <p className="error" style={{ marginBottom: 16 }}>{historyError}</p>}
            {loadingHistory ? (
              <p className="loading">Đang tải lịch sử thanh toán...</p>
            ) : history.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '40px 20px', color: '#64748b' }}>
                <p style={{ fontSize: '1.1rem', margin: '0 0 8px', fontWeight: 500 }}>Chưa có lịch sử thanh toán</p>
                <p style={{ fontSize: '0.9rem', margin: 0 }}>Các giao dịch đăng ký gói dịch vụ của bạn sẽ xuất hiện tại đây.</p>
                <div style={{ marginTop: 16 }}>
                  <Link to="/employer/subscription/plans" className="button-link">Xem các gói dịch vụ</Link>
                </div>
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.9rem' }}>
                  <thead>
                    <tr style={{ background: '#f8fafc', borderBottom: '2px solid #e2e8f0', color: '#475569' }}>
                      <th style={{ padding: '12px 14px' }}>Mã đơn</th>
                      <th style={{ padding: '12px 14px' }}>Gói dịch vụ</th>
                      <th style={{ padding: '12px 14px' }}>Số tiền</th>
                      <th style={{ padding: '12px 14px' }}>Phương thức</th>
                      <th style={{ padding: '12px 14px' }}>Ngày tạo</th>
                      <th style={{ padding: '12px 14px' }}>Trạng thái</th>
                      <th style={{ padding: '12px 14px', textAlign: 'right' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((item) => {
                      const badge = paymentStatusMeta(item.status);
                      const isPending = (item.status || '').toLowerCase() === 'pending';
                      const isBankTransfer = (item.paymentMethod || '').toLowerCase() === 'bank_transfer';
                      
                      return (
                        <tr key={item.id} style={{ borderBottom: '1px solid #e2e8f0' }}>
                          <td style={{ padding: '12px 14px', fontFamily: 'monospace', fontWeight: 600, color: '#334155' }}>
                            #{item.id.substring(0, 8).toUpperCase()}
                          </td>
                          <td style={{ padding: '12px 14px', fontWeight: 600, color: '#0f172a' }}>
                            {item.planName || 'Gói dịch vụ'}
                          </td>
                          <td style={{ padding: '12px 14px', fontWeight: 600, color: '#16a34a' }}>
                            {formatMoney(item.amount, item.currency)}
                          </td>
                          <td style={{ padding: '12px 14px', color: '#475569' }}>
                            {paymentMethodLabel(item.paymentMethod)}
                          </td>
                          <td style={{ padding: '12px 14px', color: '#64748b' }}>
                            {formatDate(item.createdAt)}
                          </td>
                          <td style={{ padding: '12px 14px' }}>
                            <span
                              style={{
                                display: 'inline-block',
                                padding: '4px 10px',
                                borderRadius: 999,
                                background: badge.bg,
                                color: badge.color,
                                fontWeight: 600,
                                fontSize: '0.8rem',
                              }}
                            >
                              {badge.label}
                            </span>
                            {item.failureReason && (
                              <div style={{ fontSize: '0.75rem', color: '#b91c1c', marginTop: 2 }}>
                                {item.failureReason}
                              </div>
                            )}
                          </td>
                          <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                            {isPending && isBankTransfer ? (
                              <Link
                                to={`/payment/bank/${item.id}`}
                                className="button-link outline"
                                style={{ padding: '4px 10px', fontSize: '0.8rem' }}
                              >
                                Xem mã QR
                              </Link>
                            ) : isPending ? (
                              <Link
                                to={`/payment/result?paymentId=${item.id}`}
                                className="button-link outline"
                                style={{ padding: '4px 10px', fontSize: '0.8rem' }}
                              >
                                Kiểm tra
                              </Link>
                            ) : (
                              <span style={{ color: '#94a3b8', fontSize: '0.8rem' }}>—</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
