import { FormEvent, useCallback, useEffect, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminCategory } from '../../types/admin';

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

export default function AdminCategoriesPage() {
  const [items, setItems] = useState<AdminCategory[]>([]);
  const [filter, setFilter] = useState('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await adminService.listCategories(filter));
      setError('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    load();
  }, [load]);

  async function createCategory(event: FormEvent) {
    event.preventDefault();
    try {
      await adminService.createCategory({ name, description, status: 'active' });
      setName('');
      setDescription('');
      setSuccess('Đã thêm danh mục mới.');
      await load();
    } catch (err) {
      setError(readError(err));
    }
  }

  async function toggleStatus(item: AdminCategory) {
    try {
      const next = item.status.toLowerCase() === 'active' ? 'inactive' : 'active';
      await adminService.updateCategory(item.id, { status: next });
      setSuccess(next === 'active' ? 'Đã kích hoạt danh mục.' : 'Đã tạm ẩn danh mục.');
      await load();
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <h1>Danh mục việc làm</h1>
          <p className="muted">Quản lý ngành nghề / lĩnh vực dùng cho tin tuyển dụng.</p>
        </div>
        <button type="button" className="outline" onClick={load} disabled={loading}>
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      <form className="admin-company-detail-panel admin-settings-form" onSubmit={createCategory}>
        <h2>Thêm danh mục</h2>
        <div className="admin-settings-grid">
          <label>
            Tên danh mục
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Ví dụ: Công nghệ thông tin" required />
          </label>
          <label className="full">
            Mô tả
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Mô tả ngắn" />
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
            className={filter === item.value ? 'active' : 'outline'}
            onClick={() => setFilter(item.value)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <section className="admin-company-list-panel">
        <div className="admin-company-list-header">
          <h2>Danh sách danh mục</h2>
          <span>{items.length} mục</span>
        </div>
        <div className="admin-review-list">
          {items.map((item) => (
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
              <button type="button" className="outline" onClick={() => toggleStatus(item)}>
                {item.status === 'active' ? 'Tạm ẩn' : 'Kích hoạt'}
              </button>
            </article>
          ))}
          {!loading && items.length === 0 && <div className="admin-placeholder-card"><p>Chưa có danh mục.</p></div>}
        </div>
      </section>
    </section>
  );
}
