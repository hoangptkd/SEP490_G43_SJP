import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminCategory, AdminSetting } from '../../types/admin';

type SettingsTab = 'general' | 'limits' | 'ai' | 'payment' | 'theme' | 'categories';

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

const booleanKeys = new Set([
  'maintenance_mode',
  'ai_interview_enabled',
  'payment_gateway_enabled',
  'payment_gateway_sandbox',
  'company_review_required',
]);

const textareaKeys = new Set([
  'ai_system_prompt',
  'ai_feedback_prompt',
]);

const tabKeys: Record<Exclude<SettingsTab, 'categories'>, string[]> = {
  general: ['site_name', 'support_email', 'maintenance_mode', 'company_review_required'],
  limits: ['max_free_job_posts', 'max_ai_sessions_per_day', 'max_applications_per_day'],
  ai: ['ai_interview_enabled', 'ai_system_prompt', 'ai_feedback_prompt'],
  payment: ['payment_gateway_enabled', 'payment_gateway_provider', 'payment_gateway_merchant_id', 'payment_gateway_sandbox'],
  theme: ['theme_mode', 'theme_primary_color'],
};

const labels: Record<string, string> = {
  payment_gateway_merchant_id: 'Merchant ID (tham chiếu — key thật vẫn lấy từ .env)',
  payment_gateway_sandbox: 'Sandbox thanh toán (tham chiếu UI)',
  theme_mode: 'Theme mode',
  theme_primary_color: 'Màu chủ đạo (áp dụng toàn site)',
  max_ai_sessions_per_day: 'Giới hạn phiên AI / ngày (user free)',
  max_applications_per_day: 'Giới hạn ứng tuyển / ngày (user free)',
  max_free_job_posts: 'Số tin miễn phí tối đa (employer free)',
  company_review_required: 'Bắt buộc duyệt hồ sơ công ty trước khi đăng tin',
  maintenance_mode: 'Chế độ bảo trì (chặn user thường)',
  ai_interview_enabled: 'Bật phỏng vấn AI',
  payment_gateway_enabled: 'Bật cổng thanh toán (gói trả phí)',
  payment_gateway_provider: 'Phương thức mặc định khi checkout',
  ai_system_prompt: 'AI Prompt Templates (hệ thống)',
  ai_feedback_prompt: 'AI Prompt phản hồi',
  site_name: 'Tên hệ thống',
  support_email: 'Email hỗ trợ',
};

const tabs: { value: SettingsTab; label: string }[] = [
  { value: 'general', label: 'Chung' },
  { value: 'limits', label: 'Giới hạn hệ thống' },
  { value: 'ai', label: 'AI Prompt' },
  { value: 'payment', label: 'Payment Gateway' },
  { value: 'theme', label: 'Theme' },
  { value: 'categories', label: 'Danh mục việc làm' },
];

export default function AdminSettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = (searchParams.get('tab') as SettingsTab) || 'general';
  const [tab, setTab] = useState<SettingsTab>(tabs.some((item) => item.value === initialTab) ? initialTab : 'general');
  const [settings, setSettings] = useState<AdminSetting[]>([]);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [categories, setCategories] = useState<AdminCategory[]>([]);
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [categoryName, setCategoryName] = useState('');
  const [categoryDescription, setCategoryDescription] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const visibleSettings = useMemo(() => {
    if (tab === 'categories') return [];
    const keys = tabKeys[tab];
    return settings.filter((item) => keys.includes(item.key));
  }, [settings, tab]);

  const loadSettings = useCallback(async () => {
    const data = await adminService.listSettings();
    setSettings(data);
    setDraft(Object.fromEntries(data.map((item) => [item.key, item.value])));
  }, []);

  const loadCategories = useCallback(async () => {
    setCategories(await adminService.listCategories(categoryFilter));
  }, [categoryFilter]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([loadSettings(), loadCategories()]);
      setError('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [loadSettings, loadCategories]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    setSearchParams({ tab }, { replace: true });
  }, [tab, setSearchParams]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (tab === 'categories') return;
    setSaving(true);
    try {
      const keys = tabKeys[tab];
      const payload = Object.fromEntries(keys.map((key) => [key, draft[key] ?? '']));
      const data = await adminService.updateSettings(payload);
      setSettings(data);
      setDraft(Object.fromEntries(data.map((item) => [item.key, item.value])));
      setSuccess('Đã lưu cấu hình.');
      setError('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setSaving(false);
    }
  }

  async function createCategory(event: FormEvent) {
    event.preventDefault();
    try {
      await adminService.createCategory({ name: categoryName, description: categoryDescription, status: 'active' });
      setCategoryName('');
      setCategoryDescription('');
      setSuccess('Đã thêm danh mục.');
      await loadCategories();
    } catch (err) {
      setError(readError(err));
    }
  }

  async function toggleCategory(item: AdminCategory) {
    try {
      const next = item.status.toLowerCase() === 'active' ? 'inactive' : 'active';
      await adminService.updateCategory(item.id, { status: next });
      setSuccess(next === 'active' ? 'Đã kích hoạt danh mục.' : 'Đã tạm ẩn danh mục.');
      await loadCategories();
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <h1>Cài đặt hệ thống</h1>
          <p className="muted">Cấu hình AI, thanh toán, theme, giới hạn hệ thống và danh mục việc làm.</p>
        </div>
        <button type="button" className="outline" onClick={load} disabled={loading}>
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      <div className="admin-filter-tabs">
        {tabs.map((item) => (
          <button
            key={item.value}
            type="button"
            className={tab === item.value ? 'active' : 'outline'}
            onClick={() => {
              setTab(item.value);
              setSuccess('');
              setError('');
            }}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab !== 'categories' ? (
        <form className="admin-company-detail-panel admin-settings-form" onSubmit={save}>
          <div className="admin-company-detail-header">
            <div>
              <h2>{tabs.find((item) => item.value === tab)?.label}</h2>
              <p className="muted">Các thay đổi áp dụng ngay cho hệ thống (giới hạn, AI, thanh toán, bảo trì, theme, duyệt công ty).</p>
            </div>
            <button type="submit" disabled={saving || loading}>
              {saving ? 'Đang lưu...' : 'Lưu cấu hình'}
            </button>
          </div>

          <div className="admin-settings-grid">
            {visibleSettings.map((item) => (
              <label key={item.key} className={textareaKeys.has(item.key) || !booleanKeys.has(item.key) ? 'full' : ''}>
                <span>{labels[item.key] || item.key}</span>
                <small className="muted">{item.description}</small>
                {booleanKeys.has(item.key) ? (
                  <select
                    value={draft[item.key] ?? item.value}
                    onChange={(e) => setDraft((prev) => ({ ...prev, [item.key]: e.target.value }))}
                  >
                    <option value="true">Bật</option>
                    <option value="false">Tắt</option>
                  </select>
                ) : item.key === 'theme_mode' ? (
                  <select
                    value={draft[item.key] ?? item.value}
                    onChange={(e) => setDraft((prev) => ({ ...prev, [item.key]: e.target.value }))}
                  >
                    <option value="light">Light</option>
                    <option value="dark">Dark</option>
                  </select>
                ) : item.key === 'payment_gateway_provider' ? (
                  <select
                    value={draft[item.key] ?? item.value}
                    onChange={(e) => setDraft((prev) => ({ ...prev, [item.key]: e.target.value }))}
                  >
                    <option value="bank_transfer">Chuyển khoản QR</option>
                    <option value="vnpay">VNPay</option>
                    <option value="momo">MoMo</option>
                  </select>
                ) : item.key === 'theme_primary_color' ? (
                  <input
                    type="color"
                    value={draft[item.key] || '#00507d'}
                    onChange={(e) => setDraft((prev) => ({ ...prev, [item.key]: e.target.value }))}
                  />
                ) : textareaKeys.has(item.key) ? (
                  <textarea
                    value={draft[item.key] ?? item.value}
                    onChange={(e) => setDraft((prev) => ({ ...prev, [item.key]: e.target.value }))}
                    rows={5}
                  />
                ) : (
                  <input
                    value={draft[item.key] ?? item.value}
                    onChange={(e) => setDraft((prev) => ({ ...prev, [item.key]: e.target.value }))}
                  />
                )}
              </label>
            ))}
          </div>

          {!loading && visibleSettings.length === 0 && (
            <div className="admin-placeholder-card">
              <p>Chưa có cấu hình cho nhóm này. Hãy làm mới hoặc khởi động lại backend.</p>
            </div>
          )}
        </form>
      ) : (
        <>
          <form className="admin-company-detail-panel admin-settings-form" onSubmit={createCategory}>
            <h2>Thêm danh mục việc làm</h2>
            <div className="admin-settings-grid">
              <label>
                Tên danh mục
                <input value={categoryName} onChange={(e) => setCategoryName(e.target.value)} required placeholder="Ví dụ: Công nghệ thông tin" />
              </label>
              <label className="full">
                Mô tả
                <textarea value={categoryDescription} onChange={(e) => setCategoryDescription(e.target.value)} placeholder="Mô tả ngắn" />
              </label>
            </div>
            <div className="admin-company-actions">
              <button type="submit">Thêm danh mục</button>
            </div>
          </form>

          <div className="admin-filter-tabs">
            {[
              { value: 'all', label: 'Tất cả' },
              { value: 'active', label: 'Đang dùng' },
              { value: 'inactive', label: 'Tạm ẩn' },
            ].map((item) => (
              <button
                key={item.value}
                type="button"
                className={categoryFilter === item.value ? 'active' : 'outline'}
                onClick={() => setCategoryFilter(item.value)}
              >
                {item.label}
              </button>
            ))}
          </div>

          <section className="admin-company-list-panel">
            <div className="admin-company-list-header">
              <h2>Danh sách danh mục</h2>
              <span>{categories.length} mục</span>
            </div>
            <div className="admin-review-list">
              {categories.map((item) => (
                <article key={item.id} className="admin-review-row">
                  <div className="admin-review-main">
                    <div className="admin-review-title-line">
                      <strong>{item.name}</strong>
                      <span className={`admin-status-badge ${item.status === 'active' ? 'status-verified' : 'status-unverified'}`}>
                        {item.status === 'active' ? 'Đang dùng' : 'Tạm ẩn'}
                      </span>
                    </div>
                    <div className="admin-review-meta">
                      <span>/{item.slug}</span>
                      {item.description && <span>{item.description}</span>}
                    </div>
                  </div>
                  <button type="button" className="outline" onClick={() => toggleCategory(item)}>
                    {item.status === 'active' ? 'Tạm ẩn' : 'Kích hoạt'}
                  </button>
                </article>
              ))}
              {!loading && categories.length === 0 && <div className="admin-placeholder-card"><p>Chưa có danh mục.</p></div>}
            </div>
          </section>
        </>
      )}
    </section>
  );
}
