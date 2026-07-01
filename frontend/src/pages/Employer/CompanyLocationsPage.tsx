import { FormEvent, useEffect, useState } from 'react';
import { employerService } from '../../services/employerService';
import type { CompanyLocation } from '../../types/job';

function CompanyLocationsPage() {
  const [locations, setLocations] = useState<CompanyLocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const [editingId, setEditingId] = useState<string | null>(null);
  const [formData, setFormData] = useState<Partial<CompanyLocation>>({
    branchName: '',
    address: '',
    city: '',
    district: '',
    country: 'Vietnam',
    headquarter: false,
  });
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    loadLocations();
  }, []);

  async function loadLocations() {
    setLoading(true);
    try {
      const data = await employerService.getLocations();
      setLocations(data);
    } catch (err) {
      setError('Không thể tải danh sách địa điểm làm việc.');
    } finally {
      setLoading(false);
    }
  }

  function handleOpenAdd() {
    setEditingId(null);
    setFormData({
      branchName: '',
      address: '',
      city: '',
      district: '',
      country: 'Vietnam',
      headquarter: locations.length === 0, // Nếu chưa có địa điểm nào thì tự đặt làm trụ sở
    });
    setShowForm(true);
    setMessage('');
    setError('');
  }

  function handleOpenEdit(loc: CompanyLocation) {
    setEditingId(loc.id);
    setFormData({
      branchName: loc.branchName,
      address: loc.address || '',
      city: loc.city || '',
      district: loc.district || '',
      country: loc.country || 'Vietnam',
      headquarter: loc.headquarter,
    });
    setShowForm(true);
    setMessage('');
    setError('');
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!formData.branchName) return;
    setSaving(true);
    setMessage('');
    setError('');
    try {
      if (editingId) {
        await employerService.updateLocation(editingId, formData);
        setMessage('Cập nhật địa điểm thành công.');
      } else {
        await employerService.createLocation(formData);
        setMessage('Thêm địa điểm mới thành công.');
      }
      setShowForm(false);
      await loadLocations();
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Có lỗi xảy ra khi lưu địa điểm làm việc.');
      }
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string, isHq: boolean) {
    if (isHq) {
      alert('Không thể xóa Trụ sở chính. Vui lòng đặt chi nhánh khác làm Trụ sở chính trước.');
      return;
    }
    if (!confirm('Bạn có chắc chắn muốn xóa địa điểm làm việc này?')) return;
    try {
      await employerService.deleteLocation(id);
      setMessage('Xóa địa điểm thành công.');
      await loadLocations();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Có lỗi xảy ra khi xóa địa điểm.');
    }
  }

  if (loading) return <p className="loading">Đang tải...</p>;

  return (
    <section className="content-card">
      <div className="company-profile-banner" style={{
        background: 'linear-gradient(135deg, #245d43 0%, #123327 100%)',
        color: '#fff',
        padding: '30px',
        borderRadius: '8px',
        marginBottom: '24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '16px',
        boxShadow: '0 4px 15px rgba(0,0,0,0.1)'
      }}>
        <div>
          <h1 style={{ color: '#fff', marginBottom: '8px', fontSize: '1.8rem', fontWeight: 800 }}>Quản lý Địa điểm làm việc</h1>
          <p style={{ margin: 0, opacity: 0.9, fontSize: '0.95rem' }}>
            Danh sách các chi nhánh và văn phòng làm việc của công ty.
          </p>
        </div>
        <button
          type="button"
          onClick={handleOpenAdd}
          style={{
            background: '#fff',
            color: '#245d43',
            border: 'none',
            padding: '10px 18px',
            borderRadius: '6px',
            fontWeight: 700,
            cursor: 'pointer',
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)'
          }}
        >
          + Thêm địa điểm mới
        </button>
      </div>

      {message && <p className="success" style={{ marginBottom: '16px' }}>{message}</p>}
      {error && !showForm && <p className="error" style={{ marginBottom: '16px' }}>{error}</p>}

      {showForm && (
        <div style={{
          background: '#f8f9fa',
          border: '1px solid #dee2e6',
          borderRadius: '8px',
          padding: '20px',
          marginBottom: '24px'
        }}>
          <h3 style={{ marginTop: 0, color: '#245d43', marginBottom: '16px' }}>
            {editingId ? 'Chỉnh sửa Địa điểm' : 'Thêm Địa điểm mới'}
          </h3>
          <form onSubmit={handleSubmit} className="form-grid two">
            <label className="wide">
              Tên chi nhánh / Văn phòng *
              <input
                required
                value={formData.branchName || ''}
                onChange={(e) => setFormData({ ...formData, branchName: e.target.value })}
                placeholder="Ví dụ: Trụ sở Hà Nội, Chi nhánh HCM..."
              />
            </label>

            <label className="wide">
              Địa chỉ chi tiết
              <input
                value={formData.address || ''}
                onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                placeholder="Số nhà, đường/phố, phường/xã..."
              />
            </label>

            <label>
              Quận / Huyện
              <input
                value={formData.district || ''}
                onChange={(e) => setFormData({ ...formData, district: e.target.value })}
                placeholder="Ví dụ: Cầu Giấy, Quận 1..."
              />
            </label>

            <label>
              Tỉnh / Thành phố
              <input
                value={formData.city || ''}
                onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                placeholder="Ví dụ: Hà Nội, TP. Hồ Chí Minh..."
              />
            </label>

            <label>
              Quốc gia
              <input
                value={formData.country || 'Vietnam'}
                onChange={(e) => setFormData({ ...formData, country: e.target.value })}
                placeholder="Vietnam"
              />
            </label>

            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', paddingTop: '24px' }}>
              <input
                type="checkbox"
                checked={formData.headquarter || false}
                onChange={(e) => setFormData({ ...formData, headquarter: e.target.checked })}
              />
              <span style={{ fontWeight: 600, color: '#245d43' }}>Đặt làm Trụ sở chính (Head Office)</span>
            </label>

            <div className="wide" style={{ display: 'flex', gap: '12px', marginTop: '12px' }}>
              <button type="submit" disabled={saving} style={{ background: '#245d43', color: '#fff' }}>
                {saving ? 'Đang lưu...' : (editingId ? 'Cập nhật' : 'Thêm mới')}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                style={{ background: '#6c757d', color: '#fff' }}
              >
                Hủy
              </button>
            </div>
            {error && <p className="error wide" style={{ margin: '8px 0 0' }}>{error}</p>}
          </form>
        </div>
      )}

      <div className="table-list">
        {locations.length === 0 ? (
          <p style={{ textAlign: 'center', padding: '30px', color: '#6c757d', margin: 0 }}>
            Chưa có địa điểm làm việc nào được cấu hình. Nhấn "+ Thêm địa điểm mới" để bắt đầu.
          </p>
        ) : (
          locations.map((loc) => (
            <div key={loc.id} style={{
              border: '1px solid #eaeaea',
              borderRadius: '8px',
              padding: '16px 20px',
              marginBottom: '12px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: '12px',
              background: loc.headquarter ? '#f0fdf4' : '#fff'
            }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                  <strong style={{ fontSize: '1.1rem', color: '#111827' }}>{loc.branchName}</strong>
                  {loc.headquarter && (
                    <span style={{
                      background: '#245d43',
                      color: '#fff',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                      padding: '2px 8px',
                      borderRadius: '12px',
                      textTransform: 'uppercase'
                    }}>
                      ★ Trụ sở chính
                    </span>
                  )}
                </div>
                <p style={{ margin: '0 0 4px', color: '#4b5563', fontSize: '0.95rem' }}>
                  {loc.address ? `${loc.address}, ` : ''}
                  {loc.district ? `${loc.district}, ` : ''}
                  {loc.city || ''}
                  {loc.country ? ` (${loc.country})` : ''}
                </p>
              </div>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  type="button"
                  onClick={() => handleOpenEdit(loc)}
                  style={{
                    background: '#e2e8f0',
                    color: '#334155',
                    border: 'none',
                    padding: '6px 14px',
                    borderRadius: '4px',
                    cursor: 'pointer',
                    fontWeight: 600,
                    fontSize: '0.85rem'
                  }}
                >
                  Sửa
                </button>
                <button
                  type="button"
                  onClick={() => handleDelete(loc.id, loc.headquarter)}
                  style={{
                    background: loc.headquarter ? '#f3f4f6' : '#fee2e2',
                    color: loc.headquarter ? '#9ca3af' : '#ef4444',
                    border: 'none',
                    padding: '6px 14px',
                    borderRadius: '4px',
                    cursor: loc.headquarter ? 'not-allowed' : 'pointer',
                    fontWeight: 600,
                    fontSize: '0.85rem'
                  }}
                  disabled={loc.headquarter}
                  title={loc.headquarter ? 'Không thể xóa trụ sở chính' : 'Xóa địa điểm'}
                >
                  Xóa
                </button>
              </div>
            </div>
          ))
        )}
      </div>
    </section>
  );
}

export default CompanyLocationsPage;
