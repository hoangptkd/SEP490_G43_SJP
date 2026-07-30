import { FormEvent, useEffect, useRef, useState } from 'react';
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
  const [isIndustryDropdownOpen, setIsIndustryDropdownOpen] = useState(false);
  const [industrySearchTerm, setIndustrySearchTerm] = useState('');
  const industryDropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (industryDropdownRef.current && !industryDropdownRef.current.contains(event.target as Node)) {
        setIsIndustryDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    Promise.all([
      employerService.getCompanyProfile(),
      jobService.getCategories().catch(() => [])
    ])
      .then(([companyData, categoriesData]) => {
        let inds = companyData.industries || [];
        if (inds.length === 0 && companyData.industry && categoriesData.length > 0) {
          const matchedCat = categoriesData.find(c => !c.parentId && c.name === companyData.industry) || categoriesData.find(c => c.name === companyData.industry);
          if (matchedCat) {
            inds = [{ categoryId: matchedCat.id, categoryName: matchedCat.name, primary: true }];
          }
        }
        setCompany({ ...companyData, industries: inds });
        setCategories(categoriesData);
        setLoading(false);
      })
      .catch(() => {
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

        <div className="wide" style={{ background: '#f8fafc', padding: '20px', borderRadius: '10px', border: '1px solid #e2e8f0', overflow: 'visible' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px', overflow: 'visible' }}>
            {/* Ô 1: Chọn nhiều lĩnh vực/ngành nghề hoạt động (Custom Multi-select Dropdown với Chips như ảnh) */}
            <div ref={industryDropdownRef} style={{ position: 'relative' }}>
              <label style={{ fontWeight: 600, fontSize: '0.95rem', color: '#0f172a', marginBottom: '8px', display: 'block' }}>
                1. Lĩnh vực / Ngành nghề hoạt động (Chọn nhiều) *
              </label>

              {/* Select Box Input (Bấm vào mở Dropdown, hiển thị Chips bên trong) */}
              <div
                onClick={() => setIsIndustryDropdownOpen(!isIndustryDropdownOpen)}
                style={{
                  minHeight: '46px',
                  padding: '6px 10px',
                  borderRadius: '8px',
                  border: isIndustryDropdownOpen ? '1.5px solid #3b82f6' : '1px solid #d1d5db',
                  background: '#fff',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '8px',
                  boxShadow: isIndustryDropdownOpen ? '0 0 0 3px rgba(59, 130, 246, 0.1)' : '0 1px 2px rgba(0,0,0,0.05)',
                  transition: 'all 0.15s ease'
                }}
              >
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', alignItems: 'center', flex: 1 }}>
                  {(company.industries || []).length === 0 ? (
                    <span style={{ color: '#94a3b8', fontSize: '0.925rem', paddingLeft: '4px' }}>-- Chọn một hoặc nhiều lĩnh vực --</span>
                  ) : (
                    <>
                      {(company.industries || []).slice(0, 3).map((ind) => (
                        <span
                          key={ind.categoryId}
                          onClick={(e) => e.stopPropagation()}
                          style={{
                            background: '#d1fae5',
                            color: '#047857',
                            border: '1px solid #a7f3d0',
                            padding: '3px 8px',
                            borderRadius: '16px',
                            fontSize: '0.825rem',
                            fontWeight: 600,
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '6px'
                          }}
                        >
                          {ind.categoryName}
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              const updatedInds = (company.industries || []).filter((ci) => ci.categoryId !== ind.categoryId);
                              const wasPrimary = ind.primary;
                              if (wasPrimary && updatedInds.length > 0) {
                                updatedInds[0].primary = true;
                                setCompany({ ...company, industries: updatedInds, industry: updatedInds[0].categoryName || '' });
                              } else if (updatedInds.length === 0) {
                                setCompany({ ...company, industries: [], industry: '' });
                              } else {
                                setCompany({ ...company, industries: updatedInds });
                              }
                            }}
                            style={{
                              background: 'transparent',
                              border: 'none',
                              color: '#047857',
                              cursor: 'pointer',
                              fontWeight: 'bold',
                              fontSize: '1rem',
                              lineHeight: 1,
                              padding: '0 2px'
                            }}
                            title="Xóa lĩnh vực này"
                          >
                            ×
                          </button>
                        </span>
                      ))}
                      {(company.industries || []).length > 3 && (
                        <span
                          title={(company.industries || []).slice(3).map(i => i.categoryName).join(', ')}
                          style={{
                            background: '#e2e8f0',
                            color: '#334155',
                            padding: '3px 8px',
                            borderRadius: '16px',
                            fontSize: '0.8rem',
                            fontWeight: 600,
                            cursor: 'help'
                          }}
                        >
                          +{(company.industries || []).length - 3}
                        </span>
                      )}
                    </>
                  )}
                </div>

                {/* Icons bên phải */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
                  {(company.industries || []).length > 0 && (
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setCompany({ ...company, industries: [], industry: '' });
                      }}
                      style={{
                        background: 'transparent',
                        border: 'none',
                        color: '#64748b',
                        cursor: 'pointer',
                        fontSize: '1.1rem',
                        padding: '0 4px'
                      }}
                      title="Xóa tất cả"
                    >
                      ×
                    </button>
                  )}
                  <span style={{ color: '#64748b', fontSize: '0.75rem', transform: isIndustryDropdownOpen ? 'rotate(180deg)' : 'rotate(0deg)', transition: 'transform 0.15s' }}>
                    ▼
                  </span>
                </div>
              </div>

              {/* Dropdown Options List */}
              {isIndustryDropdownOpen && (
                <div
                  style={{
                    position: 'absolute',
                    top: '100%',
                    left: 0,
                    right: 0,
                    marginTop: '6px',
                    background: '#fff',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    boxShadow: '0 10px 25px -5px rgba(0,0,0,0.15)',
                    zIndex: 100,
                    maxHeight: '260px',
                    overflowY: 'auto',
                    padding: '6px'
                  }}
                >
                  <div style={{ padding: '4px 6px', marginBottom: '4px', position: 'sticky', top: 0, background: '#fff', zIndex: 1 }}>
                    <input
                      type="text"
                      placeholder="🔍 Tìm nhanh lĩnh vực..."
                      value={industrySearchTerm}
                      onChange={(e) => setIndustrySearchTerm(e.target.value)}
                      onClick={(e) => e.stopPropagation()}
                      style={{
                        width: '100%',
                        padding: '6px 10px',
                        borderRadius: '6px',
                        border: '1px solid #e2e8f0',
                        fontSize: '0.85rem'
                      }}
                    />
                  </div>

                  {categories
                    .filter((c) => !c.parentId && (!industrySearchTerm || c.name.toLowerCase().includes(industrySearchTerm.toLowerCase())))
                    .map((cat) => {
                      const selectedInd = (company.industries || []).find((ci) => ci.categoryId === cat.id);
                      const isChecked = !!selectedInd;
                      return (
                        <div
                          key={cat.id}
                          onClick={(e) => {
                            e.stopPropagation();
                            let currentInds = [...(company.industries || [])];
                            if (!isChecked) {
                              const newIsPrimary = currentInds.length === 0;
                              currentInds.push({
                                categoryId: cat.id,
                                categoryName: cat.name,
                                primary: newIsPrimary
                              });
                              setCompany({
                                ...company,
                                industries: currentInds,
                                industry: newIsPrimary ? cat.name : (company.industry || cat.name)
                              });
                            } else {
                              const wasPrimary = selectedInd?.primary;
                              currentInds = currentInds.filter((ci) => ci.categoryId !== cat.id);
                              if (wasPrimary && currentInds.length > 0) {
                                currentInds[0].primary = true;
                                setCompany({ ...company, industries: currentInds, industry: currentInds[0].categoryName || '' });
                              } else if (currentInds.length === 0) {
                                setCompany({ ...company, industries: [], industry: '' });
                              } else {
                                setCompany({ ...company, industries: currentInds });
                              }
                            }
                          }}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '10px',
                            padding: '8px 10px',
                            borderRadius: '6px',
                            cursor: 'pointer',
                            background: isChecked ? '#eff6ff' : 'transparent',
                            color: isChecked ? '#1d4ed8' : '#334155',
                            fontWeight: isChecked ? 600 : 400,
                            transition: 'background 0.1s'
                          }}
                        >
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => {}}
                            style={{ width: '16px', height: '16px', cursor: 'pointer' }}
                          />
                          <span style={{ fontSize: '0.9rem', flex: 1 }}>{cat.name}</span>
                        </div>
                      );
                    })}
                </div>
              )}
            </div>

            {/* Ô 2: Chọn 1 ngành chính từ danh sách các ngành vừa chọn */}
            <div>
              <label style={{ fontWeight: 600, fontSize: '0.95rem', color: '#0f172a', marginBottom: '8px', display: 'block' }}>
                2. Lĩnh vực / Ngành chính (Chọn 1 duy nhất) *
              </label>
              <select
                value={
                  (company.industries || []).find((ci) => ci.primary)?.categoryId ||
                  ((company.industries || []).length === 1 ? (company.industries || [])[0].categoryId : '')
                }
                onChange={(e) => {
                  const selectedCatId = e.target.value;
                  if (!selectedCatId) return;
                  const updatedInds = (company.industries || []).map((ci) => ({
                    ...ci,
                    primary: ci.categoryId === selectedCatId
                  }));
                  const primaryItem = updatedInds.find((ci) => ci.primary);
                  setCompany({
                    ...company,
                    industries: updatedInds,
                    industry: primaryItem?.categoryName || company.industry
                  });
                }}
                style={{
                  width: '100%',
                  height: '46px',
                  padding: '10px 14px',
                  borderRadius: '8px',
                  border: (company.industries && company.industries.length > 0) ? '1.5px solid #3b82f6' : '1px solid #cbd5e1',
                  background: (company.industries && company.industries.length > 0) ? '#fff' : '#f1f5f9',
                  fontSize: '0.95rem',
                  fontWeight: 600,
                  color: '#1e293b',
                  cursor: (company.industries && company.industries.length > 0) ? 'pointer' : 'not-allowed',
                  boxShadow: '0 1px 2px rgba(0,0,0,0.05)'
                }}
                disabled={!(company.industries && company.industries.length > 0)}
              >
                <option value="">-- Chọn lĩnh vực chính trong các ngành đã chọn --</option>
                {(company.industries || []).map((ind) => (
                  <option key={ind.categoryId} value={ind.categoryId}>
                    {ind.categoryName} {ind.primary ? '(★ Đang là ngành chính)' : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

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
