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
      setMessage('Lưu hồ sơ công ty thành công.');
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
              background: company.verified ? '#e4eee7' : '#fff3cd',
              color: company.verified ? '#245d43' : '#856404',
              padding: '6px 12px',
              borderRadius: '20px',
              fontSize: '0.85rem',
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.5px'
            }}>
              {company.verified ? '✓ Đã xác thực' : '⚠ Chờ xác thực'}
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
          Địa điểm trụ sở
          <input
            value={company.location || ''}
            onChange={(e) => setCompany({ ...company, location: e.target.value })}
            placeholder="Thành phố hoặc địa chỉ chi tiết"
          />
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
    </section>
  );
}

export default CompanyProfilePage;
