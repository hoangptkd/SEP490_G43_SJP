import { FormEvent, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { employerService } from '../../services/employerService';
import { jobService } from '../../services/jobService';
import type { Company, Category } from '../../types/job';
import { FiCamera, FiCheckCircle, FiClock, FiAlertCircle, FiChevronDown, FiX, FiSearch } from '../../components/Icons';
import { TaxCodeLookupField } from '../../components/employer/TaxCodeLookupField';
import { isVietnamTaxCodeFormat } from '../../utils/taxCode';

function CompanyProfilePage() {
  const navigate = useNavigate();
  const [company, setCompany] = useState<Company | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploadingLogo, setUploadingLogo] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
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

  function validateForm() {
    const errors: Record<string, string> = {};
    if (!company?.name?.trim()) {
      errors.name = 'Vui lòng nhập tên công ty.';
    } else if (company.name.length > 100) {
      errors.name = 'Tên công ty không được vượt quá 100 ký tự.';
    }

    if (company?.website && !/^https?:\/\//i.test(company.website)) {
      errors.website = 'Website phải bắt đầu bằng http:// hoặc https://';
    }

    if (company?.companySize !== undefined && company.companySize !== null && company.companySize <= 0) {
      errors.companySize = 'Quy mô nhân sự phải lớn hơn 0.';
    }

    if (!company?.industries || company.industries.length === 0) {
      errors.industries = 'Vui lòng chọn ít nhất một lĩnh vực hoạt động.';
    }

    if (!company?.industry) {
      errors.industry = 'Vui lòng chọn lĩnh vực/ngành chính.';
    }

    if (company?.taxCode && !isVietnamTaxCodeFormat(company.taxCode)) {
      errors.taxCode = 'Mã số thuế phải gồm 10 chữ số hoặc 13 chữ số đối với đơn vị phụ thuộc.';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!company) return;

    if (!validateForm()) {
      setError('Vui lòng kiểm tra lại các trường thông tin không hợp lệ.');
      return;
    }

    setSaving(true);
    setMessage('');
    setError('');
    try {
      const updated = await employerService.updateCompanyProfile({ ...company, submitForReview: false });
      setCompany(updated);
      if (updated.verificationStatus?.toLowerCase() === 'pending') {
        setMessage('Lưu hồ sơ thành công. Hồ sơ đã được gửi và đang chờ admin duyệt.');
      } else {
        setMessage('Lưu hồ sơ công ty thành công.');
      }
    } catch (err: any) {
      if (err.response?.data?.message) {
        if (err.response.data.code === 'INVALID_TAX_CODE') {
          setFieldErrors((current) => ({ ...current, taxCode: err.response.data.message }));
        }
        setError(err.response.data.message);
      } else {
        setError('Có lỗi xảy ra khi lưu thông tin công ty.');
      }
    } finally {
      setSaving(false);
    }
  }

  if (loading) return (
    <div className="flex justify-center items-center h-64">
      <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-emerald-600"></div>
    </div>
  );
  
  if (!company) return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-8 text-center">
      <p className="text-red-500 font-medium">{error || 'Không tìm thấy thông tin công ty.'}</p>
    </div>
  );

  const verificationStatus = company.verificationStatus?.toLowerCase() || 'unverified';
  const isVerified = verificationStatus === 'verified' || verificationStatus === 'approved';
  
  const statusBadge = verificationStatus === 'verified' || verificationStatus === 'approved'
    ? { text: 'Đã xác thực', icon: FiCheckCircle, classes: 'bg-emerald-50 text-emerald-700 border-emerald-200' }
    : verificationStatus === 'pending'
    ? { text: 'Chờ kiểm duyệt', icon: FiClock, classes: 'bg-blue-50 text-blue-700 border-blue-200' }
    : verificationStatus === 'rejected'
    ? { text: 'Yêu cầu bổ sung', icon: FiAlertCircle, classes: 'bg-red-50 text-red-700 border-red-200' }
    : { text: 'Chưa xác thực', icon: FiAlertCircle, classes: 'bg-gray-50 text-gray-700 border-gray-200' };

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Banner */}
      <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-2xl p-8 text-white shadow-lg relative overflow-hidden">
        <div className="absolute top-0 right-0 w-64 h-64 bg-white opacity-5 rounded-full -translate-y-1/2 translate-x-1/3 blur-2xl"></div>
        <div className="absolute bottom-0 left-0 w-64 h-64 bg-emerald-500 opacity-10 rounded-full translate-y-1/3 -translate-x-1/3 blur-2xl"></div>
        
        <div className="relative z-10 flex flex-col md:flex-row items-center md:items-start justify-between gap-6">
          <div className="flex flex-col md:flex-row items-center md:items-start gap-6 text-center md:text-left">
            <div className="relative group">
              <div className="w-24 h-24 rounded-2xl bg-white border-2 border-white/20 flex items-center justify-center overflow-hidden shadow-xl shrink-0">
                {company.logoUrl ? (
                  <img src={company.logoUrl} alt={company.name} className="w-full h-full object-cover" />
                ) : (
                  <span className="text-3xl font-bold text-slate-800">
                    {company.name ? company.name.charAt(0).toUpperCase() : 'C'}
                  </span>
                )}
              </div>
              {!isVerified && (
                <label className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity rounded-2xl flex items-center justify-center cursor-pointer">
                  {uploadingLogo ? (
                    <div className="animate-spin rounded-full h-6 w-6 border-2 border-white border-t-transparent"></div>
                  ) : (
                    <FiCamera className="w-6 h-6 text-white" />
                  )}
                  <input type="file" accept="image/*" onChange={handleLogoChange} disabled={uploadingLogo} className="hidden" />
                </label>
              )}
            </div>
            
            <div>
              <h1 className="text-2xl font-bold mb-2">{company.name || 'Tên công ty'}</h1>
              <p className="text-slate-300 text-sm flex items-center justify-center md:justify-start gap-2">
                <span>{company.industry || 'Chưa cập nhật ngành nghề'}</span>
                <span>•</span>
                <span>{company.location || 'Chưa cập nhật địa điểm'}</span>
              </p>
            </div>
          </div>

          <div className="flex flex-col items-center md:items-end gap-3">
            <span className={`inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-sm font-medium border ${statusBadge.classes}`}>
              <statusBadge.icon className="w-4 h-4" />
              {statusBadge.text}
            </span>
            {!isVerified && (
              <label className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${uploadingLogo ? 'bg-white/10 text-white/50 cursor-wait' : 'bg-white/10 hover:bg-white/20 text-white cursor-pointer backdrop-blur-sm border border-white/10'}`}>
                <FiCamera className="w-4 h-4" />
                {uploadingLogo ? 'Đang cập nhật...' : 'Thay đổi logo'}
                <input type="file" accept="image/*" onChange={handleLogoChange} disabled={uploadingLogo} className="hidden" />
              </label>
            )}
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
        {isVerified && (
          <div className="bg-amber-50 border-b border-amber-100 p-4 px-6 flex items-start gap-3 text-amber-800">
            <FiAlertCircle className="w-5 h-5 mt-0.5 shrink-0" />
            <div className="text-sm">
              <strong className="font-semibold block mb-1">Hồ sơ công ty đã được duyệt.</strong>
              Để thay đổi thông tin công ty, vui lòng sang trang <button type="button" onClick={() => navigate('/employer/verification')} className="font-medium underline hover:text-amber-900 transition-colors">Xác thực pháp lý</button>.
            </div>
          </div>
        )}

        <div className="p-6 md:p-8 space-y-8">
          <fieldset disabled={isVerified} className="space-y-6 disabled:opacity-80">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2 md:col-span-2">
                <label className="block text-sm font-medium text-gray-700">Tên công ty <span className="text-red-500">*</span></label>
                <input
                  required
                  value={company.name}
                  onChange={(e) => setCompany({ ...company, name: e.target.value })}
                  placeholder="Tên chính thức của doanh nghiệp"
                  className={`w-full px-4 py-2.5 rounded-xl border focus:ring-2 outline-none transition-all disabled:bg-gray-50 disabled:text-gray-500 ${fieldErrors.name ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-gray-200 focus:border-emerald-500 focus:ring-emerald-200'}`}
                />
                {fieldErrors.name && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.name}</p>}
              </div>

              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">Website</label>
                <input
                  value={company.website || ''}
                  onChange={(e) => setCompany({ ...company, website: e.target.value })}
                  placeholder="https://example.com"
                  className={`w-full px-4 py-2.5 rounded-xl border focus:ring-2 outline-none transition-all disabled:bg-gray-50 disabled:text-gray-500 ${fieldErrors.website ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-gray-200 focus:border-emerald-500 focus:ring-emerald-200'}`}
                />
                {fieldErrors.website && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.website}</p>}
              </div>

              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">Quy mô nhân sự</label>
                <input
                  type="number"
                  min="1"
                  value={company.companySize === undefined || company.companySize === null ? '' : company.companySize}
                  onChange={(e) => setCompany({ ...company, companySize: e.target.value ? parseInt(e.target.value) : undefined })}
                  placeholder="Số lượng nhân viên"
                  className={`w-full px-4 py-2.5 rounded-xl border focus:ring-2 outline-none transition-all disabled:bg-gray-50 disabled:text-gray-500 ${fieldErrors.companySize ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-gray-200 focus:border-emerald-500 focus:ring-emerald-200'}`}
                />
                {fieldErrors.companySize && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.companySize}</p>}
              </div>
            </div>

            <div className="bg-slate-50 p-6 rounded-xl border border-slate-200 space-y-6">
              <h3 className="text-sm font-semibold text-slate-800 uppercase tracking-wider">Lĩnh vực hoạt động</h3>
              
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Lĩnh vực hoạt động (Multiple) */}
                <div ref={industryDropdownRef} className="relative space-y-2">
                  <label className="block text-sm font-medium text-gray-700">
                    1. Các ngành nghề hoạt động (Chọn nhiều) <span className="text-red-500">*</span>
                  </label>
                  
                  <div
                    onClick={() => !isVerified && setIsIndustryDropdownOpen(!isIndustryDropdownOpen)}
                    className={`min-h-[46px] p-2 rounded-xl border flex items-center justify-between gap-2 transition-all ${
                      isVerified ? 'bg-gray-50 border-gray-200 cursor-not-allowed' :
                      isIndustryDropdownOpen ? 'border-emerald-500 ring-2 ring-emerald-200 bg-white' : 
                      fieldErrors.industries ? 'border-red-500 bg-white' : 'border-gray-200 bg-white cursor-pointer hover:border-emerald-400'
                    }`}
                  >
                    <div className="flex flex-wrap gap-2 items-center flex-1">
                      {(company.industries || []).length === 0 ? (
                        <span className="text-gray-400 text-sm px-2">-- Chọn lĩnh vực --</span>
                      ) : (
                        <>
                          {(company.industries || []).slice(0, 3).map((ind) => (
                            <span key={ind.categoryId} className="inline-flex items-center gap-1 bg-emerald-50 text-emerald-700 border border-emerald-200 px-2.5 py-1 rounded-lg text-sm font-medium">
                              {ind.categoryName}
                              {!isVerified && (
                                <button
                                  type="button"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    const updatedInds = (company.industries || []).filter((ci) => ci.categoryId !== ind.categoryId);
                                    if (ind.primary && updatedInds.length > 0) {
                                      updatedInds[0].primary = true;
                                      setCompany({ ...company, industries: updatedInds, industry: updatedInds[0].categoryName || '' });
                                    } else if (updatedInds.length === 0) {
                                      setCompany({ ...company, industries: [], industry: '' });
                                    } else {
                                      setCompany({ ...company, industries: updatedInds });
                                    }
                                  }}
                                  className="text-emerald-500 hover:text-emerald-700 transition-colors p-0.5"
                                >
                                  <FiX className="w-3.5 h-3.5" />
                                </button>
                              )}
                            </span>
                          ))}
                          {(company.industries || []).length > 3 && (
                            <span className="inline-flex items-center bg-gray-100 text-gray-600 px-2.5 py-1 rounded-lg text-sm font-medium" title={(company.industries || []).slice(3).map(i => i.categoryName).join(', ')}>
                              +{(company.industries || []).length - 3}
                            </span>
                          )}
                        </>
                      )}
                    </div>
                    
                    <div className="flex items-center gap-1 shrink-0 px-1 text-gray-400">
                      {!isVerified && (company.industries || []).length > 0 && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setCompany({ ...company, industries: [], industry: '' });
                          }}
                          className="hover:text-red-500 transition-colors p-1"
                        >
                          <FiX className="w-4 h-4" />
                        </button>
                      )}
                      <FiChevronDown className={`w-4 h-4 transition-transform duration-200 ${isIndustryDropdownOpen ? 'rotate-180' : ''}`} />
                    </div>
                  </div>

                  {/* Dropdown List */}
                  {isIndustryDropdownOpen && !isVerified && (
                    <div className="absolute top-full left-0 right-0 mt-2 bg-white border border-gray-200 rounded-xl shadow-xl z-50 max-h-72 flex flex-col overflow-hidden">
                      <div className="p-3 border-b border-gray-100 bg-gray-50/50">
                        <div className="relative">
                          <FiSearch className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                          <input
                            type="text"
                            placeholder="Tìm lĩnh vực..."
                            value={industrySearchTerm}
                            onChange={(e) => setIndustrySearchTerm(e.target.value)}
                            onClick={(e) => e.stopPropagation()}
                            className="w-full pl-9 pr-4 py-2 rounded-lg border border-gray-200 focus:border-emerald-500 outline-none text-sm"
                          />
                        </div>
                      </div>
                      <div className="overflow-y-auto p-2">
                        {categories
                          .filter((c) => !c.parentId && (!industrySearchTerm || c.name.toLowerCase().includes(industrySearchTerm.toLowerCase())))
                          .map((cat) => {
                            const isChecked = !!(company.industries || []).find((ci) => ci.categoryId === cat.id);
                            return (
                              <div
                                key={cat.id}
                                className={`flex items-center gap-3 px-3 py-2.5 rounded-lg cursor-pointer transition-colors ${isChecked ? 'bg-emerald-50/50 text-emerald-800' : 'hover:bg-gray-50 text-gray-700'}`}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  e.preventDefault();
                                  let currentInds = [...(company.industries || [])];
                                  if (!isChecked) {
                                    const newIsPrimary = currentInds.length === 0;
                                    currentInds.push({ categoryId: cat.id, categoryName: cat.name, primary: newIsPrimary });
                                    setCompany({ ...company, industries: currentInds, industry: newIsPrimary ? cat.name : (company.industry || cat.name) });
                                  } else {
                                    const wasPrimary = currentInds.find(ci => ci.categoryId === cat.id)?.primary;
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
                              >
                                <input type="checkbox" checked={isChecked} readOnly className="w-4 h-4 rounded text-emerald-600 focus:ring-emerald-500 border-gray-300 pointer-events-none" />
                                <span className="text-sm font-medium">{cat.name}</span>
                              </div>
                            );
                          })}
                      </div>
                    </div>
                  )}
                  {fieldErrors.industries && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.industries}</p>}
                </div>

                {/* Ngành chính (Single) */}
                <div className="space-y-2">
                  <label className="block text-sm font-medium text-gray-700">
                    2. Ngành chính (Chọn 1) <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <select
                      value={
                        (company.industries || []).find((ci) => ci.primary)?.categoryId ||
                        ((company.industries || []).length === 1 ? (company.industries || [])[0].categoryId : '')
                      }
                      onChange={(e) => {
                        const selectedCatId = e.target.value;
                        if (!selectedCatId) return;
                        const updatedInds = (company.industries || []).map((ci) => ({ ...ci, primary: ci.categoryId === selectedCatId }));
                        const primaryItem = updatedInds.find((ci) => ci.primary);
                        setCompany({ ...company, industries: updatedInds, industry: primaryItem?.categoryName || company.industry });
                      }}
                      className={`w-full px-4 py-2.5 rounded-xl border outline-none transition-all appearance-none ${
                        isVerified || !(company.industries && company.industries.length > 0) 
                          ? 'bg-gray-50 border-gray-200 text-gray-500 cursor-not-allowed' 
                          : fieldErrors.industry 
                          ? 'bg-white border-red-500 focus:border-red-500 focus:ring-2 focus:ring-red-200 cursor-pointer' 
                          : 'bg-white border-gray-200 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200 cursor-pointer'
                      }`}
                      disabled={isVerified || !(company.industries && company.industries.length > 0)}
                    >
                      <option value="">-- Chọn ngành chính --</option>
                      {(company.industries || []).map((ind) => (
                        <option key={ind.categoryId} value={ind.categoryId}>
                          {ind.categoryName} {ind.primary ? '(★)' : ''}
                        </option>
                      ))}
                    </select>
                    <FiChevronDown className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
                  </div>
                  {fieldErrors.industry && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.industry}</p>}
                </div>
              </div>
            </div>

            <TaxCodeLookupField
              value={company.taxCode || ''}
              onChange={(taxCode) => {
                setCompany({ ...company, taxCode });
                setFieldErrors((current) => ({ ...current, taxCode: '' }));
              }}
              error={fieldErrors.taxCode}
              disabled={isVerified}
            />

            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">Mô tả / Giới thiệu công ty</label>
              <textarea
                value={company.description || ''}
                onChange={(e) => setCompany({ ...company, description: e.target.value })}
                placeholder="Giới thiệu về lịch sử, sứ mệnh, môi trường làm việc..."
                rows={5}
                className="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200 outline-none transition-all resize-y disabled:bg-gray-50 disabled:text-gray-500"
              />
            </div>
          </fieldset>
        </div>

        <div className="bg-gray-50 px-6 py-5 border-t border-gray-200 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="w-full md:w-auto">
            {message && <div className="text-emerald-700 text-sm font-medium flex items-center gap-2 bg-emerald-100/50 px-4 py-2 rounded-lg"><FiCheckCircle className="w-4 h-4" />{message}</div>}
            {error && <div className="text-red-600 text-sm font-medium flex items-center gap-2 bg-red-100/50 px-4 py-2 rounded-lg"><FiAlertCircle className="w-4 h-4" />{error}</div>}
          </div>
          
          <div className="flex w-full md:w-auto gap-3">
            {!isVerified && (
              <button
                type="submit"
                disabled={saving}
                className={`flex-1 md:flex-none px-6 py-2.5 rounded-xl text-sm font-semibold text-white transition-all shadow-sm ${
                  saving ? 'bg-emerald-400 cursor-wait' : 'bg-emerald-600 hover:bg-emerald-700 hover:shadow-md'
                }`}
              >
                {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
              </button>
            )}
            <button 
              type="button" 
              onClick={() => navigate('/employer/locations')}
              className={`flex-1 md:flex-none px-6 py-2.5 rounded-xl text-sm font-semibold transition-all shadow-sm ${
                isVerified 
                  ? 'bg-emerald-600 text-white hover:bg-emerald-700' 
                  : 'bg-white border border-gray-200 text-gray-700 hover:bg-gray-50'
              }`}
            >
              Cơ sở & Địa điểm →
            </button>
          </div>
        </div>
      </form>

      {company.locations && company.locations.length > 0 && (
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden mt-8">
          <div className="px-6 py-5 border-b border-gray-100 flex items-center justify-between">
            <h3 className="text-lg font-bold text-slate-800">Các chi nhánh & Văn phòng ({company.locations.length})</h3>
            <button onClick={() => navigate('/employer/locations')} className="text-sm font-semibold text-emerald-600 hover:text-emerald-700 transition-colors flex items-center gap-1">
              Quản lý chi nhánh <span aria-hidden="true">&rarr;</span>
            </button>
          </div>
          <div className="p-6 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {company.locations.map((loc) => (
              <div key={loc.id} className={`p-5 rounded-xl border transition-colors ${loc.headquarter ? 'bg-emerald-50/50 border-emerald-200' : 'bg-white border-gray-200 hover:border-emerald-300'}`}>
                <div className="flex items-center gap-2 mb-3">
                  <strong className="text-slate-800 font-semibold">{loc.branchName}</strong>
                  {loc.headquarter && <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wide bg-emerald-600 text-white">Trụ sở chính</span>}
                </div>
                <p className="text-sm text-slate-500 leading-relaxed">
                  {loc.address ? `${loc.address}, ` : ''}{loc.city || ''}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default CompanyProfilePage;
