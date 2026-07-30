import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminPlan } from '../../types/admin';

type PlanLimits = {
  maxJobs: number;
  maxCv: number;
  maxApplicationsPerDay: number;
  maxAiSessionsPerDay: number;
};

type FeatureState = {
  selectedBenefits: string[];
  customBenefit: string;
  limits: PlanLimits;
};

const DEFAULT_LIMITS: PlanLimits = {
  maxJobs: 20,
  maxCv: 10,
  maxApplicationsPerDay: 50,
  maxAiSessionsPerDay: 20,
};

const BENEFIT_PRESETS: Record<string, string[]> = {
  employer: [
    'Đăng tin tuyển dụng theo hạn mức gói',
    'Xem và quản lý ứng viên ứng tuyển',
    'Tin nổi bật / ưu tiên hiển thị',
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
    'Hỗ trợ ưu tiên từ admin',
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
    name: '',
    targetRole: 'employer',
    description: '',
    price: 0,
    currency: 'VND',
    durationDays: 30,
    featuresJson: '',
    status: 'active',
    sortOrder: 0,
  };
}

function normalizeStatus(status?: string) {
  return status?.toLowerCase() === 'inactive' ? 'inactive' : 'active';
}

function defaultFeaturesForRole(role: string): FeatureState {
  const presets = BENEFIT_PRESETS[role] || BENEFIT_PRESETS.employer;
  return {
    selectedBenefits: presets.slice(0, 2),
    customBenefit: '',
    limits: {
      ...DEFAULT_LIMITS,
      maxJobs: role === 'job_seeker' ? 5 : 20,
      maxCv: role === 'employer' ? 5 : 10,
      maxApplicationsPerDay: role === 'employer' ? 20 : 50,
      maxAiSessionsPerDay: role === 'employer' ? 5 : 20,
    },
  };
}

function parseFeatures(featuresJson: string | undefined, role: string): FeatureState {
  const fallback = defaultFeaturesForRole(role);
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
      },
    };
  } catch {
    return fallback;
  }
}

function buildFeaturesJson(state: FeatureState): string {
  const custom = state.customBenefit
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
  const benefits = Array.from(new Set([...state.selectedBenefits, ...custom]));
  return JSON.stringify(
    {
      benefits,
      maxJobs: Number(state.limits.maxJobs) || 0,
      maxCv: Number(state.limits.maxCv) || 0,
      maxApplicationsPerDay: Number(state.limits.maxApplicationsPerDay) || 0,
      maxAiSessionsPerDay: Number(state.limits.maxAiSessionsPerDay) || 0,
    },
    null,
    2,
  );
}

export default function AdminPlanFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const [form, setForm] = useState(emptyForm());
  const [features, setFeatures] = useState<FeatureState>(() => defaultFeaturesForRole('employer'));
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const presets = useMemo(
    () => BENEFIT_PRESETS[form.targetRole] || BENEFIT_PRESETS.employer,
    [form.targetRole],
  );

  const showEmployerLimits = form.targetRole === 'employer' || form.targetRole === 'all';
  const showCandidateLimits = form.targetRole === 'job_seeker' || form.targetRole === 'all';

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
        setForm({
          ...plan,
          status: normalizeStatus(plan.status),
          featuresJson: plan.featuresJson || '{}',
          description: plan.description || '',
        });
        setFeatures(parseFeatures(plan.featuresJson, plan.targetRole));
      })
      .catch((err) => setError(readError(err)))
      .finally(() => setLoading(false));
  }, [id]);

  function changeTargetRole(nextRole: string) {
    setForm((prev) => ({ ...prev, targetRole: nextRole }));
    setFeatures((prev) => {
      const nextPresets = BENEFIT_PRESETS[nextRole] || BENEFIT_PRESETS.employer;
      const kept = prev.selectedBenefits.filter((item) => nextPresets.includes(item));
      const nextDefaults = defaultFeaturesForRole(nextRole);
      return {
        selectedBenefits: kept.length > 0 ? kept : nextDefaults.selectedBenefits,
        customBenefit: prev.customBenefit,
        limits: {
          ...nextDefaults.limits,
          // giữ số đã nhập nếu admin đang chỉnh
          maxJobs: prev.limits.maxJobs || nextDefaults.limits.maxJobs,
          maxCv: prev.limits.maxCv || nextDefaults.limits.maxCv,
          maxApplicationsPerDay: prev.limits.maxApplicationsPerDay || nextDefaults.limits.maxApplicationsPerDay,
          maxAiSessionsPerDay: prev.limits.maxAiSessionsPerDay || nextDefaults.limits.maxAiSessionsPerDay,
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
      limits: { ...prev.limits, [key]: value },
    }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    const featuresJson = buildFeaturesJson(features);
    const payload = {
      name: form.name.trim(),
      targetRole: form.targetRole,
      description: form.description?.trim() || '',
      price: Number(form.price) || 0,
      currency: form.currency?.trim() || 'VND',
      durationDays: Number(form.durationDays) || 30,
      featuresJson,
      status: normalizeStatus(form.status),
      sortOrder: Number(form.sortOrder) || 0,
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
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <p className="muted">
            <Link to="/admin/billing?tab=plans">← Quay lại gói dịch vụ</Link>
          </p>
          <h1>{isEdit ? 'Chỉnh sửa gói dịch vụ' : 'Tạo gói dịch vụ mới'}</h1>
          <p className="muted">
            {isEdit
              ? 'Cập nhật thông tin, giá, quyền lợi và hạn mức của gói.'
              : 'Chọn đối tượng → tích quyền lợi → chỉnh hạn mức. Không cần viết JSON.'}
          </p>
        </div>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}

      <form className="admin-company-detail-panel admin-settings-form admin-plan-form" onSubmit={handleSubmit}>
        <div className="admin-settings-grid">
          <label>
            Tên gói
            <input
              required
              placeholder="Ví dụ: Employer Pro"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
          </label>
          <label>
            Đối tượng
            <select value={form.targetRole} onChange={(e) => changeTargetRole(e.target.value)}>
              <option value="job_seeker">Ứng viên</option>
              <option value="employer">Nhà tuyển dụng</option>
              <option value="all">Tất cả</option>
            </select>
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
          </label>
          <label>
            Đơn vị tiền
            <input
              value={form.currency}
              onChange={(e) => setForm({ ...form, currency: e.target.value })}
            />
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
              <option value="active">Đang hoạt động</option>
              <option value="inactive">Tạm tắt</option>
            </select>
          </label>
          <label>
            Thứ tự hiển thị
            <input
              type="number"
              value={form.sortOrder}
              onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })}
            />
          </label>
          <label className="full">
            Mô tả
            <textarea
              rows={3}
              placeholder="Mô tả ngắn về gói..."
              value={form.description || ''}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
          </label>
        </div>

        <section className="admin-plan-features">
          <div className="admin-plan-features-header">
            <h2>Quyền lợi & hạn mức</h2>
            <p className="muted">
              Tích quyền lợi hiển thị cho user. Hạn mức bên dưới là giới hạn thật khi dùng hệ thống.
            </p>
          </div>

          <div className="admin-plan-benefit-list">
            {presets.map((benefit) => {
              const checked = features.selectedBenefits.includes(benefit);
              return (
                <label key={benefit} className={`admin-plan-benefit-item ${checked ? 'checked' : ''}`}>
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => toggleBenefit(benefit)}
                  />
                  <span>{benefit}</span>
                </label>
              );
            })}
          </div>

          {/* Custom benefits already saved that are not in presets */}
          {features.selectedBenefits.some((b) => !presets.includes(b)) && (
            <div className="admin-plan-benefit-list" style={{ marginTop: 10 }}>
              <p className="muted" style={{ margin: '0 0 8px', gridColumn: '1 / -1' }}>Quyền lợi tùy chỉnh đang có</p>
              {features.selectedBenefits
                .filter((b) => !presets.includes(b))
                .map((benefit) => (
                  <label key={benefit} className="admin-plan-benefit-item checked">
                    <input type="checkbox" checked onChange={() => toggleBenefit(benefit)} />
                    <span>{benefit}</span>
                  </label>
                ))}
            </div>
          )}

          <label className="full" style={{ marginTop: 14 }}>
            Thêm quyền lợi khác (mỗi dòng 1 quyền lợi)
            <textarea
              rows={3}
              placeholder={'Ví dụ:\nƯu tiên hỗ trợ 24/7\nBadge xác thực doanh nghiệp'}
              value={features.customBenefit}
              onChange={(e) => setFeatures((prev) => ({ ...prev, customBenefit: e.target.value }))}
            />
          </label>

          <div className="admin-settings-grid" style={{ marginTop: 16 }}>
            {showEmployerLimits && (
              <label>
                Số tin đăng tối đa (maxJobs)
                <input
                  type="number"
                  min={0}
                  value={features.limits.maxJobs}
                  onChange={(e) => updateLimit('maxJobs', Number(e.target.value))}
                />
              </label>
            )}
            {showCandidateLimits && (
              <>
                <label>
                  Số CV tối đa (maxCv)
                  <input
                    type="number"
                    min={0}
                    value={features.limits.maxCv}
                    onChange={(e) => updateLimit('maxCv', Number(e.target.value))}
                  />
                </label>
                <label>
                  Ứng tuyển / ngày (maxApplicationsPerDay)
                  <input
                    type="number"
                    min={0}
                    value={features.limits.maxApplicationsPerDay}
                    onChange={(e) => updateLimit('maxApplicationsPerDay', Number(e.target.value))}
                  />
                </label>
                <label>
                  Phiên AI / ngày (maxAiSessionsPerDay)
                  <input
                    type="number"
                    min={0}
                    value={features.limits.maxAiSessionsPerDay}
                    onChange={(e) => updateLimit('maxAiSessionsPerDay', Number(e.target.value))}
                  />
                </label>
              </>
            )}
            {showEmployerLimits && !showCandidateLimits && (
              <>
                {/* keep unused keys in JSON with sensible defaults for employer */}
                <input type="hidden" value={features.limits.maxCv} readOnly />
              </>
            )}
          </div>
        </section>

        <div className="admin-company-actions">
          <button type="submit" disabled={saving}>
            {saving ? 'Đang lưu...' : isEdit ? 'Lưu thay đổi' : 'Tạo gói'}
          </button>
          <Link className="button-link outline" to="/admin/billing?tab=plans">
            Hủy
          </Link>
        </div>
      </form>
    </section>
  );
}
