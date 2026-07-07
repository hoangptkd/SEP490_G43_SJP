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
        background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
        color: '#fff',
        padding: '32px',
        borderRadius: '10px',
        marginBottom: '28px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '20px',
        boxShadow: '0 4px 15px rgba(0,0,0,0.08)'
      }}>
        <div>
          <h1 style={{ color: '#fff', marginBottom: '8px', fontSize: '1.75rem', fontWeight: 700 }}>Quản lý chi nhánh & Văn phòng</h1>
          <p style={{ margin: 0, opacity: 0.85, fontSize: '0.95rem', color: '#cbd5e1' }}>
            Danh sách các địa điểm hoạt động và văn phòng làm việc của doanh nghiệp.
          </p>
        </div>
        <button
          type="button"
          onClick={handleOpenAdd}
          style={{
            background: '#2563eb',
            color: '#fff',
            border: 'none',
            padding: '10px 20px',
            borderRadius: '6px',
            fontWeight: 600,
            cursor: 'pointer',
            fontSize: '0.9rem',
            boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)',
            transition: 'background-color 0.2s'
          }}
        >
          + Thêm địa điểm mới
        </button>
      </div>

      {message && <p className="success" style={{ marginBottom: '20px', padding: '12px 16px', background: '#ecfdf5', border: '1px solid #a7f3d0', color: '#047857', borderRadius: '6px', fontSize: '0.9rem' }}>{message}</p>}
      {error && !showForm && <p className="error" style={{ marginBottom: '20px', padding: '12px 16px', background: '#fef2f2', border: '1px solid #fecaca', color: '#b91c1c', borderRadius: '6px', fontSize: '0.9rem' }}>{error}</p>}

      {showForm && (
        <div style={{
          background: '#f8fafc',
          border: '1px solid #e2e8f0',
          borderRadius: '10px',
          padding: '24px',
          marginBottom: '28px',
          boxShadow: '0 1px 3px rgba(0,0,0,0.02)'
        }}>
          <h3 style={{ marginTop: 0, color: '#0f172a', marginBottom: '20px', fontSize: '1.2rem', fontWeight: 700, borderBottom: '1px solid #e2e8f0', paddingBottom: '12px' }}>
            {editingId ? 'Chỉnh sửa thông tin chi nhánh' : 'Thêm chi nhánh văn phòng mới'}
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
              <span style={{ fontWeight: 600, color: '#0f172a' }}>Đặt làm Trụ sở chính (Head Office)</span>
            </label>

            <div className="wide" style={{ display: 'flex', gap: '12px', marginTop: '16px', borderTop: '1px solid #e2e8f0', paddingTop: '16px' }}>
              <button
                type="submit"
                disabled={saving}
                style={{
                  background: '#2563eb',
                  color: '#fff',
                  border: 'none',
                  padding: '10px 24px',
                  borderRadius: '6px',
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  cursor: saving ? 'wait' : 'pointer',
                  boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)',
                  transition: 'background-color 0.2s'
                }}
              >
                {saving ? 'Đang xử lý...' : (editingId ? 'Lưu thay đổi' : 'Thêm mới')}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                style={{
                  background: 'transparent',
                  color: '#64748b',
                  border: 'none',
                  padding: '10px 18px',
                  borderRadius: '6px',
                  fontWeight: 500,
                  fontSize: '0.9rem',
                  cursor: 'pointer'
                }}
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
          <div style={{ textAlign: 'center', padding: '48px 24px', background: '#f8fafc', borderRadius: '8px', border: '1px dashed #cbd5e1' }}>
            <p style={{ color: '#64748b', fontSize: '1.05rem', margin: '0 0 16px 0' }}>
              Chưa có địa điểm làm việc nào được cấu hình trên hệ thống.
            </p>
          </div>
        ) : (
          <div style={{ display: 'grid', gap: '14px' }}>
            {locations.map((loc) => (
              <div key={loc.id} style={{
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
                padding: '18px 22px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '16px',
                background: loc.headquarter ? '#f0fdf4' : '#ffffff',
                boxShadow: '0 1px 3px rgba(0,0,0,0.02)'
              }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px', flexWrap: 'wrap' }}>
                    <strong style={{ fontSize: '1.1rem', color: '#0f172a', fontWeight: 700 }}>{loc.branchName}</strong>
                    {loc.headquarter && (
                      <span style={{
                        background: '#059669',
                        color: '#fff',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        padding: '2px 10px',
                        borderRadius: '12px'
                      }}>
                        Trụ sở chính
                      </span>
                    )}
                  </div>
                  <p style={{ margin: 0, color: '#64748b', fontSize: '0.9rem', lineHeight: 1.5 }}>
                    {loc.address ? `${loc.address}, ` : ''}
                    {loc.district ? `${loc.district}, ` : ''}
                    {loc.city || ''}
                    {loc.country ? ` (${loc.country})` : ''}
                  </p>
                </div>
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  <button
                    type="button"
                    onClick={() => handleOpenEdit(loc)}
                    style={{
                      background: '#f8fafc',
                      color: '#334155',
                      border: '1px solid #cbd5e1',
                      padding: '8px 16px',
                      borderRadius: '6px',
                      cursor: 'pointer',
                      fontWeight: 600,
                      fontSize: '0.85rem',
                      transition: 'all 0.2s'
                    }}
                  >
                    Chỉnh sửa
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(loc.id, loc.headquarter)}
                    style={{
                      background: loc.headquarter ? '#f1f5f9' : '#ffffff',
                      color: loc.headquarter ? '#94a3b8' : '#ef4444',
                      border: loc.headquarter ? '1px solid #cbd5e1' : '1px solid #fecaca',
                      padding: '8px 16px',
                      borderRadius: '6px',
                      cursor: loc.headquarter ? 'not-allowed' : 'pointer',
                      fontWeight: 500,
                      fontSize: '0.85rem',
                      transition: 'all 0.2s'
                    }}
                    disabled={loc.headquarter}
                    title={loc.headquarter ? 'Không thể xóa trụ sở chính' : 'Xóa chi nhánh'}
                  >
                    Xóa
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

export default CompanyLocationsPage;
