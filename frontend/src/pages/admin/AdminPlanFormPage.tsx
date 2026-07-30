import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminPlan } from '../../types/admin';

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
    featuresJson: '{\n  "benefits": []\n}',
    status: 'active',
    sortOrder: 0,
  };
}

function normalizeStatus(status?: string) {
  return status?.toLowerCase() === 'inactive' ? 'inactive' : 'active';
}

export default function AdminPlanFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const [form, setForm] = useState(emptyForm());
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

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
      })
      .catch((err) => setError(readError(err)))
      .finally(() => setLoading(false));
  }, [id]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    const payload = {
      name: form.name.trim(),
      targetRole: form.targetRole,
      description: form.description?.trim() || '',
      price: Number(form.price) || 0,
      currency: form.currency?.trim() || 'VND',
      durationDays: Number(form.durationDays) || 30,
      featuresJson: form.featuresJson?.trim() || '{}',
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
              ? 'Cập nhật thông tin, giá và trạng thái mở bán của gói.'
              : 'Nhập thông tin gói để mở bán cho ứng viên hoặc nhà tuyển dụng.'}
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
            <select
              value={form.targetRole}
              onChange={(e) => setForm({ ...form, targetRole: e.target.value })}
            >
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
              rows={4}
              placeholder="Mô tả quyền lợi của gói..."
              value={form.description || ''}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
          </label>
          <label className="full">
            Quyền lợi gói (Features JSON)
            <small className="muted">
              Dữ liệu cấu trúc của gói: danh sách quyền lợi hiển thị cho user, hạn mức tin đăng, v.v.
              Ví dụ: {'{"benefits":["Đăng tin không giới hạn","Ưu tiên hiển thị"],"maxJobs":20}'}
            </small>
            <textarea
              rows={6}
              value={form.featuresJson || '{}'}
              onChange={(e) => setForm({ ...form, featuresJson: e.target.value })}
            />
          </label>
        </div>
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
