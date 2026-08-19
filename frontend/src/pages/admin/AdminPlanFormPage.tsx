import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminPlan } from '../../types/admin';

type PlanLimits = {
  maxJobs: number;
  maxCv: number;
  maxApplicationsPerDay: number;
  maxAiSessionsPerDay: number;
  maxAiJobSearchesPerMonth: number;
  listingPriority: number;
  maxJobPostingDays: number;
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
    employerHint: 'tin được ưu tiên hiển thị',
    employerLimits: { maxJobs: 20, maxCv: 5, maxApplicationsPerDay: 20, maxAiSessionsPerDay: 5, maxAiJobSearchesPerMonth: 0, listingPriority: 1, maxJobPostingDays: 30 },
    candidateLimits: { maxJobs: 5, maxCv: 10, maxApplicationsPerDay: 30, maxAiSessionsPerDay: 10, maxAiJobSearchesPerMonth: 10, listingPriority: 0, maxJobPostingDays: 30 },
  },
  {
    name: 'Pro',
    listingPriority: 2,
    sortOrder: 2,
    employerHint: 'tin ưu tiên cao hơn Plus',
    employerLimits: { maxJobs: 50, maxCv: 5, maxApplicationsPerDay: 20, maxAiSessionsPerDay: 5, maxAiJobSearchesPerMonth: 0, listingPriority: 2, maxJobPostingDays: 60 },
    candidateLimits: { maxJobs: 5, maxCv: 20, maxApplicationsPerDay: 50, maxAiSessionsPerDay: 20, maxAiJobSearchesPerMonth: 20, listingPriority: 0, maxJobPostingDays: 60 },
  },
  {
    name: 'Premium',
    listingPriority: 3,
    sortOrder: 3,
    employerHint: 'tin ưu tiên cao nhất',
    employerLimits: { maxJobs: 100, maxCv: 5, maxApplicationsPerDay: 20, maxAiSessionsPerDay: 5, maxAiJobSearchesPerMonth: 0, listingPriority: 3, maxJobPostingDays: 90 },
    candidateLimits: { maxJobs: 5, maxCv: 50, maxApplicationsPerDay: 100, maxAiSessionsPerDay: 50, maxAiJobSearchesPerMonth: 50, listingPriority: 0, maxJobPostingDays: 90 },
  },
] as const;

const ROLE_OPTIONS = [
  { value: 'employer', label: 'Nhà tuyển dụng', hint: 'Đăng tin, quản lý ứng viên — tách riêng với ứng viên' },
  { value: 'job_seeker', label: 'Ứng viên', hint: 'Ứng tuyển, CV, phỏng vấn AI — tách riêng với NTD' },
] as const;

const BENEFIT_PRESETS: Record<string, string[]> = {
  employer: [
    'Đăng tin tuyển dụng theo hạn mức gói',
    'Tin được ưu tiên hiển thị',
    'Sử dụng tính năng AI Ranking (Smart Ranking)',
  ],
  job_seeker: [
    'Ứng tuyển việc làm theo hạn mức ngày',
    'Upload nhiều phiên bản CV',
    'Luyện phỏng vấn AI',
    'Tìm việc phù hợp bằng AI',
    'Lưu việc làm không giới hạn',
    'Gợi ý việc làm phù hợp',
  ],
};

/** Quyền lợi nền tảng luôn gắn khi lưu gói NTD (không hiện trên form chọn). */
const EMPLOYER_DEFAULT_BENEFITS = [
  'Xem và quản lý ứng viên ứng tuyển',
  'Thống kê ứng tuyển cơ bản',
] as const;

function normalizeTargetRole(role?: string): 'employer' | 'job_seeker' {
  return role === 'job_seeker' ? 'job_seeker' : 'employer';
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

function emptyForm(targetRole: 'employer' | 'job_seeker' = 'employer'): Omit<AdminPlan, 'id' | 'createdAt' | 'updatedAt'> & { id?: string } {
  return {
    name: 'Plus',
    targetRole,
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

/** Chuẩn hóa Plus/Pro/Premium; tên khác giữ nguyên. */
function canonicalPlanName(name?: string): string {
  const trimmed = (name || '').trim();
  const match = PLAN_TIERS.find((tier) => tier.name.toLowerCase() === trimmed.toLowerCase());
  return match?.name || trimmed;
}

function isStandardTier(name?: string): boolean {
  const canonical = canonicalPlanName(name);
  return PLAN_TIERS.some((tier) => tier.name === canonical);
}

function tierOf(name?: string) {
  const canonical = canonicalPlanName(name);
  return PLAN_TIERS.find((tier) => tier.name === canonical) || null;
}

function limitsFor(role: string, planName?: string): PlanLimits {
  const normalizedRole = normalizeTargetRole(role);
  const tier = tierOf(planName);
  if (tier) {
    if (normalizedRole === 'job_seeker') return { ...tier.candidateLimits };
    return { ...tier.employerLimits };
  }
  // Gói tùy chỉnh: mặc định hạn mức vừa phải, ưu tiên tin = 0
  if (normalizedRole === 'job_seeker') {
    return { maxJobs: 5, maxCv: 10, maxApplicationsPerDay: 20, maxAiSessionsPerDay: 5, maxAiJobSearchesPerMonth: 5, listingPriority: 0, maxJobPostingDays: 30 };
  }
  return { maxJobs: 15, maxCv: 5, maxApplicationsPerDay: 20, maxAiSessionsPerDay: 5, maxAiJobSearchesPerMonth: 0, listingPriority: 0, maxJobPostingDays: 30 };
}

function defaultFeaturesForRole(role: string, planName = 'Plus'): FeatureState {
  const presets = BENEFIT_PRESETS[role] || BENEFIT_PRESETS.employer;
  const limits = limitsFor(role, planName);
  const selected = role === 'employer' ? [...presets] : [...presets.slice(0, 2)];
  if (role === 'employer' && limits.listingPriority >= 1) {
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
      maxAiJobSearchesPerMonth?: unknown;
      listingPriority?: unknown;
      maxJobPostingDays?: unknown;
    };
    const rawBenefits = Array.isArray(parsed.benefits)
      ? parsed.benefits.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
      : fallback.selectedBenefits;
    // Ẩn quyền lợi mặc định NTD khỏi checkbox — chúng luôn được gắn khi lưu
    const benefits = role === 'employer'
      ? rawBenefits.filter((item) => !(EMPLOYER_DEFAULT_BENEFITS as readonly string[]).includes(item))
      : rawBenefits;
    const tier = tierOf(planName);
    const listingPriority = tier
      ? tier.listingPriority
      : Math.max(0, Math.min(3, Number(parsed.listingPriority) || 0));
    return {
      selectedBenefits: benefits.length > 0 ? benefits : fallback.selectedBenefits,
      customBenefit: '',
      limits: {
        maxJobs: Number(parsed.maxJobs) || fallback.limits.maxJobs,
        maxCv: Number(parsed.maxCv) || fallback.limits.maxCv,
        maxApplicationsPerDay: Number(parsed.maxApplicationsPerDay) || fallback.limits.maxApplicationsPerDay,
        maxAiSessionsPerDay: Number(parsed.maxAiSessionsPerDay) || fallback.limits.maxAiSessionsPerDay,
        maxAiJobSearchesPerMonth: Number.isFinite(Number(parsed.maxAiJobSearchesPerMonth))
          ? Number(parsed.maxAiJobSearchesPerMonth)
          : fallback.limits.maxAiJobSearchesPerMonth,
        listingPriority,
        maxJobPostingDays: Number(parsed.maxJobPostingDays) || fallback.limits.maxJobPostingDays || 30,
      },
    };
  } catch {
    return fallback;
  }
}

function resolveListingPriority(role: string, planName: string, limits: PlanLimits): number {
  if (role !== 'employer') return 0;
  const tier = tierOf(planName);
  if (tier) return tier.listingPriority;
  return Math.max(0, Math.min(3, Number(limits.listingPriority) || 0));
}

function buildFeaturesJson(state: FeatureState, role: string, planName: string): string {
  const custom = state.customBenefit
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
  const benefitSet = new Set([...state.selectedBenefits, ...custom]);
  const payload: Record<string, unknown> = {};
  const priority = resolveListingPriority(role, planName, state.limits);
  if (role === 'employer') {
    payload.maxJobs = Number(state.limits.maxJobs) || 0;
    payload.listingPriority = priority;
    payload.maxJobPostingDays = Number(state.limits.maxJobPostingDays) || 30;
    EMPLOYER_DEFAULT_BENEFITS.forEach((item) => benefitSet.delete(item));
  }
  if (role === 'job_seeker') {
    payload.maxCv = Number(state.limits.maxCv) || 0;
    payload.maxApplicationsPerDay = Number(state.limits.maxApplicationsPerDay) || 0;
    payload.maxAiSessionsPerDay = Number(state.limits.maxAiSessionsPerDay) || 0;
    payload.maxAiJobSearchesPerMonth = Number(state.limits.maxAiJobSearchesPerMonth) || 0;
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

function sortOrderFor(name: string): number {
  return tierOf(name)?.sortOrder ?? 10;
}

export default function AdminPlanFormPage() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const lockedRole = useMemo(() => {
    if (isEdit) return null;
    const raw = (searchParams.get('role') || '').trim().toLowerCase();
    if (raw === 'job_seeker' || raw === 'candidate') return 'job_seeker' as const;
    if (raw === 'employer') return 'employer' as const;
    return null;
  }, [isEdit, searchParams]);
  const initialRole = lockedRole || 'employer';
  const [form, setForm] = useState(() => emptyForm(initialRole));
  const [features, setFeatures] = useState<FeatureState>(() => defaultFeaturesForRole(initialRole, 'Plus'));
  const [existingPlans, setExistingPlans] = useState<AdminPlan[]>([]);
  /** 'Plus' | 'Pro' | 'Premium' | 'custom' — custom mới cho sửa ô tên gói */
  const [nameMode, setNameMode] = useState<'Plus' | 'Pro' | 'Premium' | 'custom'>('Plus');
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const presets = useMemo(
    () => BENEFIT_PRESETS[form.targetRole] || BENEFIT_PRESETS.employer,
    [form.targetRole],
  );

  const customSelected = useMemo(
    () => features.selectedBenefits.filter((item) => {
      if (presets.includes(item)) return false;
      if (form.targetRole === 'employer' && (EMPLOYER_DEFAULT_BENEFITS as readonly string[]).includes(item)) {
        return false;
      }
      return true;
    }),
    [features.selectedBenefits, form.targetRole, presets],
  );

  const customDraftCount = useMemo(
    () => features.customBenefit.split('\n').map((line) => line.trim()).filter(Boolean).length,
    [features.customBenefit],
  );

  const benefitCount = features.selectedBenefits.length + customDraftCount;
  const showEmployerLimits = form.targetRole === 'employer';
  const showCandidateLimits = form.targetRole === 'job_seeker';
  const standardSelected = nameMode !== 'custom';
  const nameLocked = nameMode !== 'custom';

  const duplicateMessage = useMemo(() => {
    const planName = canonicalPlanName(form.name);
    if (!planName) return '';
    const role = normalizeTargetRole(form.targetRole);
    const duplicated = existingPlans.some((plan) => {
      if (isEdit && id && plan.id === id) return false;
      return (
        canonicalPlanName(plan.name).toLowerCase() === planName.toLowerCase()
        && normalizeTargetRole(plan.targetRole) === role
      );
    });
    if (!duplicated) return '';
    return `Đã tồn tại gói "${planName}" dành cho ${roleLabel(role)}. Không thể tạo/lưu trùng — hãy đổi tên hoặc đổi đối tượng.`;
  }, [existingPlans, form.name, form.targetRole, id, isEdit]);

  useEffect(() => {
    adminService
      .listPlans('all')
      .then(setExistingPlans)
      .catch(() => setExistingPlans([]));
  }, []);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    setError('');
    adminService
      .listPlans('all')
      .then((plans) => {
        setExistingPlans(plans);
        const plan = plans.find((item) => item.id === id);
        if (!plan) {
          setError('Không tìm thấy gói dịch vụ.');
          return;
        }
        // Gói cũ target_role=all sẽ chuyển về employer khi chỉnh sửa (NTD / UV tách riêng).
        const role = normalizeTargetRole(plan.targetRole);
        const planName = canonicalPlanName(plan.name);
        const standard = isStandardTier(planName);
        setNameMode(standard ? (planName as 'Plus' | 'Pro' | 'Premium') : 'custom');
        setForm({
          ...plan,
          name: planName,
          targetRole: role,
          status: normalizeStatus(plan.status),
          featuresJson: plan.featuresJson || '{}',
          description: plan.description || '',
          sortOrder: sortOrderFor(planName),
        });
        setFeatures(parseFeatures(plan.featuresJson, role, planName));
      })
      .catch((err) => setError(readError(err)))
      .finally(() => setLoading(false));
  }, [id]);

  function changeTargetRole(nextRole: string) {
    if (lockedRole) return;
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
          maxAiJobSearchesPerMonth: prev.limits.maxAiJobSearchesPerMonth || nextDefaults.limits.maxAiJobSearchesPerMonth,
          listingPriority: nextDefaults.limits.listingPriority,
        },
      };
    });
  }

  /** Chọn nhanh Plus / Pro / Premium → khóa tên + điền hạn mức mặc định. */
  function applyStandardTier(tierName: 'Plus' | 'Pro' | 'Premium') {
    setNameMode(tierName);
    setForm((prev) => ({ ...prev, name: tierName, sortOrder: tierOf(tierName)!.sortOrder }));
    setFeatures((prev) => {
      const nextLimits = limitsFor(form.targetRole, tierName);
      const selected = [...prev.selectedBenefits];
      if (form.targetRole === 'employer' && !selected.includes('Tin được ưu tiên hiển thị')) {
        selected.push('Tin được ưu tiên hiển thị');
      }
      return {
        ...prev,
        selectedBenefits: selected,
        limits: { ...prev.limits, ...nextLimits },
      };
    });
  }

  /** Chọn "Khác" → mở khóa ô tên để nhập tên tùy chỉnh. */
  function applyCustomNameMode() {
    setNameMode('custom');
    setForm((prev) => {
      const keepCustom = prev.name && !isStandardTier(prev.name) ? prev.name : '';
      return { ...prev, name: keepCustom, sortOrder: 10 };
    });
    setFeatures((prev) => ({
      ...prev,
      limits: {
        ...prev.limits,
        ...limitsFor(form.targetRole, ''),
        listingPriority: form.targetRole === 'employer' ? prev.limits.listingPriority : 0,
      },
    }));
  }

  function changePlanName(nextName: string) {
    if (nameMode !== 'custom') return;
    const planName = nextName.slice(0, 80);
    setForm((prev) => ({
      ...prev,
      name: planName,
      sortOrder: 10,
    }));
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
    setFeatures((prev) => {
      let next = value;
      if (key === 'listingPriority') next = Math.max(0, Math.min(3, value));
      else if (key === 'maxAiJobSearchesPerMonth') next = value < 0 ? -1 : Math.max(0, value);
      else next = Math.max(0, value);
      return {
        ...prev,
        limits: { ...prev.limits, [key]: next },
      };
    });
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const planName = canonicalPlanName(form.name);
    if (!planName) {
      setError(nameMode === 'custom'
        ? 'Vui lòng nhập tên gói tùy chỉnh.'
        : 'Vui lòng chọn tên gói dịch vụ.');
      return;
    }
    if (duplicateMessage) {
      setError(duplicateMessage);
      window.alert(duplicateMessage);
      return;
    }
    setSaving(true);
    setError('');
    const featuresJson = buildFeaturesJson(features, form.targetRole, planName);
    const payload = {
      name: planName,
      targetRole: lockedRole || form.targetRole,
      description: form.description?.trim() || '',
      price: Number(form.price) || 0,
      currency: form.currency?.trim() || 'VND',
      durationDays: Number(form.durationDays) || 30,
      featuresJson,
      status: normalizeStatus(form.status),
      sortOrder: sortOrderFor(planName),
    };
    try {
      if (isEdit && id) {
        await adminService.updatePlan(id, payload);
      } else {
        await adminService.createPlan(payload);
      }
      navigate('/admin/billing?tab=plans', { replace: true });
    } catch (err) {
      const message = readError(err);
      setError(message);
      window.alert(message);
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
          <h1>
            {isEdit
              ? 'Chỉnh sửa gói dịch vụ'
              : lockedRole === 'job_seeker'
                ? 'Tạo gói cho Ứng viên'
                : lockedRole === 'employer'
                  ? 'Tạo gói cho Nhà tuyển dụng'
                  : 'Tạo gói dịch vụ mới'}
          </h1>
          <p>
            {lockedRole
              ? `Form này chỉ tạo gói dành cho ${roleLabel(lockedRole)}. Chọn Plus / Pro / Premium hoặc Khác để đặt tên.`
              : 'Mặc định có Plus / Pro / Premium — tạo riêng cho Nhà tuyển dụng hoặc Ứng viên. Vẫn có thể đặt tên gói khác ngoài 3 bậc này.'}
          </p>
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

      {(error || duplicateMessage) && (
        <p className="error admin-inline-message" role="alert">
          {error || duplicateMessage}
        </p>
      )}

      <form className="admin-plan-layout" onSubmit={handleSubmit}>
        <div className="admin-plan-main">
          <section className="admin-plan-card">
            <div className="admin-plan-card-head">
              <h2>1. Thông tin gói</h2>
              <p className="muted">
                {lockedRole
                  ? `Đối tượng đã khóa theo phần ${roleLabel(lockedRole)}.`
                  : 'Chọn đối tượng, bậc mặc định hoặc tự đặt tên gói.'}
              </p>
            </div>

            {lockedRole ? (
              <div className="admin-plan-role-locked">
                <strong>{roleLabel(lockedRole)}</strong>
                <span>
                  {lockedRole === 'employer'
                    ? 'Gói này sẽ hiển thị cho nhà tuyển dụng khi mua/nâng cấp.'
                    : 'Gói này sẽ hiển thị cho ứng viên khi mua/nâng cấp.'}
                </span>
              </div>
            ) : (
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
            )}

            <div className="admin-settings-grid">
              <div className="full admin-plan-name-block">
                <p className="admin-plan-sublabel">Tên gói</p>
                <div className="admin-plan-tier-picker" role="radiogroup" aria-label="Chọn tên gói">
                  {PLAN_TIERS.map((tier) => {
                    const active = nameMode === tier.name;
                    return (
                      <button
                        key={tier.name}
                        type="button"
                        role="radio"
                        aria-checked={active}
                        className={`admin-plan-tier-option ${active ? 'active' : ''}`}
                        onClick={() => applyStandardTier(tier.name)}
                      >
                        <strong>{tier.name}</strong>
                        {form.targetRole === 'employer' && <span>{tier.employerHint}</span>}
                      </button>
                    );
                  })}
                  <button
                    type="button"
                    role="radio"
                    aria-checked={nameMode === 'custom'}
                    className={`admin-plan-tier-option admin-plan-tier-option-custom ${nameMode === 'custom' ? 'active' : ''}`}
                    onClick={applyCustomNameMode}
                  >
                    <strong>Khác</strong>
                    <span>Tự đặt tên gói mới</span>
                  </button>
                </div>

                <label className="admin-plan-name-field">
                  <span className="admin-plan-name-field-label">
                    {nameLocked ? 'Tên gói (theo bậc đã chọn)' : 'Nhập tên gói tùy chỉnh'}
                  </span>
                  <input
                    required
                    maxLength={80}
                    readOnly={nameLocked}
                    disabled={nameLocked}
                    placeholder={nameLocked ? form.name : 'Ví dụ: Startup, Enterprise, Trial...'}
                    value={form.name}
                    onChange={(e) => changePlanName(e.target.value)}
                    aria-invalid={Boolean(duplicateMessage)}
                    className={nameLocked ? 'is-locked' : ''}
                  />
                  <small className={duplicateMessage ? 'error' : 'muted'}>
                    {duplicateMessage
                      || (nameLocked
                        ? `Đang dùng bậc ${nameMode}. Chọn "Khác" nếu muốn đặt tên mới.`
                        : 'Chỉ khi chọn "Khác" mới chỉnh được tên gói.')}
                  </small>
                </label>
              </div>
              <label className="full">
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
                <label className="admin-plan-limit-card">
                  <span className="admin-plan-limit-title">Hạn đăng tin tối đa (ngày)</span>
                  <span className="muted">Số ngày tối đa cho phép chọn Hạn nộp hồ sơ tin đăng (Mặc định Free = 30 ngày)</span>
                  <input
                    type="number"
                    min={1}
                    value={features.limits.maxJobPostingDays || 30}
                    onChange={(e) => updateLimit('maxJobPostingDays', Number(e.target.value))}
                  />
                </label>
              )}
              {showEmployerLimits && (
                standardSelected ? (
                  <div className="admin-plan-limit-card">
                    <span className="admin-plan-limit-title">Ưu tiên tin</span>
                    <span className="muted">
                      Theo bậc {canonicalPlanName(form.name)} — tin được ưu tiên hiển thị.
                    </span>
                  </div>
                ) : (
                  <label className="admin-plan-limit-card">
                    <span className="admin-plan-limit-title">Ưu tiên tin (0–3)</span>
                    <span className="muted">Gói tùy chỉnh: 0 = thường, 1–3 = ưu tiên tăng dần</span>
                    <input
                      type="number"
                      min={0}
                      max={3}
                      value={features.limits.listingPriority}
                      onChange={(e) => updateLimit('listingPriority', Number(e.target.value))}
                    />
                  </label>
                )
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
                  <label className="admin-plan-limit-card">
                    <span className="admin-plan-limit-title">Tìm việc AI / tháng</span>
                    <span className="muted">Lượt tạo danh sách phù hợp; nhập -1 để không giới hạn</span>
                    <input
                      type="number"
                      min={-1}
                      value={features.limits.maxAiJobSearchesPerMonth}
                      onChange={(e) => updateLimit('maxAiJobSearchesPerMonth', Number(e.target.value))}
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
                <span>Loại tên</span>
                <strong>{standardSelected ? nameMode : 'Tùy chỉnh'}</strong>
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
                  <strong>
                    {resolveListingPriority(form.targetRole, form.name, features.limits) >= 1 ? 'Có' : 'Không'}
                  </strong>
                </li>
              )}
            </ul>
            {form.description?.trim() && (
              <p className="muted admin-plan-preview-desc">{form.description.trim()}</p>
            )}
            <div className="admin-plan-preview-actions">
              <button type="submit" disabled={saving || Boolean(duplicateMessage)}>
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
