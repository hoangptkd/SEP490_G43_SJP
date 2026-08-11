import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminPlan } from '../../types/admin';

type PlanLimits = {
  maxJobs: number;
  maxCv: number;
  maxApplicationsPerDay: number;
  maxAiSessionsPerDay: number;
  listingPriority: number;
};

type FeatureState = {
  selectedBenefits: string[];
  customBenefit: string;
  limits: PlanLimits;
};

const PLAN_TIERS = [
  {
    name: 'Plus',
    listingPriority: 1,
    sortOrder: 1,
    hint: 'Gói cơ bản — tin được ưu tiên hiển thị',
    employerLimits: { maxJobs: 20, maxCv: 5, maxApplicationsPerDay: 20, maxAiSessionsPerDay: 5, listingPriority: 1 },
    candidateLimits: { maxJobs: 5, maxCv: 10, maxApplicationsPerDay: 30, maxAiSessionsPerDay: 10, listingPriority: 0 },
  },
  {
    name: 'Pro',
    listingPriority: 2,
    sortOrder: 2,
    hint: 'Gói nâng cao — tin ưu tiên cao hơn Plus',
    employerLimits: { maxJobs: 50, maxCv: 5, maxApplicationsPerDay: 20, maxAiSessionsPerDay: 5, listingPriority: 2 },
    candidateLimits: { maxJobs: 5, maxCv: 20, maxApplicationsPerDay: 50, maxAiSessionsPerDay: 20, listingPriority: 0 },
  },
  {
    name: 'Premium',
    listingPriority: 3,
    sortOrder: 3,
    hint: 'Gói cao nhất — tin ưu tiên cao nhất',
    employerLimits: { maxJobs: 100, maxCv: 5, maxApplicationsPerDay: 20, maxAiSessionsPerDay: 5, listingPriority: 3 },
    candidateLimits: { maxJobs: 5, maxCv: 50, maxApplicationsPerDay: 100, maxAiSessionsPerDay: 50, listingPriority: 0 },
  },
] as const;

const ROLE_OPTIONS = [
  { value: 'employer', label: 'Nhà tuyển dụng', hint: 'Đăng tin, quản lý ứng viên' },
  { value: 'job_seeker', label: 'Ứng viên', hint: 'Ứng tuyển, CV, phỏng vấn AI' },
  { value: 'all', label: 'Tất cả', hint: 'Áp dụng cho cả hai vai trò' },
] as const;

const BENEFIT_PRESETS: Record<string, string[]> = {
  employer: [
    'Đăng tin tuyển dụng theo hạn mức gói',
    'Xem và quản lý ứng viên ứng tuyển',
    'Tin được ưu tiên hiển thị',
    'Hỗ trợ ưu tiên từ admin',
    'Thống kê ứng tuyển cơ bản',
  ],
  job_seeker: [
    'Ứng tuyển việc làm theo hạn mức ngày',
    'Upload nhiều phiên bản CV',
    'Luyện phỏng vấn AI',
    'Lưu việc làm không giới hạn',
    'Gợi ý việc làm phù hợp',
  ],
  all: [
    'Đăng tin tuyển dụng theo hạn mức gói',
    'Xem và quản lý ứng viên ứng tuyển',
    'Ứng tuyển việc làm theo hạn mức ngày',
    'Upload nhiều phiên bản CV',
    'Luyện phỏng vấn AI',
    'Tin được ưu tiên hiển thị',
  ],
};

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

function emptyForm(): Omit<AdminPlan, 'id' | 'createdAt' | 'updatedAt'> & { id?: string } {
  return {
    name: 'Plus',
    targetRole: 'employer',
    description: '',
    price: 0,
    currency: 'VND',
    durationDays: 30,
    featuresJson: '',
    status: 'active',
    sortOrder: 1,
  };
}

function normalizeStatus(status?: string) {
  return status?.toLowerCase() === 'inactive' ? 'inactive' : 'active';
}

function normalizePlanName(name?: string): 'Plus' | 'Pro' | 'Premium' {
  const match = PLAN_TIERS.find((tier) => tier.name.toLowerCase() === (name || '').trim().toLowerCase());
  return match?.name || 'Plus';
}

function tierOf(name?: string) {
  const normalized = normalizePlanName(name);
  return PLAN_TIERS.find((tier) => tier.name === normalized) || PLAN_TIERS[0];
}

function limitsFor(role: string, planName?: string): PlanLimits {
  const tier = tierOf(planName);
  if (role === 'job_seeker') return { ...tier.candidateLimits };
  if (role === 'all') {
    return {
      maxJobs: tier.employerLimits.maxJobs,
      maxCv: tier.candidateLimits.maxCv,
      maxApplicationsPerDay: tier.candidateLimits.maxApplicationsPerDay,
      maxAiSessionsPerDay: tier.candidateLimits.maxAiSessionsPerDay,
      listingPriority: tier.listingPriority,
    };
  }
  return { ...tier.employerLimits };
}

function defaultFeaturesForRole(role: string, planName = 'Plus'): FeatureState {
  const presets = BENEFIT_PRESETS[role] || BENEFIT_PRESETS.employer;
  const limits = limitsFor(role, planName);
  const selected = [...presets.slice(0, 2)];
  if ((role === 'employer' || role === 'all') && limits.listingPriority >= 1) {
    if (!selected.includes('Tin được ưu tiên hiển thị')) {
      selected.push('Tin được ưu tiên hiển thị');
    }
  }
  return {
    selectedBenefits: selected,
    customBenefit: '',
    limits,
  };
}

function parseFeatures(featuresJson: string | undefined, role: string, planName?: string): FeatureState {
  const fallback = defaultFeaturesForRole(role, planName);
  if (!featuresJson?.trim()) return fallback;
  try {
    const parsed = JSON.parse(featuresJson) as {
      benefits?: unknown;
      maxJobs?: unknown;
      maxCv?: unknown;
      maxApplicationsPerDay?: unknown;
      maxAiSessionsPerDay?: unknown;
    };
    const benefits = Array.isArray(parsed.benefits)
      ? parsed.benefits.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
      : fallback.selectedBenefits;
    return {
      selectedBenefits: benefits,
      customBenefit: '',
      limits: {
        maxJobs: Number(parsed.maxJobs) || fallback.limits.maxJobs,
        maxCv: Number(parsed.maxCv) || fallback.limits.maxCv,
        maxApplicationsPerDay: Number(parsed.maxApplicationsPerDay) || fallback.limits.maxApplicationsPerDay,
        maxAiSessionsPerDay: Number(parsed.maxAiSessionsPerDay) || fallback.limits.maxAiSessionsPerDay,
        listingPriority: tierOf(planName).listingPriority,
      },
    };
  } catch {
    return fallback;
  }
}

function buildFeaturesJson(state: FeatureState, role: string, planName: string): string {
  const custom = state.customBenefit
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
  const benefitSet = new Set([...state.selectedBenefits, ...custom]);
  const payload: Record<string, unknown> = {};
  const priority = (role === 'employer' || role === 'all') ? tierOf(planName).listingPriority : 0;
  if (role === 'employer' || role === 'all') {
    payload.maxJobs = Number(state.limits.maxJobs) || 0;
    payload.listingPriority = priority;
    if (priority >= 1) {
      benefitSet.add('Tin được ưu tiên hiển thị');
    }
  }
  if (role === 'job_seeker' || role === 'all') {
    payload.maxCv = Number(state.limits.maxCv) || 0;
    payload.maxApplicationsPerDay = Number(state.limits.maxApplicationsPerDay) || 0;
    payload.maxAiSessionsPerDay = Number(state.limits.maxAiSessionsPerDay) || 0;
  }
  payload.benefits = Array.from(benefitSet);
  return JSON.stringify(payload, null, 2);
}

function moneyPreview(price: number, currency: string) {
  try {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: currency || 'VND',
      maximumFractionDigits: 0,
    }).format(price || 0);
  } catch {
    return `${price || 0} ${currency || 'VND'}`;
  }
}

function roleLabel(role: string) {
  return ROLE_OPTIONS.find((item) => item.value === role)?.label || role;
}

export default function AdminPlanFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const [form, setForm] = useState(emptyForm());
  const [features, setFeatures] = useState<FeatureState>(() => defaultFeaturesForRole('employer', 'Plus'));
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const presets = useMemo(
    () => BENEFIT_PRESETS[form.targetRole] || BENEFIT_PRESETS.employer,
    [form.targetRole],
  );

  const customSelected = useMemo(
    () => features.selectedBenefits.filter((item) => !presets.includes(item)),
    [features.selectedBenefits, presets],
  );

  const customDraftCount = useMemo(
    () => features.customBenefit.split('\n').map((line) => line.trim()).filter(Boolean).length,
    [features.customBenefit],
  );

  const benefitCount = features.selectedBenefits.length + customDraftCount;
  const showEmployerLimits = form.targetRole === 'employer' || form.targetRole === 'all';
  const showCandidateLimits = form.targetRole === 'job_seeker' || form.targetRole === 'all';
  const currentTier = tierOf(form.name);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    setError('');
    adminService
      .listPlans('all')
      .then((plans) => {
        const plan = plans.find((item) => item.id === id);
        if (!plan) {
          setError('Không tìm thấy gói dịch vụ.');
          return;
        }
        const planName = normalizePlanName(plan.name);
        setForm({
          ...plan,
          name: planName,
          status: normalizeStatus(plan.status),
          featuresJson: plan.featuresJson || '{}',
          description: plan.description || '',
          sortOrder: tierOf(planName).sortOrder,
        });
        setFeatures(parseFeatures(plan.featuresJson, plan.targetRole, planName));
      })
      .catch((err) => setError(readError(err)))
      .finally(() => setLoading(false));
  }, [id]);

  function changeTargetRole(nextRole: string) {
    setForm((prev) => ({ ...prev, targetRole: nextRole }));
    setFeatures((prev) => {
      const nextPresets = BENEFIT_PRESETS[nextRole] || BENEFIT_PRESETS.employer;
      const kept = prev.selectedBenefits.filter((item) => nextPresets.includes(item));
      const nextDefaults = defaultFeaturesForRole(nextRole, form.name);
      return {
        selectedBenefits: kept.length > 0 ? kept : nextDefaults.selectedBenefits,
        customBenefit: prev.customBenefit,
        limits: {
          ...nextDefaults.limits,
          maxJobs: prev.limits.maxJobs || nextDefaults.limits.maxJobs,
          maxCv: prev.limits.maxCv || nextDefaults.limits.maxCv,
          maxApplicationsPerDay: prev.limits.maxApplicationsPerDay || nextDefaults.limits.maxApplicationsPerDay,
          maxAiSessionsPerDay: prev.limits.maxAiSessionsPerDay || nextDefaults.limits.maxAiSessionsPerDay,
          listingPriority: nextDefaults.limits.listingPriority,
        },
      };
    });
  }

  function changePlanName(nextName: string) {
    const planName = normalizePlanName(nextName);
    const tier = tierOf(planName);
    setForm((prev) => ({ ...prev, name: planName, sortOrder: tier.sortOrder }));
    setFeatures((prev) => {
      const nextLimits = limitsFor(form.targetRole, planName);
      const selected = [...prev.selectedBenefits];
      if ((form.targetRole === 'employer' || form.targetRole === 'all') && !selected.includes('Tin được ưu tiên hiển thị')) {
        selected.push('Tin được ưu tiên hiển thị');
      }
      return {
        ...prev,
        selectedBenefits: selected,
        limits: {
          ...prev.limits,
          ...nextLimits,
        },
      };
    });
  }

  function toggleBenefit(benefit: string) {
    setFeatures((prev) => {
      const exists = prev.selectedBenefits.includes(benefit);
      return {
        ...prev,
        selectedBenefits: exists
          ? prev.selectedBenefits.filter((item) => item !== benefit)
          : [...prev.selectedBenefits, benefit],
      };
    });
  }

  function updateLimit(key: keyof PlanLimits, value: number) {
    setFeatures((prev) => ({
      ...prev,
      limits: { ...prev.limits, [key]: Math.max(0, value) },
    }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    const featuresJson = buildFeaturesJson(features, form.targetRole, form.name);
    const payload = {
      name: normalizePlanName(form.name),
      targetRole: form.targetRole,
      description: form.description?.trim() || '',
      price: Number(form.price) || 0,
      currency: form.currency?.trim() || 'VND',
      durationDays: Number(form.durationDays) || 30,
      featuresJson,
      status: normalizeStatus(form.status),
      sortOrder: tierOf(form.name).sortOrder,
    };
    try {
      if (isEdit && id) {
        await adminService.updatePlan(id, payload);
      } else {
        await adminService.createPlan(payload);
      }
      navigate('/admin/billing?tab=plans', { replace: true });
    } catch (err) {
      setError(readError(err));
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <section className="admin-page">
        <p className="muted">Đang tải thông tin gói...</p>
      </section>
    );
  }

  return (
    <section className="admin-page admin-plan-page">
      <header className="admin-page-intro">
        <div className="admin-page-intro-copy">
          <p className="admin-page-intro-eyebrow">Cấu hình gói dịch vụ</p>
          <h1>{isEdit ? 'Chỉnh sửa gói dịch vụ' : 'Tạo gói dịch vụ mới'}</h1>
          <p>Chọn đối tượng, tích quyền lợi và chỉnh hạn mức — không cần viết JSON.</p>
        </div>
        <div className="admin-page-intro-aside">
          <div className="admin-page-intro-stat">
            <span>Chế độ</span>
            <strong>{isEdit ? 'Chỉnh sửa' : 'Tạo mới'}</strong>
          </div>
          <div className="admin-page-intro-stat">
            <span>Đối tượng</span>
            <strong>{form.targetRole === 'employer' ? 'Nhà tuyển dụng' : 'Ứng viên'}</strong>
          </div>
        </div>
      </header>

      <div className="admin-toolbar">
        <div className="admin-toolbar-group">
          <Link to="/admin/billing?tab=plans" className="button-link outline">← Quay lại gói dịch vụ</Link>
        </div>
      </div>

      {error && <p className="error admin-inline-message">{error}</p>}

      <form className="admin-plan-layout" onSubmit={handleSubmit}>
        <div className="admin-plan-main">
          <section className="admin-plan-card">
            <div className="admin-plan-card-head">
              <h2>1. Thông tin gói</h2>
              <p className="muted">Tên, giá và thời hạn hiển thị cho người dùng.</p>
            </div>

            <div className="admin-plan-role-picker" role="radiogroup" aria-label="Đối tượng gói">
              {ROLE_OPTIONS.map((option) => {
                const active = form.targetRole === option.value;
                return (
                  <button
                    key={option.value}
                    type="button"
                    role="radio"
                    aria-checked={active}
                    className={`admin-plan-role-card ${active ? 'active' : ''}`}
                    onClick={() => changeTargetRole(option.value)}
                  >
                    <strong>{option.label}</strong>
                    <span>{option.hint}</span>
                  </button>
                );
              })}
            </div>

            <div className="admin-settings-grid">
              <label className="full">
                Tên gói
                <select
                  required
                  value={normalizePlanName(form.name)}
                  onChange={(e) => changePlanName(e.target.value)}
                >
                  {PLAN_TIERS.map((tier) => (
                    <option key={tier.name} value={tier.name}>
                      {tier.name}
                    </option>
                  ))}
                </select>
                <small className="muted">{currentTier.hint}. Chỉ được chọn Plus / Pro / Premium.</small>
              </label>
              <label>
                Giá
                <input
                  type="number"
                  min={0}
                  required
                  value={form.price}
                  onChange={(e) => setForm({ ...form, price: Number(e.target.value) })}
                />
                <small className="muted">{moneyPreview(Number(form.price) || 0, form.currency || 'VND')}</small>
              </label>
              <label>
                Đơn vị tiền
                <select
                  value={form.currency || 'VND'}
                  onChange={(e) => setForm({ ...form, currency: e.target.value })}
                >
                  <option value="VND">VND</option>
                  <option value="USD">USD</option>
                </select>
              </label>
              <label>
                Thời hạn (ngày)
                <input
                  type="number"
                  min={1}
                  required
                  value={form.durationDays}
                  onChange={(e) => setForm({ ...form, durationDays: Number(e.target.value) })}
                />
              </label>
              <label>
                Trạng thái
                <select
                  value={normalizeStatus(form.status)}
                  onChange={(e) => setForm({ ...form, status: e.target.value })}
                >
                  <option value="active">Đang bán</option>
                  <option value="inactive">Tạm tắt</option>
                </select>
              </label>
              <label className="full">
                Mô tả ngắn
                <textarea
                  rows={3}
                  placeholder="Mô tả ngắn về gói để người dùng hiểu nhanh..."
                  value={form.description || ''}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
              </label>
            </div>
          </section>

          <section className="admin-plan-card">
            <div className="admin-plan-card-head">
              <div>
                <h2>2. Quyền lợi hiển thị</h2>
                <p className="muted">Các dòng này hiện trên trang mua gói. Đã chọn {benefitCount} quyền lợi.</p>
              </div>
              <button
                type="button"
                className="outline admin-plan-select-all"
                onClick={() => setFeatures((prev) => ({ ...prev, selectedBenefits: [...presets, ...customSelected] }))}
              >
                Chọn tất cả
              </button>
            </div>

            <div className="admin-plan-benefit-grid">
              {presets.map((benefit) => {
                const checked = features.selectedBenefits.includes(benefit);
                return (
                  <label key={benefit} className={`admin-plan-chip ${checked ? 'checked' : ''}`}>
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleBenefit(benefit)}
                    />
                    <span className="admin-plan-chip-mark" aria-hidden="true" />
                    <span className="admin-plan-chip-text">{benefit}</span>
                  </label>
                );
              })}
            </div>

            {customSelected.length > 0 && (
              <div className="admin-plan-custom-block">
                <p className="admin-plan-sublabel">Quyền lợi tùy chỉnh đang có</p>
                <div className="admin-plan-benefit-grid">
                  {customSelected.map((benefit) => (
                    <label key={benefit} className="admin-plan-chip checked">
                      <input type="checkbox" checked onChange={() => toggleBenefit(benefit)} />
                      <span className="admin-plan-chip-mark" aria-hidden="true" />
                      <span className="admin-plan-chip-text">{benefit}</span>
                    </label>
                  ))}
                </div>
              </div>
            )}

            <label className="admin-plan-custom-input">
              Thêm quyền lợi khác
              <textarea
                rows={3}
                placeholder={'Mỗi dòng 1 quyền lợi, ví dụ:\nƯu tiên hỗ trợ 24/7\nBadge xác thực doanh nghiệp'}
                value={features.customBenefit}
                onChange={(e) => setFeatures((prev) => ({ ...prev, customBenefit: e.target.value }))}
              />
            </label>
          </section>

          <section className="admin-plan-card">
            <div className="admin-plan-card-head">
              <h2>3. Hạn mức sử dụng</h2>
              <p className="muted">Giới hạn thật khi user dùng hệ thống (không chỉ hiển thị).</p>
            </div>

            <div className="admin-plan-limit-grid">
              {showEmployerLimits && (
                <label className="admin-plan-limit-card">
                  <span className="admin-plan-limit-title">Tin đăng tối đa</span>
                  <span className="muted">Áp dụng cho nhà tuyển dụng trong thời hạn gói</span>
                  <input
                    type="number"
                    min={0}
                    value={features.limits.maxJobs}
                    onChange={(e) => updateLimit('maxJobs', Number(e.target.value))}
                  />
                </label>
              )}
              {showEmployerLimits && (
                <div className="admin-plan-limit-card">
                  <span className="admin-plan-limit-title">Ưu tiên tin</span>
                  <span className="muted">Tin được ưu tiên hiển thị theo bậc gói {normalizePlanName(form.name)}.</span>
                </div>
              )}
              {showCandidateLimits && (
                <>
                  <label className="admin-plan-limit-card">
                    <span className="admin-plan-limit-title">Số CV tối đa</span>
                    <span className="muted">Phiên bản CV ứng viên được lưu</span>
                    <input
                      type="number"
                      min={0}
                      value={features.limits.maxCv}
                      onChange={(e) => updateLimit('maxCv', Number(e.target.value))}
                    />
                  </label>
                  <label className="admin-plan-limit-card">
                    <span className="admin-plan-limit-title">Ứng tuyển / ngày</span>
                    <span className="muted">Số lần nộp hồ sơ mỗi ngày</span>
                    <input
                      type="number"
                      min={0}
                      value={features.limits.maxApplicationsPerDay}
                      onChange={(e) => updateLimit('maxApplicationsPerDay', Number(e.target.value))}
                    />
                  </label>
                  <label className="admin-plan-limit-card">
                    <span className="admin-plan-limit-title">Phiên AI / ngày</span>
                    <span className="muted">Lượt luyện phỏng vấn AI mỗi ngày</span>
                    <input
                      type="number"
                      min={0}
                      value={features.limits.maxAiSessionsPerDay}
                      onChange={(e) => updateLimit('maxAiSessionsPerDay', Number(e.target.value))}
                    />
                  </label>
                </>
              )}
            </div>
          </section>
        </div>

        <aside className="admin-plan-side">
          <div className="admin-plan-preview">
            <p className="admin-plan-preview-eyebrow">Xem trước</p>
            <h3>{form.name.trim() || 'Tên gói chưa nhập'}</h3>
            <p className="admin-plan-preview-price">{moneyPreview(Number(form.price) || 0, form.currency || 'VND')}</p>
            <ul className="admin-plan-preview-meta">
              <li>
                <span>Đối tượng</span>
                <strong>{roleLabel(form.targetRole)}</strong>
              </li>
              <li>
                <span>Thời hạn</span>
                <strong>{form.durationDays || 0} ngày</strong>
              </li>
              <li>
                <span>Trạng thái</span>
                <strong>{normalizeStatus(form.status) === 'active' ? 'Đang bán' : 'Tạm tắt'}</strong>
              </li>
              <li>
                <span>Quyền lợi</span>
                <strong>{benefitCount}</strong>
              </li>
              {showEmployerLimits && (
                <li>
                  <span>Ưu tiên tin</span>
                  <strong>Có</strong>
                </li>
              )}
            </ul>
            {form.description?.trim() && (
              <p className="muted admin-plan-preview-desc">{form.description.trim()}</p>
            )}
            <div className="admin-plan-preview-actions">
              <button type="submit" disabled={saving}>
                {saving ? 'Đang lưu...' : isEdit ? 'Lưu thay đổi' : 'Tạo gói'}
              </button>
              <Link className="button-link outline" to="/admin/billing?tab=plans">
                Hủy
              </Link>
            </div>
          </div>
        </aside>
      </form>
    </section>
  );
}
