import { FormEvent, useEffect, useState } from 'react';
import { employerService } from '../../services/employerService';
import type { Company } from '../../types/job';

function CompanyProfilePage() {
  const [company, setCompany] = useState<Company | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    employerService.getCompanyProfile()
      .then((data) => {
        setCompany(data);
        setLoading(false);
      })
      .catch((err) => {
        setError('Không thể tải thông tin công ty.');
        setLoading(false);
      });
  }, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!company) return;
    setSaving(true);
    setMessage('');
    setError('');
    try {
      const updated = await employerService.updateCompanyProfile(company);
      setCompany(updated);
      if (updated.verificationStatus?.toLowerCase() === 'pending') {
        setMessage('Lưu hồ sơ thành công. Hồ sơ đã được gửi và đang chờ admin duyệt.');
      } else {
        setMessage('Lưu hồ sơ công ty thành công.');
      }
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Có lỗi xảy ra khi lưu thông tin công ty.');
      }
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <p className="loading">Đang tải...</p>;
  if (!company) return <div className="content-card"><p className="error">{error || 'Không tìm thấy thông tin công ty.'}</p></div>;

  const verificationStatus = company.verificationStatus?.toLowerCase() || 'unverified';
  const statusBadge = verificationStatus === 'verified'
    ? { text: '✓ Đã xác thực', bg: '#e4eee7', color: '#245d43' }
    : verificationStatus === 'pending'
    ? { text: '⏳ Chờ duyệt', bg: '#fff3cd', color: '#856404' }
    : verificationStatus === 'rejected'
    ? { text: '✕ Bị từ chối', bg: '#f8d7da', color: '#842029' }
    : { text: '○ Chưa gửi duyệt', bg: '#e2e3e5', color: '#41464b' };

  return (
    <section className="content-card">
      <div className="company-profile-banner" style={{
        background: 'linear-gradient(135deg, #245d43 0%, #123327 100%)',
        color: '#fff',
        padding: '30px',
        borderRadius: '8px',
        marginBottom: '24px',
        position: 'relative',
        boxShadow: '0 4px 15px rgba(0,0,0,0.1)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <h1 style={{ color: '#fff', marginBottom: '8px', fontSize: '2rem', fontWeight: 800 }}>{company.name}</h1>
            <p style={{ margin: 0, opacity: 0.9, fontSize: '1rem' }}>
              {company.industry || 'Chưa cập nhật ngành nghề'} · {company.location || 'Chưa cập nhật địa điểm'}
            </p>
          </div>
          <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
            <span style={{
              background: statusBadge.bg,
              color: statusBadge.color,
              padding: '6px 12px',
              borderRadius: '20px',
              fontSize: '0.85rem',
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.5px'
            }}>
              {statusBadge.text}
            </span>
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="form-grid two">
        <label className="wide">
          Tên công ty *
          <input
            required
            value={company.name}
            onChange={(e) => setCompany({ ...company, name: e.target.value })}
            placeholder="Tên chính thức của doanh nghiệp"
          />
        </label>

        <label>
          Website
          <input
            value={company.website || ''}
            onChange={(e) => setCompany({ ...company, website: e.target.value })}
            placeholder="https://example.com"
          />
        </label>

        <label>
          Ngành nghề
          <input
            value={company.industry || ''}
            onChange={(e) => setCompany({ ...company, industry: e.target.value })}
            placeholder="Ví dụ: Công nghệ thông tin, Bán lẻ, Giáo dục..."
          />
        </label>

        <label>
          Địa điểm trụ sở (Head Office)
          {company.locations && company.locations.length > 0 ? (
            <select
              value={company.location || ''}
              onChange={(e) => setCompany({ ...company, location: e.target.value })}
            >
              <option value="">-- Chọn chi nhánh làm trụ sở chính --</option>
              {company.locations.map((loc) => (
                <option key={loc.id} value={loc.branchName}>
                  {loc.branchName} {loc.city ? `(${loc.city})` : ''} {loc.headquarter ? '★ [Trụ sở hiện tại]' : ''}
                </option>
              ))}
            </select>
          ) : (
            <input
              value={company.location || ''}
              onChange={(e) => setCompany({ ...company, location: e.target.value })}
              placeholder="Thành phố hoặc địa chỉ chi tiết (Hoặc vào Quản lý chi nhánh để thêm)"
            />
          )}
        </label>

        <label>
          Quy mô nhân sự
          <input
            type="number"
            min="0"
            value={company.companySize === undefined || company.companySize === null ? '' : company.companySize}
            onChange={(e) => setCompany({ ...company, companySize: e.target.value ? parseInt(e.target.value) : undefined })}
            placeholder="Số lượng nhân viên"
          />
        </label>

        <label className="wide">
          Mã số thuế
          <input
            value={company.taxCode || ''}
            onChange={(e) => setCompany({ ...company, taxCode: e.target.value })}
            placeholder="Mã số thuế doanh nghiệp"
          />
        </label>

        <label className="wide">
          Mô tả / Giới thiệu công ty
          <textarea
            value={company.description || ''}
            onChange={(e) => setCompany({ ...company, description: e.target.value })}
            placeholder="Giới thiệu về lịch sử, sứ mệnh, môi trường làm việc..."
          />
        </label>

        <div className="wide" style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {message && <p className="success" style={{ margin: 0 }}>{message}</p>}
          {error && <p className="error" style={{ margin: 0 }}>{error}</p>}
          <button type="submit" disabled={saving} style={{ alignSelf: 'flex-start' }}>
            {saving ? 'Đang lưu...' : 'Lưu hồ sơ công ty'}
          </button>
        </div>
      </form>

      {company.locations && company.locations.length > 0 && (
        <div style={{ marginTop: '36px', borderTop: '1px solid #eaeaea', paddingTop: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h3 style={{ margin: 0, color: '#111827', fontSize: '1.2rem' }}>Các chi nhánh & Văn phòng ({company.locations.length})</h3>
            <a href="/employer/locations" style={{ color: '#245d43', fontWeight: 600, textDecoration: 'none' }}>
              Quản lý chi nhánh →
            </a>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '12px' }}>
            {company.locations.map((loc) => (
              <div key={loc.id} style={{
                border: '1px solid #e5e7eb',
                borderRadius: '6px',
                padding: '12px 16px',
                background: loc.headquarter ? '#f0fdf4' : '#f9fafb'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                  <strong style={{ color: '#1f2937' }}>{loc.branchName}</strong>
                  {loc.headquarter && <span style={{ fontSize: '0.7rem', background: '#245d43', color: '#fff', padding: '1px 6px', borderRadius: '10px' }}>HQ</span>}
                </div>
                <p style={{ margin: 0, fontSize: '0.85rem', color: '#6b7280' }}>
                  {loc.address ? `${loc.address}, ` : ''}{loc.city || ''}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}

export default CompanyProfilePage;
