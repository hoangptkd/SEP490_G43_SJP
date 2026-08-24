import { useEffect, useState, useRef } from 'react';
import { employerService } from '../../services/employerService';
import { jobService } from '../../services/jobService';
import { customConfirm } from '../../utils/dialog';
import type { Company, CompanyDocument, Category } from '../../types/job';
import { FiCheckCircle, FiClock, FiAlertCircle, FiUploadCloud, FiFileText, FiImage, FiDownload, FiTrash2, FiRefreshCw, FiExternalLink, FiChevronDown, FiX, FiSearch, FiCamera, FiBuilding } from '../../components/Icons';
import { TaxCodeLookupField } from '../../components/employer/TaxCodeLookupField';
import { isVietnamTaxCodeFormat } from '../../utils/taxCode';

function CompanyVerificationPage() {
  const [company, setCompany] = useState<Company | null>(null);
  const [documents, setDocuments] = useState<CompanyDocument[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [uploadingLogo, setUploadingLogo] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [replacingId, setReplacingId] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState('');
  const [suggestedName, setSuggestedName] = useState('');
  const [success, setSuccess] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  
  const [isIndustryDropdownOpen, setIsIndustryDropdownOpen] = useState(false);
  const [industrySearchTerm, setIndustrySearchTerm] = useState('');
  const [noWebsite, setNoWebsite] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const replaceInputRefs = useRef<Record<string, HTMLInputElement | null>>({});
  const industryDropdownRef = useRef<HTMLDivElement>(null);

  const verificationStatus = company?.verificationStatus?.toLowerCase() || 'unverified';
  const isVerified = verificationStatus === 'verified' || verificationStatus === 'approved';

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
      setNoWebsite(!compData.website);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Không thể tải thông tin xác thực pháp lý');
    } finally {
      setLoading(false);
    }
  }

  async function handleLogoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setError('Vui lòng chọn tệp hình ảnh (PNG, JPG, JPEG)');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      setError('Dung lượng ảnh tối đa 5MB');
      return;
    }

    setUploadingLogo(true);
    setError('');
    setSuccess('');

    try {
      const updated = await employerService.uploadLogo(file);
      setCompany(updated);
      setSuccess('Đã cập nhật ảnh đại diện/logo công ty thành công (thay đổi logo không cần chờ admin duyệt)!');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Có lỗi xảy ra khi tải lên logo.');
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

    if (!noWebsite) {
      const trimmedWebsite = company?.website?.trim() || '';
      if (!trimmedWebsite) {
        errors.website = 'Vui lòng nhập địa chỉ website.';
      } else if (trimmedWebsite.length > 255) {
        errors.website = 'Website không được vượt quá 255 ký tự.';
      } else if (!/^https?:\/\//i.test(trimmedWebsite)) {
        errors.website = 'Website phải bắt đầu bằng http:// hoặc https://';
      } else {
        try {
          new URL(trimmedWebsite);
        } catch (e) {
          errors.website = 'Định dạng URL không hợp lệ.';
        }
      }
    }

    if (company?.companySize !== undefined && company.companySize !== null) {
      if (!Number.isInteger(company.companySize)) {
        errors.companySize = 'Quy mô nhân sự phải là số nguyên.';
      } else if (company.companySize <= 0) {
        errors.companySize = 'Quy mô nhân sự phải lớn hơn 0.';
      } else if (company.companySize > 1000000) {
        errors.companySize = 'Quy mô nhân sự không vượt quá 1.000.000.';
      }
    }

    const trimmedDescription = company?.description?.trim() || '';
    if (!trimmedDescription) {
      errors.description = 'Vui lòng nhập mô tả / giới thiệu công ty.';
    } else if (trimmedDescription.length < 500) {
      errors.description = 'Mô tả / Giới thiệu công ty không được ít hơn 500 ký tự.';
    } else if (trimmedDescription.length > 5000) {
      errors.description = 'Mô tả / Giới thiệu công ty không được vượt quá 5000 ký tự.';
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

    if (company?.contactPhone) {
      if (!/^(0|\+84)[3|5|7|8|9][0-9]{8}$/.test(company.contactPhone.replace(/\s+/g, ''))) {
        errors.contactPhone = 'Số điện thoại không hợp lệ.';
      }
    }

    if (company?.contactEmail) {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(company.contactEmail)) {
        errors.contactEmail = 'Email không hợp lệ.';
      }
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(e: React.FormEvent, submitForReview: boolean) {
    if (e) e.preventDefault();
    if (!company) return;

    if (!validateForm()) {
      setError('Vui lòng kiểm tra lại các trường thông tin không hợp lệ.');
      return;
    }

    setSaving(true);
    setSuccess('');
    setError('');
    try {
      const payload = { ...company, submitForReview };
      if (noWebsite) {
        payload.website = '';
      } else if (payload.website) {
        payload.website = payload.website.trim();
      }
      const updated = await employerService.updateCompanyProfile(payload);
      setCompany(updated);
      
      const newStatus = updated.verificationStatus?.toLowerCase() || 'unverified';
      if (submitForReview || (isVerified && newStatus === 'pending')) {
        setSuccess('Hồ sơ đã được gửi và đang chờ admin duyệt lại do có thay đổi pháp lý.');
      } else {
        setSuccess('Lưu thông tin công ty thành công.');
      }
      await loadData();
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
    if (!(await customConfirm('Bạn có chắc chắn muốn xóa tài liệu này?'))) return;
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
          classes: 'bg-emerald-50 text-emerald-700 border-emerald-200',
          icon: FiCheckCircle,
          desc: 'Tuyệt vời! Doanh nghiệp của bạn đã hoàn tất kiểm duyệt pháp lý thành công. Huy hiệu xác thực được hiển thị công khai trên tất cả tin tuyển dụng.'
        };
      case 'PENDING':
      case 'PENDING_REVIEW':
        return {
          text: 'Chờ kiểm duyệt hồ sơ',
          classes: 'bg-blue-50 text-blue-700 border-blue-200',
          icon: FiClock,
          desc: 'Tài liệu pháp lý đang được Bộ phận kiểm duyệt rà soát. Quá trình kiểm duyệt thường hoàn tất trong vòng 24 giờ làm việc.'
        };
      case 'REJECTED':
        return {
          text: 'Yêu cầu cập nhật tài liệu',
          classes: 'bg-red-50 text-red-700 border-red-200',
          icon: FiAlertCircle,
          desc: 'Một hoặc nhiều tài liệu bị từ chối. Vui lòng dùng nút Cập nhật lại trên từng file bị từ chối rồi gửi lại để admin duyệt.'
        };
      default:
        return {
          text: 'Chưa xác thực',
          classes: 'bg-slate-100 text-slate-700 border-slate-200',
          icon: FiAlertCircle,
          desc: 'Vui lòng điền đủ thông tin và tải lên Giấy phép đăng ký kinh doanh hoặc Mã số thuế để tiến hành xác thực.'
        };
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-emerald-600"></div>
      </div>
    );
  }

  const badgeInfo = getStatusBadge(company?.verificationStatus);

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Header Banner */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 md:p-8 shadow-sm flex flex-col md:flex-row items-center justify-between gap-6">
        <div className="text-center md:text-left">
          <h1 className="text-2xl font-bold text-gray-900 mb-1">Xác thực pháp lý doanh nghiệp</h1>
          <p className="text-gray-500 text-sm">
            Quản lý thông tin công ty và tài liệu pháp lý định danh tổ chức
          </p>
        </div>
        <button
          onClick={loadData}
          className="inline-flex items-center gap-2 bg-gray-50 hover:bg-gray-100 text-gray-700 px-4 py-2 rounded-xl text-sm font-medium transition-colors border border-gray-200 shadow-sm"
        >
          <FiRefreshCw className="w-4 h-4" /> Làm mới dữ liệu
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border-l-4 border-red-500 text-red-700 px-5 py-4 rounded-xl flex items-center gap-3 shadow-sm">
          <FiAlertCircle className="w-5 h-5 shrink-0" /> {error}
        </div>
      )}

      {success && (
        <div className="bg-emerald-50 border-l-4 border-emerald-500 text-emerald-700 px-5 py-4 rounded-xl flex items-center gap-3 shadow-sm">
          <FiCheckCircle className="w-5 h-5 shrink-0" /> {success}
        </div>
      )}

      {/* Status Banner */}
      <div className={`p-6 rounded-2xl border ${badgeInfo.classes} flex flex-col md:flex-row items-start md:items-center gap-4`}>
        <div className="bg-white/50 p-3 rounded-full shrink-0">
          <badgeInfo.icon className="w-8 h-8" />
        </div>
        <div>
          <div className="flex items-center gap-3 mb-1 flex-wrap">
            <span className="font-bold text-slate-900">Trạng thái hồ sơ:</span>
            <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-bold border bg-white ${badgeInfo.classes}`}>
              {badgeInfo.text}
            </span>
          </div>
          <p className="text-sm font-medium opacity-90 leading-relaxed max-w-3xl">{badgeInfo.desc}</p>
        </div>
      </div>

      <div className="bg-amber-50 border border-amber-200 text-amber-800 p-5 rounded-2xl flex items-start gap-4">
        <FiAlertCircle className="w-6 h-6 shrink-0 mt-0.5 text-amber-500" />
        <div className="text-sm">
          <strong className="block mb-1 font-bold text-amber-900">Lưu ý về việc cập nhật thông tin:</strong>
          Việc thay đổi các trường quan trọng (Tên công ty, Lĩnh vực hoạt động, Mã số thuế) sẽ yêu cầu công ty phải được duyệt lại. Các trường khác (Quy mô, Website, Mô tả) có thể lưu bình thường mà không ảnh hưởng tới trạng thái xác thực.
        </div>
      </div>

      {/* Company Form */}
      {company && (
        <form onSubmit={(e) => handleSubmit(e, false)} className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
          <div className="p-6 md:p-8 space-y-6">
            {/* Company Logo Section */}
            <div className="flex flex-col sm:flex-row items-center sm:items-start gap-5 p-5 bg-slate-50 border border-slate-200 rounded-2xl">
              <div className="w-20 h-20 rounded-2xl bg-white border border-slate-200 flex items-center justify-center overflow-hidden shrink-0 shadow-sm relative group">
                {company.logoUrl ? (
                  <img src={company.logoUrl} alt={company.name} className="w-full h-full object-cover" />
                ) : (
                  <FiBuilding className="w-8 h-8 text-slate-400" />
                )}
                <label className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center cursor-pointer">
                  <FiCamera className="w-5 h-5 text-white" />
                  <input type="file" accept="image/*" onChange={handleLogoChange} disabled={uploadingLogo} className="hidden" />
                </label>
              </div>
              <div className="flex-1 text-center sm:text-left space-y-1">
                <div className="flex items-center gap-2 justify-center sm:justify-start">
                  <h4 className="font-bold text-slate-800 text-base">Ảnh đại diện / Logo công ty</h4>
                  <span className="bg-emerald-100 text-emerald-800 text-[11px] font-semibold px-2.5 py-0.5 rounded-full">
                    Đổi không cần duyệt
                  </span>
                </div>
                <p className="text-xs text-slate-500 leading-relaxed">
                  Hiển thị trên danh sách tuyển dụng, trang chi tiết công ty và tin tuyển dụng. Bạn có thể thay đổi bất cứ lúc nào mà không ảnh hưởng tới trạng thái xác thực.
                </p>
                <div className="pt-2">
                  <label className={`inline-flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all shadow-sm ${uploadingLogo ? 'bg-slate-200 text-slate-500 cursor-wait' : 'bg-emerald-600 hover:bg-emerald-700 text-white cursor-pointer'}`}>
                    <FiCamera className="w-4 h-4" />
                    {uploadingLogo ? 'Đang cập nhật logo...' : (company.logoUrl ? 'Thay đổi logo công ty' : 'Tải lên logo công ty')}
                    <input type="file" accept="image/*" onChange={handleLogoChange} disabled={uploadingLogo} className="hidden" />
                  </label>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2 md:col-span-1 relative">
                <label className="block text-sm font-medium text-gray-700">Tên công ty <span className="text-red-500">*</span></label>
                <input
                  required
                  value={company.name}
                  onChange={(e) => setCompany({ ...company, name: e.target.value })}
                  placeholder={suggestedName || "Tên chính thức của doanh nghiệp"}
                  className={`w-full px-4 py-2.5 rounded-xl border focus:ring-2 outline-none transition-all disabled:bg-gray-50 disabled:text-gray-500 ${fieldErrors.name ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-gray-200 focus:border-emerald-500 focus:ring-emerald-200'}`}
                />
                {suggestedName && company.name !== suggestedName && (
                  <p className="text-xs text-emerald-600 mt-1">
                    Gợi ý: <button type="button" className="font-semibold hover:underline" onClick={() => setCompany({ ...company, name: suggestedName })}>{suggestedName}</button>
                  </p>
                )}
                {fieldErrors.name && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.name}</p>}
              </div>

              <TaxCodeLookupField
                value={company.taxCode || ''}
                onChange={(taxCode) => {
                  setCompany({ ...company, taxCode });
                  setFieldErrors((current) => ({ ...current, taxCode: '' }));
                }}
                onLookupSuccess={(companyName) => {
                  setSuggestedName(companyName);
                }}
                error={fieldErrors.taxCode}
              />

              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">Website {!noWebsite && <span className="text-red-500">*</span>}</label>
                <input
                  disabled={noWebsite}
                  value={noWebsite ? '' : (company.website || '')}
                  onChange={(e) => setCompany({ ...company, website: e.target.value })}
                  placeholder="https://example.com"
                  className={`w-full px-4 py-2.5 rounded-xl border focus:ring-2 outline-none transition-all disabled:bg-gray-50 disabled:text-gray-500 ${fieldErrors.website ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-gray-200 focus:border-emerald-500 focus:ring-emerald-200'}`}
                />
                <label className="flex items-center gap-2 mt-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={noWebsite}
                    onChange={(e) => {
                      setNoWebsite(e.target.checked);
                      if (e.target.checked) {
                        setFieldErrors(prev => ({ ...prev, website: '' }));
                      }
                    }}
                    className="w-4 h-4 rounded text-emerald-600 focus:ring-emerald-500 border-gray-300"
                  />
                  <span className="text-sm text-gray-600">Tôi không có website</span>
                </label>
                {fieldErrors.website && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.website}</p>}
              </div>

              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">Quy mô nhân sự</label>
                <input
                  type="number"
                  min="1"
                  value={company.companySize === undefined || company.companySize === null ? '' : company.companySize}
                  onChange={(e) => {
                    const val = e.target.value;
                    setCompany({ ...company, companySize: val ? Number(val) : undefined });
                  }}
                  onKeyDown={(e) => {
                    if (['-', '+', 'e', 'E', '.'].includes(e.key)) {
                      e.preventDefault();
                    }
                  }}
                  placeholder="Số lượng nhân viên"
                  className={`w-full px-4 py-2.5 rounded-xl border focus:ring-2 outline-none transition-all disabled:bg-gray-50 disabled:text-gray-500 ${fieldErrors.companySize ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-gray-200 focus:border-emerald-500 focus:ring-emerald-200'}`}
                />
                {fieldErrors.companySize && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.companySize}</p>}
              </div>
            </div>

            <div className="bg-emerald-50/50 p-6 rounded-xl border border-emerald-100 space-y-6">
              <h3 className="text-sm font-semibold text-emerald-800 uppercase tracking-wider">Thông tin liên hệ (Có thể cập nhật bất kỳ lúc nào)</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="space-y-2">
                  <label className="block text-sm font-medium text-gray-700">Số điện thoại liên hệ</label>
                  <input
                    type="text"
                    value={company.contactPhone || ''}
                    onChange={(e) => {
                      const cleaned = e.target.value.replace(/[^\d+]/g, '');
                      setCompany({ ...company, contactPhone: cleaned });
                      setFieldErrors(prev => ({ ...prev, contactPhone: '' }));
                    }}
                    placeholder="0912345678"
                    className={`w-full px-4 py-2.5 rounded-xl border focus:ring-2 outline-none transition-all ${fieldErrors.contactPhone ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-emerald-200 focus:border-emerald-500 focus:ring-emerald-200 bg-white'}`}
                  />
                  {fieldErrors.contactPhone && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.contactPhone}</p>}
                </div>

                <div className="space-y-2">
                  <label className="block text-sm font-medium text-gray-700">Email liên hệ</label>
                  <input
                    type="email"
                    value={company.contactEmail || ''}
                    onChange={(e) => {
                      setCompany({ ...company, contactEmail: e.target.value });
                      setFieldErrors(prev => ({ ...prev, contactEmail: '' }));
                    }}
                    placeholder="contact@company.com"
                    className={`w-full px-4 py-2.5 rounded-xl border focus:ring-2 outline-none transition-all ${fieldErrors.contactEmail ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-emerald-200 focus:border-emerald-500 focus:ring-emerald-200 bg-white'}`}
                  />
                  {fieldErrors.contactEmail && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.contactEmail}</p>}
                </div>
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
                    onClick={() => setIsIndustryDropdownOpen(!isIndustryDropdownOpen)}
                    className={`min-h-[46px] p-2 rounded-xl border flex items-center justify-between gap-2 transition-all ${
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
                      {(company.industries || []).length > 0 && (
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
                  {isIndustryDropdownOpen && (
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
                        !(company.industries && company.industries.length > 0) 
                          ? 'bg-gray-50 border-gray-200 text-gray-500 cursor-not-allowed' 
                          : fieldErrors.industry 
                          ? 'bg-white border-red-500 focus:border-red-500 focus:ring-2 focus:ring-red-200 cursor-pointer' 
                          : 'bg-white border-gray-200 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200 cursor-pointer'
                      }`}
                      disabled={!(company.industries && company.industries.length > 0)}
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



            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">Mô tả / Giới thiệu công ty <span className="text-red-500">*</span></label>
              <textarea
                value={company.description || ''}
                onChange={(e) => setCompany({ ...company, description: e.target.value })}
                placeholder="Giới thiệu về lịch sử, sứ mệnh, môi trường làm việc..."
                rows={5}
                className={`w-full px-4 py-3 rounded-xl border focus:ring-2 outline-none transition-all resize-y ${fieldErrors.description ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-gray-200 focus:border-emerald-500 focus:ring-emerald-200'}`}
              />
              {fieldErrors.description && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.description}</p>}
            </div>
          </div>

          <div className="bg-gray-50 px-6 py-5 border-t border-gray-200 flex flex-col sm:flex-row gap-3">
            <button 
              type="submit" 
              disabled={saving} 
              className={`flex-1 sm:flex-none px-6 py-2.5 rounded-xl text-sm font-semibold transition-all shadow-sm ${
                isVerified 
                  ? 'bg-emerald-600 text-white hover:bg-emerald-700'
                  : 'bg-white border border-gray-200 text-gray-700 hover:bg-gray-50'
              }`}
            >
              {saving ? 'Đang xử lý...' : (isVerified ? 'Lưu thay đổi' : 'Lưu (Không gửi duyệt)')}
            </button>
            {!isVerified && (
              <button 
                type="button" 
                disabled={saving} 
                onClick={(e) => handleSubmit(e, true)} 
                className={`flex-1 sm:flex-none px-6 py-2.5 rounded-xl text-sm font-semibold text-white transition-all shadow-sm ${
                  saving ? 'bg-emerald-400 cursor-wait' : 'bg-emerald-600 hover:bg-emerald-700 hover:shadow-md'
                }`}
              >
                {saving ? 'Đang xử lý...' : 'Lưu & Gửi duyệt'}
              </button>
            )}
          </div>
        </form>
      )}

      {/* Upload Box */}
      <div className="border-2 border-dashed border-emerald-200 bg-emerald-50/50 rounded-2xl p-8 text-center transition-colors hover:border-emerald-300">
        <FiUploadCloud className="w-12 h-12 text-emerald-500 mx-auto mb-4" />
        <h3 className="text-lg font-bold text-slate-800 mb-2">
          Tải lên tài liệu xác thực (PDF, JPG, PNG)
        </h3>
        <p className="text-slate-500 text-sm mb-6 max-w-lg mx-auto">
          Hỗ trợ tệp tin tối đa 10MB. Tài liệu được mã hóa và bảo mật tuyệt đối trên hệ thống cloud.
        </p>

        <form onSubmit={handleUpload} className="inline-flex flex-col items-center gap-4">
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
            className="hidden"
            id="doc-upload-input"
          />
          <label
            htmlFor="doc-upload-input"
            className="inline-flex items-center gap-2 bg-white border border-gray-200 px-6 py-2.5 rounded-xl text-sm font-semibold text-slate-700 cursor-pointer hover:bg-gray-50 transition-all shadow-sm"
          >
            {selectedFile ? (
              <><FiCheckCircle className="text-emerald-500" /> Đã chọn: {selectedFile.name} ({(selectedFile.size / 1024 / 1024).toFixed(2)} MB)</>
            ) : (
              <><FiUploadCloud className="text-gray-400" /> + Chọn tệp từ máy tính</>
            )}
          </label>

          {selectedFile && (
            <button
              type="submit"
              disabled={uploading}
              className={`px-8 py-2.5 rounded-xl text-sm font-semibold text-white transition-all shadow-md ${
                uploading ? 'bg-emerald-400 cursor-wait' : 'bg-emerald-600 hover:bg-emerald-700'
              }`}
            >
              {uploading ? 'Đang tải lên hệ thống...' : 'Tải lên & Nộp kiểm duyệt'}
            </button>
          )}
        </form>
      </div>

      {/* Document List */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
        <div className="px-6 py-5 border-b border-gray-100">
          <h3 className="text-lg font-bold text-slate-800">
            Danh sách tài liệu đã gửi ({documents.length})
          </h3>
        </div>

        <div className="p-6">
          {documents.length === 0 ? (
            <div className="text-center py-12">
              <FiFileText className="w-12 h-12 text-slate-300 mx-auto mb-3" />
              <p className="text-slate-500">
                Chưa có tài liệu nào được tải lên. Vui lòng chọn và tải lên tài liệu pháp lý ở khung phía trên.
              </p>
            </div>
          ) : (
            <div className="grid gap-4">
              {documents.map((doc) => {
                const docStatus = doc.status?.toLowerCase() || 'pending';
                const statusStyle = docStatus === 'approved' || docStatus === 'verified'
                  ? { bg: 'bg-emerald-100 text-emerald-700', label: 'Đã hợp lệ' }
                  : docStatus === 'rejected'
                  ? { bg: 'bg-red-100 text-red-700', label: 'Yêu cầu cập nhật' }
                  : { bg: 'bg-blue-100 text-blue-700', label: 'Chờ kiểm duyệt' };

                return (
                  <div key={doc.id} className="flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 rounded-2xl border border-gray-200 hover:border-emerald-300 transition-colors shadow-sm bg-white">
                    <div className="flex items-start md:items-center gap-4 flex-1">
                      <div className={`w-12 h-12 rounded-xl flex items-center justify-center shrink-0 ${doc.fileType === 'pdf' ? 'bg-red-50 text-red-500 border border-red-100' : 'bg-blue-50 text-blue-500 border border-blue-100'}`}>
                        {doc.fileType === 'pdf' ? <FiFileText className="w-6 h-6" /> : <FiImage className="w-6 h-6" />}
                      </div>
                      
                      <div className="flex-1">
                        <div className="font-bold text-slate-800 mb-1">
                          {doc.fileName}
                        </div>
                        <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500">
                          <span className="flex items-center gap-1"><FiClock /> {new Date(doc.uploadedAt).toLocaleString('vi-VN')}</span>
                          <span className={`px-2.5 py-0.5 rounded-full font-bold uppercase tracking-wide text-[10px] ${statusStyle.bg}`}>
                            {statusStyle.label}
                          </span>
                        </div>
                        {doc.rejectReason && (
                          <div className="mt-3 bg-red-50 text-red-700 p-3 rounded-lg text-sm border border-red-100">
                            <strong className="font-semibold">Phản hồi:</strong> {doc.rejectReason}
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      <a
                        href={doc.fileType === 'pdf' || doc.fileName?.toLowerCase().endsWith('.pdf') ? `https://docs.google.com/gview?url=${encodeURIComponent(doc.fileUrl)}` : doc.fileUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold bg-slate-50 border border-slate-200 text-slate-700 hover:bg-slate-100 transition-colors"
                      >
                        <FiExternalLink className="w-4 h-4" /> Xem
                      </a>

                      <a
                        href={doc.fileUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        download={doc.fileName}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold bg-white border border-gray-200 text-slate-600 hover:bg-gray-50 transition-colors"
                      >
                        <FiDownload className="w-4 h-4" /> Tải về
                      </a>

                      {docStatus === 'pending' && (
                        <button
                          onClick={() => handleDelete(doc.id)}
                          disabled={deletingId === doc.id}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold bg-white border border-red-200 text-red-600 hover:bg-red-50 transition-colors"
                        >
                          <FiTrash2 className="w-4 h-4" /> {deletingId === doc.id ? 'Đang xóa...' : 'Xóa'}
                        </button>
                      )}

                      {docStatus === 'rejected' && (
                        <>
                          <input
                            type="file"
                            accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                            className="hidden"
                            ref={(el) => { replaceInputRefs.current[doc.id] = el; }}
                            onChange={(e) => {
                              const file = e.target.files?.[0];
                              if (file) handleReplace(doc.id, file);
                            }}
                          />
                          <button
                            type="button"
                            onClick={() => replaceInputRefs.current[doc.id]?.click()}
                            disabled={replacingId === doc.id}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold bg-emerald-600 text-white hover:bg-emerald-700 transition-colors shadow-sm"
                          >
                            <FiUploadCloud className="w-4 h-4" /> {replacingId === doc.id ? 'Đang cập nhật...' : 'Cập nhật lại'}
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
      </div>
    </div>
  );
}

export default CompanyVerificationPage;
