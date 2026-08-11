import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { billingService } from '../../services/billingService';
import type { PlanCatalogItem } from '../../types/billing';

type Props = {
  backTo: string;
  backLabel: string;
  title?: string;
  subtitle?: string;
};

function formatMoney(value: number, currency = 'VND') {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value);
}

function roleLabel(targetRole: string) {
  if (targetRole === 'employer') return 'Nhà tuyển dụng';
  if (targetRole === 'job_seeker') return 'Ứng viên';
  return 'Tất cả';
}

function planLimitLines(plan: PlanCatalogItem) {
  const lines: string[] = [];
  if (plan.targetRole === 'employer' || plan.targetRole === 'all') {
    if (plan.maxJobs != null) lines.push(`Tin đăng tối đa: ${plan.maxJobs}`);
    if (plan.listingPriority != null && plan.listingPriority > 0) {
      lines.push('Tin được ưu tiên hiển thị');
    }
  }
  if (plan.targetRole === 'job_seeker' || plan.targetRole === 'all') {
    if (plan.maxCv != null) lines.push(`CV tối đa: ${plan.maxCv}`);
    if (plan.maxApplicationsPerDay != null) lines.push(`Ứng tuyển/ngày: ${plan.maxApplicationsPerDay}`);
    if (plan.maxAiSessionsPerDay != null) lines.push(`Phiên AI/ngày: ${plan.maxAiSessionsPerDay}`);
    if (plan.maxAiJobSearchesPerMonth != null) {
      lines.push(plan.maxAiJobSearchesPerMonth < 0
        ? 'Tìm việc bằng AI: Không giới hạn'
        : `Tìm việc bằng AI/tháng: ${plan.maxAiJobSearchesPerMonth}`);
    }
  }
  return lines;
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

export default function SubscriptionPlansPage({
  backTo,
  backLabel,
  title = 'Chọn gói dịch vụ',
  subtitle = 'Chọn gói phù hợp rồi tiến hành thanh toán.',
}: Props) {
  const navigate = useNavigate();
  const [plans, setPlans] = useState<PlanCatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadPlans = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await billingService.listPlans();
      setPlans(data);
    } catch (err) {
      setError(readError(err));
      setPlans([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPlans();
  }, [loadPlans]);

  function handleBuy(plan: PlanCatalogItem) {
    navigate(`/payment/checkout?planId=${plan.id}`);
  }

  return (
    <section className="admin-page" style={{ maxWidth: 1100 }}>
      <header className="admin-page-header">
        <div>
          <Link to={backTo} className="button-link outline" style={{ marginBottom: 12, display: 'inline-block' }}>
            ← {backLabel}
          </Link>
          <h1>{title}</h1>
          <p className="muted">{subtitle}</p>
        </div>
        <button type="button" className="outline" onClick={loadPlans} disabled={loading}>
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}

      {loading ? (
        <p className="loading">Đang tải danh sách gói...</p>
      ) : plans.length === 0 ? (
        <div className="admin-placeholder-card">
          <p>Chưa có gói nào đang bán cho loại tài khoản của bạn. Vui lòng liên hệ quản trị viên.</p>
        </div>
      ) : (
        <div className="admin-metric-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
          {plans.map((plan) => (
            <article key={plan.id} className="admin-metric-card" style={{ alignItems: 'flex-start', gap: 12 }}>
              <span className={`admin-status-badge ${plan.targetRole === 'employer' ? 'status-pending' : 'status-verified'}`}>
                {roleLabel(plan.targetRole)}
              </span>
              <strong style={{ fontSize: '1.2rem' }}>{plan.name}</strong>
              {plan.description && <p className="muted" style={{ margin: 0 }}>{plan.description}</p>}
              <div style={{ fontSize: '1.6rem', fontWeight: 700, color: 'var(--primary)' }}>
                {formatMoney(plan.price, plan.currency)}
              </div>
              <p className="muted" style={{ margin: 0 }}>Thời hạn: {plan.durationDays} ngày</p>
              {plan.benefits.length > 0 && (
                <ul style={{ margin: '8px 0 0', paddingLeft: 18, width: '100%' }}>
                  {plan.benefits.map((benefit) => (
                    <li key={benefit}>{benefit}</li>
                  ))}
                </ul>
              )}
              {planLimitLines(plan).length > 0 && (
                <div style={{ width: '100%', marginTop: 8, padding: '10px 12px', background: '#f8fafc', borderRadius: 8, border: '1px solid #e2e8f0' }}>
                  <div className="muted" style={{ fontSize: '0.8rem', marginBottom: 6 }}>Hạn mức gói</div>
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {planLimitLines(plan).map((line) => (
                      <li key={line}>{line}</li>
                    ))}
                  </ul>
                </div>
              )}
              <button
                type="button"
                style={{ marginTop: 12, width: '100%' }}
                onClick={() => handleBuy(plan)}
              >
                Mua
              </button>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
