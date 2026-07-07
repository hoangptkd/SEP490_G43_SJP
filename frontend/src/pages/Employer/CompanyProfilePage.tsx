import { FormEvent, useEffect, useState } from 'react';
import { employerService } from '../../services/employerService';
import { jobService } from '../../services/jobService';
import type { Company, Category } from '../../types/job';

function CompanyProfilePage() {
  const [company, setCompany] = useState<Company | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploadingLogo, setUploadingLogo] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      employerService.getCompanyProfile(),
      jobService.getCategories().catch(() => [])
    ])
      .then(([companyData, categoriesData]) => {
        setCompany(companyData);
        setCategories(categoriesData);
        setLoading(false);
      })
      .catch((err) => {
        setError('Không thể tải thông tin công ty.');
        setLoading(false);
      });
  }, []);

  async function handleLogoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setUploadingLogo(true);
    setMessage('');
    setError('');
    try {
      const updated = await employerService.uploadLogo(files[0]);
      setCompany(updated);
      setMessage('Tải lên logo thành công!');
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Có lỗi xảy ra khi tải lên logo.');
      }
    } finally {
      setUploadingLogo(false);
    }
  }

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
    ? { text: 'Đã xác thực', bg: '#ecfdf5', color: '#047857', border: '#a7f3d0', dot: '#10b981' }
    : verificationStatus === 'pending'
    ? { text: 'Chờ kiểm duyệt', bg: '#eff6ff', color: '#1d4ed8', border: '#bfdbfe', dot: '#3b82f6' }
    : verificationStatus === 'rejected'
    ? { text: 'Yêu cầu bổ sung hồ sơ', bg: '#fef2f2', color: '#b91c1c', border: '#fecaca', dot: '#ef4444' }
    : { text: 'Chưa xác thực', bg: '#f8fafc', color: '#475569', border: '#cbd5e1', dot: '#94a3b8' };

  return (
    <section className="content-card">
      <div className="company-profile-banner" style={{
        background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
        color: '#fff',
        padding: '32px',
        borderRadius: '10px',
        marginBottom: '28px',
        position: 'relative',
        boxShadow: '0 4px 15px rgba(0,0,0,0.08)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            <div style={{
              width: '84px',
              height: '84px',
              borderRadius: '12px',
              backgroundColor: '#fff',
              border: '1px solid rgba(255,255,255,0.2)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              position: 'relative',
              flexShrink: 0,
              boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
            }}>
              {company.logoUrl ? (
                <img src={company.logoUrl} alt={company.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                <span style={{ fontSize: '2rem', fontWeight: 700, color: '#0f172a' }}>
                  {company.name ? company.name.charAt(0).toUpperCase() : 'C'}
                </span>
              )}
            </div>
            <div>
              <h1 style={{ color: '#fff', marginBottom: '8px', fontSize: '1.75rem', fontWeight: 700 }}>{company.name}</h1>
              <p style={{ margin: 0, opacity: 0.85, fontSize: '0.95rem', color: '#cbd5e1' }}>
                {company.industry || 'Chưa cập nhật ngành nghề'} · {company.location || 'Chưa cập nhật địa điểm'}
              </p>
            </div>
          </div>
          <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
            <label style={{
              background: 'rgba(255,255,255,0.1)',
              border: '1px solid rgba(255,255,255,0.2)',
              color: '#fff',
              padding: '8px 16px',
              borderRadius: '6px',
              cursor: uploadingLogo ? 'wait' : 'pointer',
              fontSize: '0.875rem',
              fontWeight: 500,
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              transition: 'background 0.2s',
              margin: 0
            }}>
              <span>{uploadingLogo ? 'Đang cập nhật...' : 'Thay đổi logo'}</span>
              <input type="file" accept="image/*" onChange={handleLogoChange} disabled={uploadingLogo} style={{ display: 'none' }} />
            </label>
            <span style={{
              background: statusBadge.bg,
              color: statusBadge.color,
              border: `1px solid ${statusBadge.border}`,
              padding: '6px 14px',
              borderRadius: '20px',
              fontSize: '0.85rem',
              fontWeight: 600,
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px'
            }}>
              <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: statusBadge.dot }}></span>
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
          Ngành nghề hoạt động
          {categories.length > 0 ? (
            <select
              value={company.industry || ''}
              onChange={(e) => setCompany({ ...company, industry: e.target.value })}
              style={{ padding: '10px 12px', borderRadius: '6px', border: '1px solid #d1d5db', background: '#fff', fontSize: '0.95rem' }}
            >
              <option value="">-- Chọn lĩnh vực / ngành nghề chính --</option>
              {categories
                .filter((c) => !c.parentId)
                .map((cat) => (
                  <option key={cat.id} value={cat.name}>
                    {cat.name}
                  </option>
                ))}
              {company.industry && !categories.some(c => !c.parentId && c.name === company.industry) && (
                <option value={company.industry}>[{company.industry}] (Ngành hiện tại)</option>
              )}
            </select>
          ) : (
            <input
              value={company.industry || ''}
              onChange={(e) => setCompany({ ...company, industry: e.target.value })}
              placeholder="Ví dụ: Công nghệ thông tin, Bán lẻ, Giáo dục..."
            />
          )}
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
                  {loc.branchName} {loc.city ? `(${loc.city})` : ''} {loc.headquarter ? '(Trụ sở chính)' : ''}
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

        <div className="wide" style={{ marginTop: '20px', display: 'flex', flexDirection: 'column', gap: '12px', borderTop: '1px solid #e2e8f0', paddingTop: '20px' }}>
          {message && <p className="success" style={{ margin: 0, padding: '12px 16px', background: '#ecfdf5', border: '1px solid #a7f3d0', color: '#047857', borderRadius: '6px', fontSize: '0.9rem' }}>{message}</p>}
          {error && <p className="error" style={{ margin: 0, padding: '12px 16px', background: '#fef2f2', border: '1px solid #fecaca', color: '#b91c1c', borderRadius: '6px', fontSize: '0.9rem' }}>{error}</p>}
          <button
            type="submit"
            disabled={saving}
            style={{
              alignSelf: 'flex-start',
              background: '#2563eb',
              color: '#fff',
              border: 'none',
              padding: '10px 24px',
              borderRadius: '6px',
              fontWeight: 600,
              fontSize: '0.95rem',
              cursor: saving ? 'wait' : 'pointer',
              boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)',
              transition: 'background-color 0.2s'
            }}
          >
            {saving ? 'Đang xử lý...' : 'Lưu thay đổi'}
          </button>
        </div>
      </form>

      {company.locations && company.locations.length > 0 && (
        <div style={{ marginTop: '36px', borderTop: '1px solid #e2e8f0', paddingTop: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h3 style={{ margin: 0, color: '#0f172a', fontSize: '1.2rem', fontWeight: 700 }}>Các chi nhánh & Văn phòng ({company.locations.length})</h3>
            <a href="/employer/locations" style={{ color: '#2563eb', fontWeight: 600, textDecoration: 'none', fontSize: '0.9rem' }}>
              Quản lý chi nhánh →
            </a>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '14px' }}>
            {company.locations.map((loc) => (
              <div key={loc.id} style={{
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
                padding: '14px 18px',
                background: loc.headquarter ? '#f0fdf4' : '#ffffff',
                boxShadow: '0 1px 2px rgba(0,0,0,0.02)'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                  <strong style={{ color: '#0f172a', fontSize: '0.95rem' }}>{loc.branchName}</strong>
                  {loc.headquarter && <span style={{ fontSize: '0.75rem', background: '#059669', color: '#fff', padding: '2px 8px', borderRadius: '12px', fontWeight: 600 }}>Trụ sở chính</span>}
                </div>
                <p style={{ margin: 0, fontSize: '0.875rem', color: '#64748b' }}>
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
