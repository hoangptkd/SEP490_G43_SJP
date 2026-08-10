import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { billingService, type UserSubscription } from '../../services/billingService';

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

export default function EmployerSubscriptionPage() {
  const [subscription, setSubscription] = useState<UserSubscription | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    billingService.getMySubscription()
      .then(setSubscription)
      .catch(() => setError('Không tải được thông tin gói đang dùng.'));
  }, []);

  if (error) {
    return <p className="error" style={{ padding: 24 }}>{error}</p>;
  }

  if (!subscription) {
    return <p className="loading">Đang tải gói dịch vụ...</p>;
  }

  const badge = statusMeta(subscription.status);
  const isPaidPlan = Boolean(subscription.planId) && subscription.status?.toLowerCase() === 'active';

  return (
    <section>
      <div className="page-header">
        <h1>Gói dịch vụ</h1>
        <p>Xem gói bạn đang dùng và nâng cấp khi cần</p>
      </div>

      <article
        className="card"
        style={{
          marginBottom: 20,
          border: isPaidPlan ? '2px solid #16a34a' : '1px solid var(--border, #e5e7eb)',
          background: isPaidPlan ? 'linear-gradient(180deg, #f0fdf4 0%, #fff 55%)' : undefined,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <div>
            <p className="muted" style={{ margin: 0, textTransform: 'uppercase', letterSpacing: '0.04em', fontSize: '0.8rem' }}>
              Gói đang dùng
            </p>
            <h2 style={{ margin: '6px 0 0', fontSize: '1.6rem' }}>{subscription.planName || 'Free'}</h2>
          </div>
          <span
            style={{
              display: 'inline-block',
              padding: '6px 12px',
              borderRadius: 999,
              background: badge.bg,
              color: badge.color,
              fontWeight: 600,
              fontSize: '0.85rem',
            }}
          >
            {badge.label}
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
            <div className="muted" style={{ marginBottom: 8, fontSize: '0.85rem' }}>Hạn mức theo gói</div>
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

        <div style={{ marginTop: 20, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <Link to="/employer/subscription/plans" className="button-link">
            {isPaidPlan ? 'Đổi / gia hạn gói' : 'Mua gói'}
          </Link>
        </div>
      </article>
    </section>
  );
}
