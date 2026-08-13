import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { billingService } from '../../services/billingService';
import type { PlanCatalogItem } from '../../types/billing';
import '../../styles/admin.css';

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

const HIDDEN_DEFAULT_BENEFITS = [
  'xem và quản lý ứng viên ứng tuyển',
  'thống kê ứng tuyển cơ bản',
];

function checkedBenefits(plan: PlanCatalogItem) {
  return (plan.benefits || []).filter((item) => {
    const text = item.trim().toLowerCase();
    return text.length > 0 && !HIDDEN_DEFAULT_BENEFITS.includes(text);
  });
}

function planLimitLines(plan: PlanCatalogItem) {
  const lines: string[] = [];
  if (plan.targetRole === 'employer' || plan.targetRole === 'all') {
    if (plan.maxJobs != null) lines.push(`Tin đăng tối đa: ${plan.maxJobs}`);
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

function displayBenefits(plan: PlanCatalogItem) {
  const selected = checkedBenefits(plan);
  const selectedLower = new Set(selected.map((item) => item.trim().toLowerCase()));
  const extraLimits = planLimitLines(plan).filter((line) => !selectedLower.has(line.trim().toLowerCase()));
  return [...selected, ...extraLimits];
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

  const featuredPlanId = (() => {
    const byName = plans.find((plan) => plan.name.trim().toLowerCase() === 'premium')
      || plans.find((plan) => plan.name.trim().toLowerCase() === 'pro');
    if (byName) return byName.id;
    return plans.reduce<PlanCatalogItem | null>((best, plan) => (
      !best || plan.price > best.price ? plan : best
    ), null)?.id;
  })();

  return (
    <section className="admin-page plan-catalog-page">
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
        <div className="plan-catalog-grid">
          {plans.map((plan) => {
            const benefits = displayBenefits(plan);
            const featured = plan.id === featuredPlanId && plans.length > 1;
            return (
              <article
                key={plan.id}
                className={`plan-catalog-card${featured ? ' is-featured' : ''}`}
              >
                {featured && <div className="plan-catalog-ribbon">Phổ biến nhất</div>}
                <div className="plan-catalog-head">
                  <div className="plan-catalog-meta">
                    <span className={`plan-catalog-role ${plan.targetRole === 'employer' ? 'is-employer' : 'is-candidate'}`}>
                      {roleLabel(plan.targetRole)}
                    </span>
                    <span className="plan-catalog-term">{plan.durationDays} ngày</span>
                  </div>
                  <h2 className="plan-catalog-name">{plan.name}</h2>
                  <div className="plan-catalog-price">
                    <span className="plan-catalog-amount">{formatMoney(plan.price, plan.currency)}</span>
                    <span className="plan-catalog-price-hint">/ {plan.durationDays} ngày</span>
                  </div>
                </div>

                {benefits.length > 0 && (
                  <div className="plan-catalog-block">
                    <div className="plan-catalog-label">Quyền lợi</div>
                    <ul className="plan-catalog-benefits">
                      {benefits.map((line) => (
                        <li key={line}>{line}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {plan.description?.trim() && (
                  <p className="plan-catalog-desc">{plan.description.trim()}</p>
                )}

                <button type="button" className="plan-catalog-buy" onClick={() => handleBuy(plan)}>
                  {featured ? 'Chọn gói này' : 'Mua ngay'}
                </button>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
