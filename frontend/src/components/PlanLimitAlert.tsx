import { Link } from 'react-router-dom';
import { getSubscriptionPlansPath } from '../utils/planLimits';

type Props = {
  message: string;
  className?: string;
};

/** Banner khi hết hạn mức free / gói — gợi ý xem các gói để tăng số lượng. */
export default function PlanLimitAlert({ message, className }: Props) {
  const plansPath = getSubscriptionPlansPath();

  return (
    <div
      className={className || 'error'}
      role="alert"
      style={{
        display: 'grid',
        gap: 10,
        padding: '14px 16px',
        borderRadius: 10,
        border: '1px solid #fecaca',
        background: '#fef2f2',
        color: '#991b1b',
      }}
    >
      <strong style={{ fontSize: '0.95rem' }}>Đã hết hạn mức sử dụng</strong>
      <p style={{ margin: 0, lineHeight: 1.45 }}>{message}</p>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
        <Link
          to={plansPath}
          className="button-link"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            padding: '8px 14px',
            borderRadius: 8,
            background: '#1d4ed8',
            color: '#fff',
            textDecoration: 'none',
            fontWeight: 600,
            fontSize: '0.9rem',
          }}
        >
          Xem các gói để tăng hạn mức
        </Link>
        <span style={{ fontSize: '0.85rem', color: '#7f1d1d' }}>
          Mua gói phù hợp để đăng tin / ứng tuyển / dùng AI nhiều hơn.
        </span>
      </div>
    </div>
  );
}
