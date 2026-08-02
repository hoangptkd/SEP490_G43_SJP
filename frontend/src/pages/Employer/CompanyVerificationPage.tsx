import { useEffect, useState, useRef } from 'react';
import { employerService } from '../../services/employerService';
import { jobService } from '../../services/jobService';
import type { Company, CompanyDocument, Category } from '../../types/job';

function CompanyVerificationPage() {
  const [company, setCompany] = useState<Company | null>(null);
  const [documents, setDocuments] = useState<CompanyDocument[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [replacingId, setReplacingId] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  
  const [isIndustryDropdownOpen, setIsIndustryDropdownOpen] = useState(false);
  const [industrySearchTerm, setIndustrySearchTerm] = useState('');

  const fileInputRef = useRef<HTMLInputElement>(null);
  const replaceInputRefs = useRef<Record<string, HTMLInputElement | null>>({});
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
    loadData();
  }, []);

  async function loadData() {
    setLoading(true);
    setError('');
    try {
      const [compData, docsData, categoriesData] = await Promise.all([
        employerService.getCompanyProfile(),
        employerService.getDocuments(),
        jobService.getCategories().catch(() => [])
      ]);
      let inds = compData.industries || [];
      if (inds.length === 0 && compData.industry && categoriesData.length > 0) {
        const matchedCat = categoriesData.find(c => !c.parentId && c.name === compData.industry) || categoriesData.find(c => c.name === compData.industry);
        if (matchedCat) {
          inds = [{ categoryId: matchedCat.id, categoryName: matchedCat.name, primary: true }];
        }
      }
      setCompany({ ...compData, industries: inds });
      setDocuments(docsData);
      setCategories(categoriesData);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Không thể tải thông tin xác thực pháp lý');
    } finally {
      setLoading(false);
    }
  }

  async function handleSubmit(e: React.FormEvent, submitForReview: boolean) {
    if (e) e.preventDefault();
    if (!company) return;
    setSaving(true);
    setSuccess('');
    setError('');
    try {
      const updated = await employerService.updateCompanyProfile({ ...company, submitForReview });
      setCompany(updated);
      setSuccess(submitForReview ? 'Hồ sơ đã được gửi và đang chờ admin duyệt.' : 'Lưu thông tin công ty thành công.');
      await loadData();
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

  function validateFile(file: File) {
    const validTypes = ['application/pdf', 'image/jpeg', 'image/png', 'image/jpg'];
    if (!validTypes.includes(file.type) && !file.name.toLowerCase().endsWith('.pdf')) {
      setError('Chỉ chấp nhận định dạng file PDF hoặc hình ảnh (JPG, PNG)');
      return false;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError('Dung lượng file tối đa là 10MB');
      return false;
    }
    return true;
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    if (e.target.files && e.target.files.length > 0) {
      const file = e.target.files[0];
      if (!validateFile(file)) return;
      setSelectedFile(file);
      setError('');
    }
  }

  async function handleUpload(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedFile) {
      setError('Vui lòng chọn file để tải lên');
      return;
    }
    setUploading(true);
    setError('');
    setSuccess('');
    try {
      await employerService.uploadDocument(selectedFile);
      setSuccess('Tải lên tài liệu thành công! Trạng thái hồ sơ đã chuyển sang Chờ duyệt.');
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      await loadData();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Lỗi khi tải lên tài liệu. Vui lòng thử lại.');
    } finally {
      setUploading(false);
    }
  }

  async function handleDelete(id: string) {
    if (!window.confirm('Bạn có chắc chắn muốn xóa tài liệu này?')) return;
    setDeletingId(id);
    setError('');
    try {
      await employerService.deleteDocument(id);
      setSuccess('Đã xóa tài liệu xác thực.');
      await loadData();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Không thể xóa tài liệu này');
    } finally {
      setDeletingId(null);
    }
  }

  async function handleReplace(docId: string, file: File) {
    if (!validateFile(file)) return;
    setReplacingId(docId);
    setError('');
    setSuccess('');
    try {
      await employerService.replaceDocument(docId, file);
      setSuccess('Đã cập nhật tài liệu. Hồ sơ chuyển sang Chờ duyệt để admin kiểm tra lại.');
      await loadData();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Không thể cập nhật tài liệu. Vui lòng thử lại.');
    } finally {
      setReplacingId(null);
      const input = replaceInputRefs.current[docId];
      if (input) input.value = '';
    }
  }

  function getStatusBadge(status?: string) {
    const s = status?.toUpperCase() || 'UNVERIFIED';
    switch (s) {
      case 'VERIFIED':
      case 'APPROVED':
        return {
          text: 'Đã xác thực pháp lý',
          color: '#047857',
          bg: '#ecfdf5',
          border: '#a7f3d0',
          dot: '#10b981',
          desc: 'Tuyệt vời! Doanh nghiệp của bạn đã hoàn tất kiểm duyệt pháp lý thành công. Huy hiệu xác thực được hiển thị công khai trên tất cả tin tuyển dụng.'
        };
      case 'PENDING':
      case 'PENDING_REVIEW':
        return {
          text: 'Chờ kiểm duyệt hồ sơ',
          color: '#1d4ed8',
          bg: '#eff6ff',
          border: '#bfdbfe',
          dot: '#3b82f6',
          desc: 'Tài liệu pháp lý đang được Bộ phận kiểm duyệt rà soát. Quá trình kiểm duyệt thường hoàn tất trong vòng 24 giờ làm việc.'
        };
      case 'REJECTED':
        return {
          text: 'Yêu cầu cập nhật tài liệu',
          color: '#b91c1c',
          bg: '#fef2f2',
          border: '#fecaca',
          dot: '#ef4444',
          desc: 'Một hoặc nhiều tài liệu bị từ chối. Vui lòng dùng nút Cập nhật lại trên từng file bị từ chối rồi gửi lại để admin duyệt.'
        };
      default:
        return {
          text: 'Chưa xác thực',
          color: '#475569',
          bg: '#f8fafc',
          border: '#cbd5e1',
          dot: '#94a3b8',
          desc: 'Vui lòng điền đủ thông tin và tải lên Giấy phép đăng ký kinh doanh hoặc Mã số thuế để tiến hành xác thực.'
        };
    }
  }

  const badgeInfo = getStatusBadge(company?.verificationStatus);

  if (loading) {
    return (
      <div className="card" style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
        <p style={{ margin: 0, fontSize: '1rem' }}>Đang tải thông tin xác thực doanh nghiệp...</p>
      </div>
    );
  }

  return (
    <div className="card" style={{ padding: '32px', borderRadius: '10px', border: '1px solid #e2e8f0', background: '#fff', boxShadow: '0 1px 3px rgba(0,0,0,0.02)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', flexWrap: 'wrap', gap: '16px', borderBottom: '1px solid #e2e8f0', paddingBottom: '16px' }}>
        <div>
          <h1 style={{ margin: '0 0 8px 0', fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>Xác thực pháp lý doanh nghiệp</h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.95rem' }}>
            Quản lý thông tin công ty và tài liệu pháp lý định danh tổ chức
          </p>
        </div>
        <button
          onClick={loadData}
          style={{
            background: '#f8fafc',
            border: '1px solid #cbd5e1',
            color: '#334155',
            padding: '8px 16px',
            borderRadius: '6px',
            cursor: 'pointer',
            fontSize: '0.875rem',
            fontWeight: 600,
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            transition: 'all 0.2s'
          }}
        >
          Làm mới dữ liệu
        </button>
      </div>

      {error && (
        <div style={{ background: '#fef2f2', color: '#991b1b', padding: '14px 18px', borderRadius: '8px', marginBottom: '20px', border: '1px solid #fecaca', borderLeft: '4px solid #dc2626', fontSize: '0.9rem' }}>
          {error}
        </div>
      )}

      {success && (
        <div style={{ background: '#ecfdf5', color: '#047857', padding: '14px 18px', borderRadius: '8px', marginBottom: '20px', border: '1px solid #a7f3d0', borderLeft: '4px solid #10b981', fontSize: '0.9rem' }}>
          {success}
        </div>
      )}

      {/* Status Banner */}
      <div style={{
        background: badgeInfo.bg,
        color: badgeInfo.color,
        border: `1px solid ${badgeInfo.border}`,
        padding: '22px 24px',
        borderRadius: '8px',
        marginBottom: '28px',
        display: 'flex',
        alignItems: 'flex-start',
        gap: '16px'
      }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px', flexWrap: 'wrap' }}>
            <span style={{ fontWeight: 700, fontSize: '0.95rem', color: '#0f172a' }}>Trạng thái hồ sơ:</span>
            <span style={{
              background: '#ffffff',
              color: badgeInfo.color,
              border: `1px solid ${badgeInfo.border}`,
              padding: '4px 14px',
              borderRadius: '20px',
              fontWeight: 600,
              fontSize: '0.85rem',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px'
            }}>
              <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: badgeInfo.dot }}></span>
              {badgeInfo.text}
            </span>
          </div>
          <p style={{ margin: 0, fontSize: '0.9rem', lineHeight: 1.6, opacity: 0.9 }}>{badgeInfo.desc}</p>
        </div>
      </div>

      {/* Warning Banner */}
      <div style={{ background: '#fffbeb', color: '#b45309', padding: '16px', borderRadius: '8px', border: '1px solid #fde68a', marginBottom: '28px', fontSize: '0.9rem', display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
        <span style={{ fontSize: '1.2rem' }}>⚠️</span>
        <div>
          <strong style={{ display: 'block', marginBottom: '4px' }}>Lưu ý về việc cập nhật thông tin:</strong>
          Việc thay đổi các trường quan trọng (Tên công ty, Lĩnh vực hoạt động, Mã số thuế) sẽ yêu cầu công ty phải được duyệt lại. Các trường khác (Quy mô, Website, Mô tả) có thể lưu bình thường mà không ảnh hưởng tới trạng thái xác thực.
        </div>
      </div>

      {/* Company Form */}
      {company && (
        <form onSubmit={(e) => handleSubmit(e, false)} style={{ marginBottom: '48px' }}>
          <div className="form-grid two">
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
                <div ref={industryDropdownRef} style={{ position: 'relative' }}>
                  <label style={{ fontWeight: 600, fontSize: '0.95rem', color: '#0f172a', marginBottom: '8px', display: 'block' }}>
                    1. Lĩnh vực / Ngành nghề hoạt động (Chọn nhiều) *
                  </label>
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
                                style={{ background: 'transparent', border: 'none', color: '#047857', cursor: 'pointer', fontWeight: 'bold', fontSize: '1rem', lineHeight: 1, padding: '0 2px' }}
                                title="Xóa lĩnh vực này"
                              >
                                ×
                              </button>
                            </span>
                          ))}
                          {(company.industries || []).length > 3 && (
                            <span style={{ background: '#e2e8f0', color: '#334155', padding: '3px 8px', borderRadius: '16px', fontSize: '0.8rem', fontWeight: 600, cursor: 'help' }}>
                              +{(company.industries || []).length - 3}
                            </span>
                          )}
                        </>
                      )}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
                      {(company.industries || []).length > 0 && (
                        <button type="button" onClick={(e) => { e.stopPropagation(); setCompany({ ...company, industries: [], industry: '' }); }} style={{ background: 'transparent', border: 'none', color: '#64748b', cursor: 'pointer', fontSize: '1.1rem', padding: '0 4px' }} title="Xóa tất cả">
                          ×
                        </button>
                      )}
                      <span style={{ color: '#64748b', fontSize: '0.75rem', transform: isIndustryDropdownOpen ? 'rotate(180deg)' : 'rotate(0deg)', transition: 'transform 0.15s' }}>▼</span>
                    </div>
                  </div>
                  {isIndustryDropdownOpen && (
                    <div style={{ position: 'absolute', top: '100%', left: 0, right: 0, marginTop: '6px', background: '#fff', border: '1px solid #cbd5e1', borderRadius: '8px', boxShadow: '0 10px 25px -5px rgba(0,0,0,0.15)', zIndex: 100, maxHeight: '260px', overflowY: 'auto', padding: '6px' }}>
                      <div style={{ padding: '4px 6px', marginBottom: '4px', position: 'sticky', top: 0, background: '#fff', zIndex: 1 }}>
                        <input
                          type="text"
                          placeholder="🔍 Tìm nhanh lĩnh vực..."
                          value={industrySearchTerm}
                          onChange={(e) => setIndustrySearchTerm(e.target.value)}
                          onClick={(e) => e.stopPropagation()}
                          style={{ width: '100%', padding: '6px 10px', borderRadius: '6px', border: '1px solid #e2e8f0', fontSize: '0.85rem' }}
                        />
                      </div>
                      {categories.filter((c) => !c.parentId && (!industrySearchTerm || c.name.toLowerCase().includes(industrySearchTerm.toLowerCase()))).map((cat) => {
                          const selectedInd = (company.industries || []).find((ci) => ci.categoryId === cat.id);
                          const isChecked = !!selectedInd;
                          return (
                            <div key={cat.id} onClick={(e) => {
                                e.stopPropagation();
                                let currentInds = [...(company.industries || [])];
                                if (!isChecked) {
                                  const newIsPrimary = currentInds.length === 0;
                                  currentInds.push({ categoryId: cat.id, categoryName: cat.name, primary: newIsPrimary });
                                  setCompany({ ...company, industries: currentInds, industry: newIsPrimary ? cat.name : (company.industry || cat.name) });
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
                              style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '8px 10px', borderRadius: '6px', cursor: 'pointer', background: isChecked ? '#eff6ff' : 'transparent', color: isChecked ? '#1d4ed8' : '#334155', fontWeight: isChecked ? 600 : 400, transition: 'background 0.1s' }}
                            >
                              <input type="checkbox" checked={isChecked} onChange={() => {}} style={{ width: '16px', height: '16px', cursor: 'pointer' }} />
                              <span style={{ fontSize: '0.9rem', flex: 1 }}>{cat.name}</span>
                            </div>
                          );
                        })}
                    </div>
                  )}
                </div>

                <div>
                  <label style={{ fontWeight: 600, fontSize: '0.95rem', color: '#0f172a', marginBottom: '8px', display: 'block' }}>
                    2. Lĩnh vực / Ngành chính (Chọn 1 duy nhất) *
                  </label>
                  <select
                    value={(company.industries || []).find((ci) => ci.primary)?.categoryId || ((company.industries || []).length === 1 ? (company.industries || [])[0].categoryId : '')}
                    onChange={(e) => {
                      const selectedCatId = e.target.value;
                      if (!selectedCatId) return;
                      const updatedInds = (company.industries || []).map((ci) => ({ ...ci, primary: ci.categoryId === selectedCatId }));
                      const primaryItem = updatedInds.find((ci) => ci.primary);
                      setCompany({ ...company, industries: updatedInds, industry: primaryItem?.categoryName || company.industry });
                    }}
                    style={{ width: '100%', height: '46px', padding: '10px 14px', borderRadius: '8px', border: (company.industries && company.industries.length > 0) ? '1.5px solid #3b82f6' : '1px solid #cbd5e1', background: (company.industries && company.industries.length > 0) ? '#fff' : '#f1f5f9', fontSize: '0.95rem', fontWeight: 600, color: '#1e293b', cursor: (company.industries && company.industries.length > 0) ? 'pointer' : 'not-allowed', boxShadow: '0 1px 2px rgba(0,0,0,0.05)' }}
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
          </div>

          <div style={{ marginTop: '20px', display: 'flex', gap: '16px', borderTop: '1px solid #e2e8f0', paddingTop: '24px' }}>
            <button type="submit" disabled={saving} style={{ background: '#f8fafc', color: '#334155', border: '1px solid #cbd5e1', padding: '10px 24px', borderRadius: '6px', fontWeight: 600, fontSize: '0.95rem', cursor: saving ? 'wait' : 'pointer' }}>
              {saving ? 'Đang xử lý...' : 'Lưu (Không gửi duyệt)'}
            </button>
            <button type="button" disabled={saving} onClick={(e) => handleSubmit(e, true)} style={{ background: '#2563eb', color: '#fff', border: 'none', padding: '10px 24px', borderRadius: '6px', fontWeight: 600, fontSize: '0.95rem', cursor: saving ? 'wait' : 'pointer', boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)' }}>
              {saving ? 'Đang xử lý...' : 'Lưu & Gửi duyệt'}
            </button>
          </div>
        </form>
      )}

      {/* Upload Box */}
      <div style={{
        border: '2px dashed #cbd5e1',
        borderRadius: '10px',
        padding: '36px 24px',
        textAlign: 'center',
        background: '#f8fafc',
        marginBottom: '32px',
        transition: 'border-color 0.2s'
      }}>
        <h3 style={{ margin: '0 0 8px 0', fontSize: '1.1rem', color: '#0f172a', fontWeight: 600 }}>
          Tải lên tài liệu xác thực (PDF, JPG, PNG)
        </h3>
        <p style={{ margin: '0 0 20px 0', color: '#64748b', fontSize: '0.875rem' }}>
          Hỗ trợ tệp tin tối đa 10MB. Tài liệu được mã hóa và bảo mật tuyệt đối trên hệ thống cloud.
        </p>

        <form onSubmit={handleUpload} style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center', gap: '14px' }}>
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
            style={{ display: 'none' }}
            id="doc-upload-input"
          />
          <label
            htmlFor="doc-upload-input"
            style={{
              background: '#ffffff',
              border: '1px solid #cbd5e1',
              padding: '10px 22px',
              borderRadius: '6px',
              cursor: 'pointer',
              fontWeight: 500,
              color: '#334155',
              fontSize: '0.9rem',
              boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
              display: 'inline-block',
              transition: 'all 0.2s'
            }}
          >
            {selectedFile ? `Đã chọn: ${selectedFile.name} (${(selectedFile.size / 1024 / 1024).toFixed(2)} MB)` : '+ Chọn tệp từ máy tính'}
          </label>

          {selectedFile && (
            <button
              type="submit"
              disabled={uploading}
              style={{
                background: '#2563eb',
                color: '#fff',
                border: 'none',
                padding: '10px 26px',
                borderRadius: '6px',
                cursor: uploading ? 'not-allowed' : 'pointer',
                fontWeight: 600,
                fontSize: '0.9rem',
                boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)',
                transition: 'background-color 0.2s'
              }}
            >
              {uploading ? 'Đang tải lên hệ thống...' : 'Tải lên & Nộp kiểm duyệt'}
            </button>
          )}
        </form>
      </div>

      {/* Document List */}
      <h3 style={{ fontSize: '1.2rem', margin: '0 0 16px 0', color: '#0f172a', fontWeight: 700, borderBottom: '1px solid #e2e8f0', paddingBottom: '12px' }}>
        Danh sách tài liệu đã gửi ({documents.length})
      </h3>

      {documents.length === 0 ? (
        <p style={{ textAlign: 'center', padding: '36px 0', color: '#64748b', margin: 0, fontSize: '0.95rem' }}>
          Chưa có tài liệu nào được tải lên. Vui lòng chọn và tải lên tài liệu pháp lý ở khung phía trên.
        </p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {documents.map((doc) => {
            const docStatus = doc.status?.toLowerCase() || 'pending';
            const statusStyle = docStatus === 'approved' || docStatus === 'verified'
              ? { bg: '#ecfdf5', color: '#047857', border: '#a7f3d0', label: 'Đã hợp lệ' }
              : docStatus === 'rejected'
              ? { bg: '#fef2f2', color: '#b91c1c', border: '#fecaca', label: 'Yêu cầu cập nhật' }
              : { bg: '#eff6ff', color: '#1d4ed8', border: '#bfdbfe', label: 'Chờ kiểm duyệt' };

            return (
              <div
                key={doc.id}
                style={{
                  border: '1px solid #e2e8f0',
                  borderRadius: '8px',
                  padding: '18px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: '#ffffff',
                  boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
                  flexWrap: 'wrap',
                  gap: '16px'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flex: '1 1 300px' }}>
                  <div style={{
                    width: '48px',
                    height: '48px',
                    borderRadius: '8px',
                    background: doc.fileType === 'pdf' ? '#fef2f2' : '#eff6ff',
                    color: doc.fileType === 'pdf' ? '#dc2626' : '#2563eb',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '0.85rem',
                    fontWeight: 700,
                    border: doc.fileType === 'pdf' ? '1px solid #fecaca' : '1px solid #bfdbfe',
                    flexShrink: 0
                  }}>
                    {doc.fileType === 'pdf' ? 'PDF' : 'IMG'}
                  </div>
                  <div>
                    <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem', marginBottom: '6px' }}>
                      {doc.fileName}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#64748b', display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
                      <span>Thời gian: {new Date(doc.uploadedAt).toLocaleString('vi-VN')}</span>
                      <span style={{
                        background: statusStyle.bg,
                        color: statusStyle.color,
                        border: `1px solid ${statusStyle.border}`,
                        padding: '2px 10px',
                        borderRadius: '12px',
                        fontWeight: 600,
                        fontSize: '0.75rem'
                      }}>
                        {statusStyle.label}
                      </span>
                    </div>
                    {doc.rejectReason && (
                      <div style={{ color: '#b91c1c', fontSize: '0.85rem', marginTop: '8px', background: '#fef2f2', border: '1px solid #fecaca', padding: '8px 12px', borderRadius: '6px', lineHeight: 1.4 }}>
                        <strong>Phản hồi:</strong> {doc.rejectReason}
                      </div>
                    )}
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                  <a
                    href={doc.fileType === 'pdf' || doc.fileName?.toLowerCase().endsWith('.pdf')
                      ? `https://docs.google.com/gview?url=${encodeURIComponent(doc.fileUrl)}`
                      : doc.fileUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{
                      background: '#f8fafc',
                      color: '#334155',
                      border: '1px solid #cbd5e1',
                      padding: '8px 14px',
                      borderRadius: '6px',
                      textDecoration: 'none',
                      fontSize: '0.85rem',
                      fontWeight: 600,
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                      transition: 'all 0.2s'
                    }}
                  >
                    Xem chi tiết
                  </a>

                  <a
                    href={doc.fileUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    download={doc.fileName}
                    style={{
                      background: '#ffffff',
                      color: '#475569',
                      border: '1px solid #cbd5e1',
                      padding: '8px 14px',
                      borderRadius: '6px',
                      textDecoration: 'none',
                      fontSize: '0.85rem',
                      fontWeight: 500,
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                      transition: 'all 0.2s'
                    }}
                  >
                    Tải về
                  </a>

                  {docStatus === 'pending' && (
                    <button
                      onClick={() => handleDelete(doc.id)}
                      disabled={deletingId === doc.id}
                      style={{
                        background: '#ffffff',
                        color: '#ef4444',
                        border: '1px solid #fecaca',
                        padding: '8px 14px',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        fontSize: '0.85rem',
                        fontWeight: 500,
                        transition: 'all 0.2s'
                      }}
                    >
                      {deletingId === doc.id ? 'Đang xử lý...' : 'Xóa'}
                    </button>
                  )}

                  {docStatus === 'rejected' && (
                    <>
                      <input
                        type="file"
                        accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                        style={{ display: 'none' }}
                        ref={(el) => {
                          replaceInputRefs.current[doc.id] = el;
                        }}
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (file) handleReplace(doc.id, file);
                        }}
                      />
                      <button
                        type="button"
                        onClick={() => replaceInputRefs.current[doc.id]?.click()}
                        disabled={replacingId === doc.id}
                        style={{
                          background: '#2563eb',
                          color: '#fff',
                          border: 'none',
                          padding: '8px 14px',
                          borderRadius: '6px',
                          cursor: replacingId === doc.id ? 'not-allowed' : 'pointer',
                          fontSize: '0.85rem',
                          fontWeight: 600,
                          boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)',
                        }}
                      >
                        {replacingId === doc.id ? 'Đang cập nhật...' : 'Cập nhật lại'}
                      </button>
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default CompanyVerificationPage;
