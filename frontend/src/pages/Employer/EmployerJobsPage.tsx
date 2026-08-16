import { FormEvent, useEffect, useState, useRef } from 'react';
import { employerService } from '../../services/employerService';
import type { Company, CompanyLocation, Job } from '../../types/job';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { billingService, UserSubscription } from '../../services/billingService';
import PlanLimitAlert from '../../components/PlanLimitAlert';
import { parseApiError } from '../../utils/planLimits';
import { customAlert, customConfirm, customPrompt } from '../../utils/dialog';
import VietnamAddressPicker from '../../components/location/VietnamAddressPicker';
import { IconLock, IconRobot } from '../../components/icons/PortalNavIcons';

const PRESET_WORKING_TIMES = [
  'Thứ 2 - Thứ 6 (08:00 - 17:30)',
  'Thứ 2 - Thứ 6 (08:30 - 18:00)',
  'Thứ 2 - Thứ 6 (09:00 - 18:00)',
  'Thứ 2 - Thứ 6 & Sáng Thứ 7 (08:00 - 17:30)',
  'Thứ 2 - Thứ 6 & Sáng Thứ 7 (08:30 - 18:00)',
  'Thứ 2 - Thứ 7 (08:00 - 17:00)',
  'Thứ 2 - Thứ 7 (08:00 - 17:30)',
  'Thứ 2 - Thứ 7 (08:30 - 18:00)',
  'Thời gian linh hoạt (Flexible working hours)',
  'Làm việc theo ca (Shift work)',
  'Thỏa thuận / Theo dự án',
];

function EmployerJobsPage() {
  const [company, setCompany] = useState<Company | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const [jobs, setJobs] = useState<Job[]>([]);
  const [allJobsForCount, setAllJobsForCount] = useState<Job[]>([]);
  const [locations, setLocations] = useState<CompanyLocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [planLimitReached, setPlanLimitReached] = useState(false);
  const [subscription, setSubscription] = useState<UserSubscription | null>(null);

  const submitTargetRef = useRef<string | undefined>(undefined);

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const jobsPerPage = 10;
  const [totalPages, setTotalPages] = useState(1);
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'PENDING_REVIEW' | 'PUBLISHED' | 'CLOSED' | 'EXPIRED' | 'DRAFT' | 'AWAITING_COMPANY' | 'ARCHIVED'>('ALL');
  const [viewingJob, setViewingJob] = useState<Job | null>(null);

  useEffect(() => {
    const handler = setTimeout(() => {
      setSearchTerm(searchInput);
    }, 500);
    return () => clearTimeout(handler);
  }, [searchInput]);

  useEffect(() => {
    setCurrentPage(1);
  }, [statusFilter, searchTerm]);
  const [skillsInput, setSkillsInput] = useState('');
  const [reqsInput, setReqsInput] = useState('');
  const [hasApplications, setHasApplications] = useState(false);
  const [showAiConfig, setShowAiConfig] = useState(false);
  const [formOpenedFromParams, setFormOpenedFromParams] = useState(false);
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    benefits: '',
    vacancies: 1,
    workingTime: 'Thứ 2 - Thứ 6 (08:00 - 17:30)',
    salaryType: 'range',
    salaryMin: 10000000,
    salaryMax: 20000000,
    jobType: 'full_time',
    workMode: 'onsite',
    experienceLevel: 'fresher',
    deadline: '',
    location: '',
    companyLocationId: '',
    status: 'published',
    rankingConfig: {
      enabled: false,
      template: 'default',
      weights: { skills: 40, experience: 30, projects: 10, education: 10, certificates: 10 },
      enabled_criteria: ['skills', 'experience', 'projects', 'education', 'certificates'],
      mandatory: { skills: [], certificates: [], min_experience_years: null, education_level: null },
    } as any,
  });

  useEffect(() => {
    loadData();
  }, [currentPage, statusFilter, searchTerm]);

  useEffect(() => {
    if (locations.length > 0 && searchParams.get('action') === 'new' && !formOpenedFromParams) {
      setFormOpenedFromParams(true);
      handleOpenAdd();
      searchParams.delete('action');
      setSearchParams(searchParams, { replace: true });
    }
  }, [searchParams, locations, formOpenedFromParams]);

  async function loadData() {
    setLoading(true);
    setError('');
    try {
      const [compData, jobsData, locsData, subData, allJobsData] = await Promise.all([
        employerService.getCompanyProfile(),
        employerService.getJobs({
          page: currentPage,
          size: jobsPerPage,
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          search: searchTerm || undefined
        }).catch(() => ({ items: [], totalPages: 1 }) as import('../../types/candidateDomain').PageResult<Job>),
        employerService.getLocations().catch(() => []),
        billingService.getMySubscription().catch(() => null),
        employerService.getJobs({
          page: 1,
          size: 1000,
          search: searchTerm || undefined
        }).catch(() => ({ items: [] }) as any)
      ]);
      setCompany(compData);
      setJobs(jobsData.items || []);
      setAllJobsForCount(allJobsData.items || []);
      setTotalPages(jobsData.totalPages || 1);
      setLocations(locsData);
      setSubscription(subData);
    } catch (err: any) {
      setError('Không thể tải thông tin tuyển dụng.');
    } finally {
      setLoading(false);
    }
  }

  function handleOpenAdd() {
    setEditingId(null);
    setSkillsInput('');
    setReqsInput('');
    setHasApplications(false);
    setShowAiConfig(false);
    const defaultLoc = locations.find((l) => l.headquarter) || locations[0];
    const defaultDate = new Date();
    defaultDate.setDate(defaultDate.getDate() + 30);
    const deadlineStr = defaultDate.toISOString().split('T')[0];

    setFormData({
      title: '',
      description: 'Bảo hiểm y tế, BHXH theo quy định pháp luật\nThưởng lương tháng 13, thưởng hiệu quả\nDu lịch hàng năm, khám sức khỏe định kỳ',
      benefits: 'Bảo hiểm y tế, BHXH theo quy định pháp luật\nThưởng lương tháng 13, thưởng hiệu quả\nDu lịch hàng năm, khám sức khỏe định kỳ',
      vacancies: 1,
      workingTime: 'Thứ 2 - Thứ 6 (08:00 - 17:30)',
      salaryType: 'range',
      salaryMin: 10000000,
      salaryMax: 20000000,
      jobType: 'full_time',
      workMode: 'onsite',
      experienceLevel: 'fresher',
      deadline: deadlineStr,
      location: defaultLoc
        ? [defaultLoc.branchName, defaultLoc.address, defaultLoc.district, defaultLoc.city].filter(Boolean).join(', ')
        : '',
      companyLocationId: defaultLoc ? defaultLoc.id : '',
      status: 'draft',
      rankingConfig: {
        template: 'default',
        weights: { skills: 40, experience: 30, projects: 10, education: 10, certificates: 10 },
        enabled_criteria: ['skills', 'experience', 'projects', 'education', 'certificates'],
        mandatory: { skills: [], certificates: [], min_experience_years: null, education_level: null },
      },
    });
    submitTargetRef.current = 'draft';
    setShowForm(true);
    setMessage('');
    setError('');
  }

  function handleOpenEdit(job: Job) {
    setEditingId(job.id);
    setSkillsInput((job.skills || []).join(', '));
    setReqsInput((job.requirements || []).join('\n'));
    setHasApplications(job.applicationsCount ? job.applicationsCount > 0 : false);
    setShowAiConfig(false);
    setEditingId(job.id);
    setShowForm(true);
    let deadlineStr = '';
    if (job.deadline) {
      deadlineStr = job.deadline.split('T')[0];
    }

    setFormData({
      title: job.title || '',
      description: job.description || '',
      benefits: job.benefits || '',
      vacancies: job.vacancies || 1,
      workingTime: job.workingTime || 'Thứ 2 - Thứ 6 (08:00 - 17:30)',
      salaryType: job.salaryType || (job.salaryMin && job.salaryMax ? 'range' : 'negotiable'),
      salaryMin: job.salaryMin || 0,
      salaryMax: job.salaryMax || 0,
      jobType: job.jobType || 'full_time',
      workMode: job.workMode || 'onsite',
      experienceLevel: job.experienceLevel || 'fresher',
      deadline: deadlineStr,
      location: job.location || '',
      companyLocationId: job.companyLocationId || (job.companyLocation?.id || ''),
      status: job.status?.toLowerCase() === 'rejected' || job.status?.toLowerCase() === 'awaiting_company'
        ? 'pending_review'
        : (job.status?.toLowerCase() || 'draft'),
      rankingConfig: job.rankingConfig || {
        template: 'default',
        weights: { skills: 40, experience: 30, projects: 10, education: 10, certificates: 10 },
        enabled_criteria: ['skills', 'experience', 'projects', 'education', 'certificates'],
        mandatory: { skills: [], certificates: [], min_experience_years: null, education_level: null },
      },
    });
    submitTargetRef.current =
      job.status?.toLowerCase() === 'rejected' || job.status?.toLowerCase() === 'awaiting_company'
        ? 'pending_review'
        : (job.status?.toLowerCase() || 'draft');
    setShowForm(true);
    setMessage('');
    setError('');
  }

  async function handleDelete(id: string, title: string) {
    if (!(await customConfirm(`Bạn có chắc chắn muốn xóa tin tuyển dụng "${title}" không?`))) return;
    try {
      await employerService.deleteJob(id);
      setJobs(jobs.filter((j) => j.id !== id));
      setMessage('Xóa tin tuyển dụng thành công.');
    } catch (err: any) {
      await customAlert('Không thể xóa tin tuyển dụng này.');
    }
  }

  async function handleSubmitForReview(id: string, title: string) {
    if (!(await customConfirm(`Bạn có chắc chắn muốn gửi duyệt tin tuyển dụng "${title}" cho Admin không?`))) return;
    try {
      const updated = await employerService.submitJobForReview(id);
      setJobs(jobs.map((j) => (j.id === id ? updated : j)));
      const isAutoPublished = updated.status?.toUpperCase() === 'PUBLISHED' || updated.status?.toUpperCase() === 'ACTIVE';
      setMessage(
        isAutoPublished
          ? `Đã đăng tin "${title}" thành công! Do công ty đã có tin được duyệt trước đó nên tin mới được xuất bản ngay không cần chờ Admin.`
          : `Đã gửi duyệt tin "${title}" thành công. Vui lòng chờ Admin kiểm duyệt.`
      );
    } catch (err: any) {
      await customAlert(err?.response?.data?.message || 'Không thể gửi duyệt tin tuyển dụng này.');
    }
  }

  async function handleCloseJob(id: string, title: string) {
    if (!(await customConfirm(`Bạn có chắc chắn muốn ĐÓNG tin tuyển dụng "${title}" (ngừng nhận đơn ứng tuyển) không? Các ứng viên đã nộp đơn sẽ nhận được thông báo.`))) return;
    try {
      const updated = await employerService.closeJob(id);
      setJobs(jobs.map((j) => (j.id === id ? updated : j)));
      setMessage(`Đã đóng tin tuyển dụng "${title}" thành công.`);
    } catch (err: any) {
      await customAlert(err?.response?.data?.message || 'Không thể đóng tin tuyển dụng này.');
    }
  }

  async function handleReopenJob(job: Job) {
    if (!(await customConfirm(`Bạn có muốn MỞ LẠI tin tuyển dụng "${job.title}" để tiếp tục nhận ứng viên không?`))) return;
    let newDeadline: string | undefined = undefined;
    if (job.deadline) {
      const isExpired = new Date(job.deadline).getTime() < new Date().setHours(0, 0, 0, 0);
      if (isExpired) {
        const input = await customPrompt('Tin tuyển dụng này đã hết hạn. Vui lòng nhập hạn nộp hồ sơ mới (YYYY-MM-DD):', new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]);
        if (!input) {
          await customAlert('Bạn phải cập nhật hạn nộp hồ sơ mới để mở lại tin!');
          return;
        }
        newDeadline = input;
      }
    }
    try {
      const updated = await employerService.reopenJob(job.id, newDeadline);
      setJobs(jobs.map((j) => (j.id === job.id ? updated : j)));
      setMessage(`Đã mở lại tin tuyển dụng "${job.title}" thành công.`);
    } catch (err: any) {
      const msg = err?.response?.data?.message || 'Không thể mở lại tin tuyển dụng này.';
      if (err?.response?.data?.errorCode === 'PLAN_LIMIT_REACHED' || err?.response?.status === 402 || msg.includes('nâng cấp gói') || msg.includes('đạt giới hạn')) {
        if (await customConfirm(`${msg}\n\nBạn có muốn đi đến trang Nâng cấp gói dịch vụ không?`)) {
          window.location.href = '/employer/subscription/plans';
        }
      } else {
        await customAlert(msg);
      }
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!company) return;
    setSaving(true);
    setMessage('');
    setError('');
    setPlanLimitReached(false);

    // Validation
    const errors: Record<string, string> = {};
    const titleTrimmed = formData.title?.trim() || '';
    if (!titleTrimmed || titleTrimmed.length < 6 || titleTrimmed.length > 50) {
      errors.title = 'Tên vị trí tuyển dụng phải có từ 6 đến 50 ký tự.';
    } else if (titleTrimmed.charAt(0) !== titleTrimmed.charAt(0).toUpperCase()) {
      errors.title = 'Chữ cái đầu tiên của vị trí tuyển dụng phải được viết hoa.';
    }
    const vacanciesNum = Number(formData.vacancies);
    if (!formData.vacancies || isNaN(vacanciesNum)) {
      errors.vacancies = 'Vui lòng nhập số lượng tuyển hợp lệ.';
    } else if (!Number.isInteger(vacanciesNum)) {
      errors.vacancies = 'Số lượng tuyển phải là số nguyên.';
    } else if (vacanciesNum < 1) {
      errors.vacancies = 'Số lượng tuyển phải lớn hơn hoặc bằng 1.';
    }
    if (formData.salaryType === 'range') {
      const sMin = Number(formData.salaryMin);
      const sMax = Number(formData.salaryMax);
      if (!formData.salaryMin || isNaN(sMin) || !Number.isInteger(sMin) || sMin <= 0) {
        errors.salaryMin = 'Mức lương tối thiểu phải là số nguyên lớn hơn 0.';
      } else if (sMin > 10000000000) {
        errors.salaryMin = 'Mức lương quá lớn, vui lòng kiểm tra lại.';
      }
      if (!formData.salaryMax || isNaN(sMax) || !Number.isInteger(sMax) || sMax <= 0) {
        errors.salaryMax = 'Mức lương tối đa phải là số nguyên lớn hơn 0.';
      } else if (sMax > 10000000000) {
        errors.salaryMax = 'Mức lương quá lớn, vui lòng kiểm tra lại.';
      } else if (!errors.salaryMin && sMax <= sMin) {
        errors.salaryMax = 'Mức lương tối đa phải lớn hơn mức lương tối thiểu.';
      }
    } else if (formData.salaryType === 'fixed') {
      const sMax = Number(formData.salaryMax);
      if (!formData.salaryMax || isNaN(sMax) || !Number.isInteger(sMax) || sMax <= 0) {
        errors.salaryMax = 'Mức lương cố định phải là số nguyên lớn hơn 0.';
      } else if (sMax > 10000000000) {
        errors.salaryMax = 'Mức lương quá lớn, vui lòng kiểm tra lại.';
      }
    }
    
    if (formData.deadline) {
      const selectedDate = new Date(formData.deadline);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (selectedDate < today) {
        errors.deadline = 'Hạn nộp hồ sơ không được ở trong quá khứ.';
      }
    } else {
      errors.deadline = 'Hạn nộp hồ sơ là bắt buộc.';
    }

    if (!formData.location || formData.location.trim() === '') {
      errors.location = 'Địa điểm hiển thị trên tin tuyển dụng là bắt buộc.';
    }

    const skillsArray = skillsInput
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);

    const reqsArray = reqsInput
      .split('\n')
      .map((r) => r.trim())
      .filter((r) => r.length > 0);

    if (skillsArray.length === 0) {
      errors.skills = 'Yêu cầu ít nhất 1 kỹ năng (ngăn cách bởi dấu phẩy).';
    }
    if (reqsArray.length === 0 && skillsArray.length === 0) {
      errors.requirements = 'Yêu cầu công việc không được để trống.';
    }

    const trimmedDesc = formData.description?.trim() || '';
    if (!trimmedDesc) {
      errors.description = 'Mô tả công việc không được để trống hoặc chỉ chứa khoảng trắng.';
    } else if (trimmedDesc.length > 2000) {
      errors.description = 'Mô tả công việc không được vượt quá 2000 ký tự.';
    }

    const trimmedBenefits = formData.benefits?.trim() || '';
    if (trimmedBenefits.length > 2000) {
      errors.benefits = 'Quyền lợi & Phúc lợi không được vượt quá 2000 ký tự.';
    }

    if (formData.rankingConfig?.weights) {
      const totalWeights = Object.values(formData.rankingConfig.weights).reduce((sum, w) => sum + (w as number), 0);
      if (totalWeights !== 100) {
        errors.weights = `Tổng trọng số AI Ranking phải bằng 100% (Hiện tại: ${totalWeights}%).`;
      }
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setSaving(false);
      return;
    }
    setFieldErrors({});

    const statusToUse = submitTargetRef.current || formData.status || 'draft';
    const payload: any = {
      ...formData,
      title: formData.title?.trim(),
      description: trimmedDesc,
      benefits: trimmedBenefits || undefined,
      status: statusToUse,
      skills: skillsArray,
      requirements: reqsArray.length > 0 ? reqsArray : skillsArray,
      salaryMin: formData.salaryType === 'negotiable' ? undefined : Number(formData.salaryMin),
      salaryMax: formData.salaryType === 'negotiable' ? undefined : Number(formData.salaryMax),
      vacancies: Number(formData.vacancies),
    };

    try {
      if (editingId) {
        const updated = await employerService.updateJob(editingId, payload);
        setJobs(jobs.map((j) => (j.id === editingId ? updated : j)));
        const isAutoPublished = updated.status?.toUpperCase() === 'PUBLISHED' || updated.status?.toUpperCase() === 'ACTIVE';
        const isPending = updated.status?.toUpperCase() === 'PENDING_REVIEW';
        setMessage(
          isAutoPublished
            ? 'Cập nhật tin tuyển dụng thành công và đang được hiển thị ngay trên hệ thống!'
            : isPending
            ? 'Đã gửi duyệt tin tuyển dụng thành công. Vui lòng chờ Admin kiểm duyệt.'
            : 'Cập nhật tin tuyển dụng thành công!'
        );
      } else {
        const created = await employerService.createJob(payload);
        setJobs([created, ...jobs]);
        const isAutoPublished = created.status?.toUpperCase() === 'PUBLISHED' || created.status?.toUpperCase() === 'ACTIVE';
        const isPending = created.status?.toUpperCase() === 'PENDING_REVIEW';
        setMessage(
          isAutoPublished
            ? 'Đăng tin tuyển dụng mới thành công! Do công ty đã có tin được duyệt trước đó nên tin mới được xuất bản ngay.'
            : isPending
            ? 'Đăng tin đầu tiên thành công! Tin tuyển dụng đang ở trạng thái Chờ duyệt bởi Admin.'
            : 'Lưu tin tuyển dụng thành công!'
        );
      }
      setShowForm(false);
    } catch (err: any) {
      const info = parseApiError(err);
      setError(info.message);
      setPlanLimitReached(info.isPlanLimit);
    } finally {
      setSaving(false);
    }
  }

  if (loading && !company) return <p className="loading">Đang tải dữ liệu tuyển dụng...</p>;
  if (!company) return <div className="content-card"><p className="error">{error || 'Không tìm thấy thông tin công ty.'}</p></div>;

  const isVerified = company.verified || company.verificationStatus?.toLowerCase() === 'verified';
  const hasApprovedJob = jobs.some((j) => {
    const st = j.status?.toLowerCase();
    return st === 'published' || st === 'active' || st === 'closed' || st === 'expired' || st === 'archived';
  });

  return (
    <>
    <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 md:p-8 mb-6">
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 mb-2">Quản lý & Đăng tin tuyển dụng</h1>
            <p className="text-gray-500">
              Đăng tin tìm kiếm nhân tài và quản lý các vị trí đang mở tại <span className="font-semibold text-gray-700">{company.name}</span>
            </p>
          </div>
          {isVerified && !showForm && (
            <button
              onClick={handleOpenAdd}
              className="inline-flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-5 py-2.5 rounded-lg font-medium transition-colors shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 w-full md:w-auto"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4"></path>
              </svg>
              Tạo tin tuyển dụng mới
            </button>
          )}
        </div>
      </div>

      {message && (
        <div className="mb-6 p-4 rounded-lg bg-green-50 border border-green-200 text-green-700 flex items-start gap-3">
          <svg className="w-5 h-5 text-green-600 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
          <p className="font-medium">{message}</p>
        </div>
      )}
      {planLimitReached && error ? (
        <div className="mb-6">
          <PlanLimitAlert message={error} />
        </div>
      ) : error ? (
        <div className="mb-6 p-4 rounded-lg bg-red-50 border border-red-200 text-red-700 flex items-start gap-3 whitespace-pre-wrap">
          <svg className="w-5 h-5 text-red-600 mt-0.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
          <p className="font-medium">{error}</p>
        </div>
      ) : null}

      {!isVerified ? (
        <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-6 mb-8 flex flex-col items-start gap-4 shadow-sm">
          <div className="flex items-start gap-4">
            <div className="p-2 bg-yellow-100 rounded-full text-yellow-600">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8V7a4 4 0 00-8 0v4h8z"></path></svg>
            </div>
            <div>
              <h3 className="text-lg font-semibold text-yellow-800 mb-1">
                Chức năng Đăng tin tuyển dụng yêu cầu xác thực doanh nghiệp
              </h3>
              <p className="text-yellow-700 leading-relaxed">
                Hiện tại công ty <strong>{company.name}</strong> có trạng thái pháp lý là:{' '}
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-yellow-200 text-yellow-800 uppercase">
                  {company.verificationStatus || 'Chưa gửi duyệt'}
                </span>
                .<br />
                Theo quy định của hệ thống SRP, <strong>chỉ các doanh nghiệp đã được Admin xác thực pháp lý thành công</strong> mới được phép sử dụng tính năng tạo và đăng tin tuyển dụng.
              </p>
            </div>
          </div>
          <div className="flex flex-wrap gap-3 mt-2 ml-14">
            <Link
              to="/employer/verification"
              className="inline-flex items-center gap-2 bg-yellow-600 hover:bg-yellow-700 text-white px-5 py-2 rounded-lg font-medium transition-colors"
            >
              Đến trang Xác thực pháp lý
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M14 5l7 7m0 0l-7 7m7-7H3"></path></svg>
            </Link>
            <Link
              to="/employer/company-profile"
              className="inline-flex items-center bg-white border border-gray-300 hover:bg-gray-50 text-gray-700 px-5 py-2 rounded-lg font-medium transition-colors"
            >
              Xem hồ sơ công ty
            </Link>
          </div>
        </div>
      ) : null}

      {showForm && (
      <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-[1000] p-4">
        <div className="bg-white rounded-2xl w-full max-w-4xl max-h-[90vh] overflow-y-auto shadow-2xl relative flex flex-col">
          <div className="p-6 border-b border-gray-100 flex justify-between items-center sticky top-0 bg-white/95 backdrop-blur z-10">
            <h2 className="m-0 text-xl font-bold text-gray-900">
              {editingId ? 'Chỉnh sửa tin tuyển dụng' : 'Tạo tin tuyển dụng mới'}
            </h2>
            <button
              type="button"
              onClick={() => { setShowForm(false); setFieldErrors({}); }}
              className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 font-semibold rounded-lg transition-colors"
            >
              Hủy
            </button>
          </div>
          
          <div className="p-6 lg:p-8 flex-1">
          {editingId && (() => {
            const editingJob = jobs.find((j) => j.id === editingId);
            const st = editingJob?.status?.toLowerCase();
            if (st !== 'rejected' && st !== 'awaiting_company') return null;
            const isReportFix = st === 'awaiting_company';
            return (
              <div className={`mb-6 p-5 rounded-xl border-l-4 ${isReportFix ? 'bg-orange-50 border-orange-200 border-l-orange-600' : 'bg-red-50 border-red-200 border-l-red-600'}`}>
                <div className={`font-semibold mb-2 ${isReportFix ? 'text-orange-800' : 'text-red-800'}`}>
                {isReportFix ? 'Yêu cầu chỉnh sửa từ Admin (tin bị báo cáo)' : 'Phản hồi từ Bộ phận kiểm duyệt'}
              </div>
                <div className={`bg-white p-3 rounded-lg border mb-3 text-sm leading-relaxed ${isReportFix ? 'border-orange-100 text-orange-900' : 'border-red-100 text-red-900'}`}>
                {editingJob?.rejectionReason || 'Vui lòng kiểm tra và hoàn thiện các nội dung chưa đạt yêu cầu trước khi gửi lại.'}
              </div>
                {isReportFix && editingJob?.reportFixDeadline && (
                  <div className="text-sm font-semibold text-orange-800 mb-2">
                    Hạn chỉnh sửa: {new Date(editingJob.reportFixDeadline).toLocaleString('vi-VN')}. Quá hạn tin sẽ bị gỡ tự động.
                  </div>
                )}
                <div className={`text-sm opacity-90 ${isReportFix ? 'text-orange-800' : 'text-red-800'}`}>
                Anh/chị vui lòng cập nhật lại thông tin bên dưới theo yêu cầu, sau đó nhấn nút <b>"Lưu & Nộp kiểm duyệt"</b> để gửi lại cho Admin duyệt.
              </div>
            </div>
            );
          })()}

            <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <label className="md:col-span-2 flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              <span>Tên vị trí tuyển dụng <span className="text-red-500">*</span></span>
              <input
                required
                className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900"
                value={formData.title}
                onChange={(e) => {
                  let val = e.target.value;
                  if (val.length > 0) {
                    val = val.charAt(0).toUpperCase() + val.slice(1);
                  }
                  setFormData({ ...formData, title: val });
                }}
                placeholder="Ví dụ: Vị trí + Ngành nghề / Chuyên môn + (Dự án nếu có)"
              />
              <span className="text-xs text-gray-500 font-normal">Gợi ý cách điền: Vị trí + Ngành nghề / Chuyên môn + (Dự án nếu có). (6 - 50 ký tự, viết hoa chữ cái đầu)</span>
              {fieldErrors.title && <span className="text-red-600 text-sm mt-1">{fieldErrors.title}</span>}
            </label>

            <label className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              Loại hình công việc
              <select
                className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900"
                value={formData.jobType}
                onChange={(e) => setFormData({ ...formData, jobType: e.target.value })}
              >
                <option value="full_time">Toàn thời gian (Full-time)</option>
                <option value="part_time">Bán thời gian (Part-time)</option>
                <option value="contract">Hợp đồng (Contract)</option>
                <option value="internship">Thực tập sinh (Internship)</option>
                <option value="freelance">Tự do (Freelance)</option>
              </select>
            </label>

            <label className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              Hình thức làm việc
              <select
                className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900"
                value={formData.workMode}
                onChange={(e) => setFormData({ ...formData, workMode: e.target.value })}
              >
                <option value="onsite">Làm tại văn phòng (Onsite)</option>
                <option value="remote">Làm từ xa (Remote)</option>
                <option value="hybrid">Kết hợp (Hybrid)</option>
              </select>
            </label>

            <label className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              Cấp bậc kinh nghiệm
              <select
                className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900"
                value={formData.experienceLevel}
                onChange={(e) => setFormData({ ...formData, experienceLevel: e.target.value })}
              >
                <option value="intern">Thực tập sinh (Intern)</option>
                <option value="fresher">Mới tốt nghiệp (Fresher / Dưới 1 năm)</option>
                <option value="junior">Nhân viên (Junior / 1-3 năm)</option>
                <option value="middle">Chuyên viên (Middle / 3-5 năm)</option>
                <option value="senior">Chuyên gia (Senior / Trên 5 năm)</option>
                <option value="manager">Quản lý / Trưởng phòng (Manager)</option>
              </select>
            </label>

            <label className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              Số lượng tuyển
              <input
                className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900 [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                type="number"
                min="1"
                required
                value={formData.vacancies === undefined ? '' : formData.vacancies}
                onChange={(e) => {
                  const val = e.target.value;
                  setFormData({ ...formData, vacancies: val ? Number(val) : ('' as any) });
                }}
                onKeyDown={(e) => {
                  if (['-', '+', 'e', 'E', '.'].includes(e.key)) {
                    e.preventDefault();
                  }
                  if (e.key === '0' && (e.target as HTMLInputElement).value === '') {
                    e.preventDefault();
                  }
                }}
              />
              {fieldErrors.vacancies && <span className="text-red-600 text-sm mt-1">{fieldErrors.vacancies}</span>}
            </label>

            <label className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              Hình thức trả lương
              <select
                className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900"
                value={formData.salaryType}
                onChange={(e) => setFormData({ ...formData, salaryType: e.target.value })}
              >
                <option value="range">Trong khoảng (Min - Max)</option>
                <option value="fixed">Mức cố định</option>
                <option value="negotiable">Thỏa thuận (Negotiable)</option>
              </select>
            </label>

            {formData.salaryType === 'range' ? (
              <>
                <label className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
                  <span>Mức lương tối thiểu (VNĐ/tháng) <span className="text-red-500">*</span></span>
                  <input
                    className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900 [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                    type="number"
                    min="1"
                    value={formData.salaryMin === undefined || formData.salaryMin === null || formData.salaryMin === 0 ? '' : formData.salaryMin}
                    onChange={(e) => {
                      const val = e.target.value;
                      setFormData({ ...formData, salaryMin: val ? Number(val) : ('' as any) });
                    }}
                    onKeyDown={(e) => {
                      if (['-', '+', 'e', 'E', '.'].includes(e.key)) e.preventDefault();
                      if (e.key === '0' && (e.target as HTMLInputElement).value === '') e.preventDefault();
                    }}
                  />
                  {fieldErrors.salaryMin && <span className="text-red-600 text-sm mt-1">{fieldErrors.salaryMin}</span>}
                </label>
                <label className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
                  <span>Mức lương tối đa (VNĐ/tháng) <span className="text-red-500">*</span></span>
                  <input
                    className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900 [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                    type="number"
                    min="1"
                    value={formData.salaryMax === undefined || formData.salaryMax === null || formData.salaryMax === 0 ? '' : formData.salaryMax}
                    onChange={(e) => {
                      const val = e.target.value;
                      setFormData({ ...formData, salaryMax: val ? Number(val) : ('' as any) });
                    }}
                    onKeyDown={(e) => {
                      if (['-', '+', 'e', 'E', '.'].includes(e.key)) e.preventDefault();
                      if (e.key === '0' && (e.target as HTMLInputElement).value === '') e.preventDefault();
                    }}
                  />
                  {fieldErrors.salaryMax && <span className="text-red-600 text-sm mt-1">{fieldErrors.salaryMax}</span>}
                </label>
              </>
            ) : formData.salaryType === 'fixed' ? (
              <label className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
                <span>Mức lương cố định (VNĐ/tháng) <span className="text-red-500">*</span></span>
                <input
                  className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900 [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                  type="number"
                  min="1"
                  value={formData.salaryMax === undefined || formData.salaryMax === null || formData.salaryMax === 0 ? '' : formData.salaryMax}
                  onChange={(e) => {
                    const val = e.target.value;
                    setFormData({ ...formData, salaryMax: val ? Number(val) : ('' as any) });
                  }}
                  onKeyDown={(e) => {
                    if (['-', '+', 'e', 'E', '.'].includes(e.key)) e.preventDefault();
                    if (e.key === '0' && (e.target as HTMLInputElement).value === '') e.preventDefault();
                  }}
                />
                {fieldErrors.salaryMax && <span className="text-red-600 text-sm mt-1">{fieldErrors.salaryMax}</span>}
              </label>
            ) : (
              <div className="md:col-span-2 flex items-center justify-center p-4 bg-gray-50 border border-gray-200 rounded-lg text-gray-600 text-sm">
                Mức lương sẽ được hiển thị là "Thỏa thuận" đối với ứng viên.
              </div>
            )}

            <div className="md:col-span-2 grid grid-cols-1 md:grid-cols-2 gap-6">
              <label className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
                Chọn chi nhánh (Branch)
                <select
                  className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900"
                  value={formData.companyLocationId || ''}
                  onChange={(e) => {
                    const loc = locations.find((l) => l.id === e.target.value);
                    setFormData({
                      ...formData,
                      companyLocationId: e.target.value,
                      location: loc
                        ? [loc.branchName, loc.address, loc.district, loc.city].filter(Boolean).join(', ')
                        : '',
                    });
                  }}
                >
                  <option value="">-- Chọn từ chi nhánh công ty --</option>
                  {locations.map((loc) => (
                    <option key={loc.id} value={loc.id}>
                      {loc.branchName} {loc.city ? `(${loc.city})` : ''} {loc.headquarter ? '(Trụ sở chính)' : ''}
                    </option>
                  ))}
                </select>
                {locations.length === 0 && (
                  <span className="text-sm text-gray-500 mt-1">
                    Chưa có chi nhánh nào. <a href="/employer/locations" target="_blank" rel="noreferrer" className="text-blue-600 font-semibold hover:underline">+ Quản lý/Thêm chi nhánh</a>
                  </span>
                )}
              </label>

              <div className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
                {formData.companyLocationId ? (
                  <>
                    <span>Địa điểm hiển thị trên tin tuyển dụng</span>
                    <div className="w-full px-4 py-2.5 rounded-lg border border-gray-200 bg-gray-50 font-normal text-gray-700">
                      {formData.location}
                    </div>
                    <span className="text-xs text-gray-500">Địa điểm được lấy từ chi nhánh đã chọn.</span>
                  </>
                ) : (
                  <VietnamAddressPicker
                    label="Địa điểm hiển thị trên tin tuyển dụng"
                  required
                    allowRemote
                  value={formData.location || ''}
                    onChange={(value) => setFormData({ ...formData, location: value })}
                  />
                )}
                {fieldErrors.location && <span className="text-red-600 text-sm mt-1" role="alert">{fieldErrors.location}</span>}
              </div>
            </div>

            <label className="flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              <span>Hạn nộp hồ sơ (Deadline) <span className="text-red-500">*</span></span>
              <input
                className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900"
                type="date"
                required
                min={new Date().toISOString().split('T')[0]}
                value={formData.deadline}
                onChange={(e) => setFormData({ ...formData, deadline: e.target.value })}
              />
              {fieldErrors.deadline && <span className="text-red-600 text-sm mt-1">{fieldErrors.deadline}</span>}
            </label>

            <label className="md:col-span-2 flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              Thời gian làm việc
              <select
                className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900"
                value={PRESET_WORKING_TIMES.includes(formData.workingTime) ? formData.workingTime : 'CUSTOM'}
                onChange={(e) => {
                  const val = e.target.value;
                  if (val === 'CUSTOM') {
                    setFormData({ ...formData, workingTime: '' });
                  } else {
                    setFormData({ ...formData, workingTime: val });
                  }
                }}
              >
                {PRESET_WORKING_TIMES.map((time) => (
                  <option key={time} value={time}>
                    {time}
                  </option>
                ))}
                <option value="CUSTOM">-- Khác (Tự nhập thời gian làm việc cụ thể) --</option>
              </select>
              {(!PRESET_WORKING_TIMES.includes(formData.workingTime) || formData.workingTime === '') && (
                <input
                  className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal mt-2 text-gray-900"
                  value={formData.workingTime}
                  onChange={(e) => setFormData({ ...formData, workingTime: e.target.value })}
                  placeholder="Nhập thời gian làm việc chi tiết (VD: Thứ 2 - Thứ 6 (07:30 - 16:30))"
                />
              )}
            </label>

            <label className="md:col-span-2 flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              <span>Kỹ năng yêu cầu (Nhập các từ khóa ngăn cách bằng dấu phẩy) <span className="text-red-500">*</span></span>
              <input
                className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal text-gray-900"
                required
                value={skillsInput}
                onChange={(e) => setSkillsInput(e.target.value)}
                placeholder="Ví dụ: Java, Spring Boot, MySQL, Docker, ReactJS"
              />
              {fieldErrors.skills && <span className="text-red-600 text-sm mt-1">{fieldErrors.skills}</span>}
            </label>

            <label className="md:col-span-2 flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              <span>Mô tả công việc (Description) <span className="text-red-500">*</span></span>
              <textarea
                className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal resize-y min-h-[120px] text-gray-900"
                required
                rows={5}
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                onBlur={(e) => setFormData({ ...formData, description: e.target.value.trim() })}
                placeholder="Mô tả chi tiết các trách nhiệm, công việc hàng ngày của ứng viên..."
              />
              {fieldErrors.description && <span className="text-red-600 text-sm mt-1">{fieldErrors.description}</span>}
            </label>

            <label className="md:col-span-2 flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              Yêu cầu công việc (Requirements - Mỗi yêu cầu 1 dòng)
              <textarea
                className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none transition-all font-normal resize-y min-h-[120px] text-gray-900"
                rows={4}
                value={reqsInput}
                onChange={(e) => setReqsInput(e.target.value)}
                placeholder="Tốt nghiệp đại học chuyên ngành CNTT&#10;Có ít nhất 1 năm kinh nghiệm làm việc với Spring Boot&#10;Tư duy logic tốt, có tinh thần trách nhiệm cao"
              />
              {fieldErrors.requirements && <span className="text-red-600 text-sm mt-1">{fieldErrors.requirements}</span>}
            </label>

            <label className="md:col-span-2 flex flex-col gap-1.5 text-sm font-semibold text-gray-700">
              Quyền lợi & Phúc lợi (Benefits)
              <textarea
                className={`w-full px-4 py-3 rounded-lg border focus:ring-1 outline-none transition-all font-normal resize-y min-h-[120px] text-gray-900 ${fieldErrors.benefits ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'}`}
                rows={4}
                value={formData.benefits || ''}
                onChange={(e) => setFormData({ ...formData, benefits: e.target.value })}
                onBlur={(e) => setFormData({ ...formData, benefits: e.target.value.trim() })}
                placeholder="Mức lương cạnh tranh, review lương 2 lần/năm&#10;Bảo hiểm chăm sóc sức khỏe toàn diện&#10;Môi trường trẻ trung, năng động"
              />
              {fieldErrors.benefits && <span className="text-red-600 text-sm mt-1">{fieldErrors.benefits}</span>}
            </label>

            {/* AI Ranking Configuration Section */}
            <div className="md:col-span-2 relative mt-8 pt-6 border-t-2 border-gray-100">
                {hasApplications && (
                  <div className="absolute top-6 inset-x-0 bottom-0 bg-white/60 z-10 flex items-center justify-center backdrop-blur-[1px]">
                    <div className="bg-white p-6 rounded-xl shadow-lg border border-gray-200 text-center max-w-sm">
                      <div className="text-3xl mb-2 mono-icon"><IconLock size={32} /></div>
                      <h4 className="text-lg font-semibold text-gray-900 mb-2">Đã khóa Cấu hình AI</h4>
                      <p className="text-sm text-gray-600 leading-relaxed m-0">Tin tuyển dụng này đã có người nộp CV. Để đảm bảo công bằng cho tất cả ứng viên, tiêu chí chấm điểm không thể thay đổi nữa.</p>
                    </div>
                  </div>
                )}
                
                <div className="flex items-center gap-3 mb-5">
                  <div className="bg-blue-50 p-2.5 rounded-lg flex items-center justify-center mono-icon">
                    <IconRobot size={24} />
                  </div>
                  <div className="flex-1">
                    <h3 className="text-lg font-bold text-gray-900 m-0">Cấu hình AI chấm điểm (Smart Ranking)</h3>
                    <p className="text-sm text-gray-500 mt-1 m-0">Hệ thống tự động đánh giá độ phù hợp của CV với Yêu cầu tuyển dụng.</p>
                  </div>
                  
                  <label className={`flex items-center gap-2 cursor-pointer px-4 py-2 rounded-full border transition-all ${formData.rankingConfig?.enabled ? 'bg-emerald-50 border-emerald-300' : 'bg-gray-50 border-gray-200'}`}>
                    <input
                      type="checkbox"
                      checked={formData.rankingConfig?.enabled || false}
                      onChange={(e) => {
                        const isEnabled = e.target.checked;
                        setFormData({
                          ...formData,
                          rankingConfig: { ...formData.rankingConfig, enabled: isEnabled }
                        });
                        setShowAiConfig(isEnabled); // Auto expand if enabled
                      }}
                      className="cursor-pointer w-4 h-4 accent-emerald-600"
                    />
                    <span className={`font-semibold text-[15px] ${formData.rankingConfig?.enabled ? 'text-emerald-800' : 'text-gray-600'}`}>
                      {formData.rankingConfig?.enabled ? 'Đã Bật AI' : 'Bật AI'}
                    </span>
                  </label>
                  
                  {formData.rankingConfig?.enabled && (
                    <div 
                      className={`text-gray-500 text-xl cursor-pointer p-2 transition-transform duration-300 ${showAiConfig ? 'rotate-180' : ''}`} 
                      onClick={() => setShowAiConfig(!showAiConfig)}
                    >
                      ▼
                    </div>
                  )}
                </div>

                {formData.rankingConfig?.enabled && showAiConfig && (
                <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                  
                  {/* Top Bar: Template Selection */}
                  <div className="p-4 md:p-6 bg-gray-50 border-b border-gray-200 flex flex-wrap justify-between items-center gap-4">
                    <div>
                      <h4 className="text-[15px] font-semibold text-gray-900 m-0">Mẫu phân bổ trọng số (Template)</h4>
                      <p className="text-sm text-gray-500 mt-1 m-0">Chọn mẫu để tự động điền điểm cho các tiêu chí bên dưới.</p>
                    </div>
                    <select
                      className="px-4 py-2.5 rounded-lg border border-gray-300 text-[15px] font-medium text-gray-700 bg-white shadow-sm focus:outline-none focus:ring-1 focus:ring-blue-500 min-w-[220px]"
                      value={formData.rankingConfig?.template || 'balanced'}
                      onChange={(e) => {
                        const template = e.target.value;
                        let newWeights = { ...formData.rankingConfig?.weights };
                        if (template === 'balanced') {
                          newWeights = { skills: 30, experience: 30, projects: 15, education: 15, certificates: 10 };
                        } else if (template === 'skills_focus') {
                          newWeights = { skills: 50, experience: 20, projects: 20, education: 5, certificates: 5 };
                        } else if (template === 'experience_focus') {
                          newWeights = { skills: 30, experience: 50, projects: 10, education: 5, certificates: 5 };
                        } else if (template === 'project_focus') {
                          newWeights = { skills: 30, experience: 10, projects: 50, education: 5, certificates: 5 };
                        }
                        setFormData({
                          ...formData,
                          rankingConfig: {
                            ...formData.rankingConfig,
                            template: template,
                            weights: newWeights
                          }
                        });
                      }}
                    >
                      <option value="balanced">⚖️ Cân bằng (Balanced)</option>
                      <option value="skills_focus">🎯 Tập trung Kỹ năng</option>
                      <option value="experience_focus">⏳ Tập trung Kinh nghiệm</option>
                      <option value="project_focus">🚀 Tập trung Dự án</option>
                      <option value="custom">⚙️ Tùy chỉnh (Custom)</option>
                    </select>
                  </div>

                  <div className="grid grid-cols-1 lg:grid-cols-2">
                    
                    {/* Left Column: Scoring Criteria */}
                    <div className="p-6 border-b lg:border-b-0 lg:border-r border-gray-200">
                      <div className="flex justify-between items-center mb-5">
                        <h4 className="m-0 text-base font-semibold text-gray-900">
                          Tiêu chí đánh giá
                        </h4>
                        {(() => {
                          const total = Object.entries(formData.rankingConfig?.weights || {})
                            .filter(([k]) => formData.rankingConfig?.enabled_criteria?.includes(k))
                            .reduce((sum, [, v]) => sum + Number(v), 0);
                          const isError = total !== 100;
                          return (
                            <span className={`text-[13px] px-3 py-1 rounded-full font-semibold border ${isError ? 'bg-red-50 text-red-600 border-red-200' : 'bg-emerald-50 text-emerald-600 border-emerald-200'}`}>
                              Tổng: {total}% {isError && ' (Cần đúng 100%)'}
                            </span>
                          );
                        })()}
                      </div>
                      {fieldErrors.weights && <div className="text-red-600 text-sm mb-4 bg-red-50 p-2 rounded-lg">{fieldErrors.weights}</div>}
                      
                      {['skills', 'experience', 'projects', 'education', 'certificates'].map(criteria => {
                        const isEnabled = formData.rankingConfig?.enabled_criteria?.includes(criteria) ?? true;
                        
                        return (
                          <div key={criteria} className={`flex items-center mb-3 p-3 rounded-lg border transition-all ${isEnabled ? 'bg-gray-50 border-gray-200 opacity-100' : 'bg-white border-gray-100 opacity-60'}`}>
                            <label className="flex-1 flex items-center gap-3 cursor-pointer m-0">
                              <input
                                type="checkbox"
                                checked={isEnabled}
                                onChange={(e) => {
                                  const current = formData.rankingConfig?.enabled_criteria || [];
                                  const next = e.target.checked ? [...current, criteria] : current.filter((c: string) => c !== criteria);
                                  setFormData({
                                    ...formData,
                                    rankingConfig: { ...formData.rankingConfig, enabled_criteria: next }
                                  });
                                }}
                                className="cursor-pointer w-4 h-4 accent-blue-600"
                              />
                              <span className={`text-[15px] ${isEnabled ? 'text-gray-900 font-semibold' : 'text-gray-500 font-medium'}`}>
                                {criteria === 'skills' ? 'Kỹ năng (Skills)' : 
                                 criteria === 'experience' ? 'Kinh nghiệm (Experience)' : 
                                 criteria === 'projects' ? 'Dự án (Projects)' : 
                                 criteria === 'education' ? 'Học vấn (Education)' : 'Chứng chỉ (Certificates)'}
                              </span>
                            </label>
                            
                            {isEnabled && (
                              <div className="flex items-center gap-2">
                                <input
                                  type="number"
                                  min="0" max="100"
                                  value={formData.rankingConfig?.weights?.[criteria] || 0}
                                  onChange={(e) => {
                                    setFormData({
                                      ...formData,
                                      rankingConfig: {
                                        ...formData.rankingConfig,
                                        template: 'custom',
                                        weights: { ...formData.rankingConfig?.weights, [criteria]: parseInt(e.target.value) || 0 }
                                      }
                                    });
                                  }}
                                  className="w-16 px-2.5 py-1.5 rounded-lg border border-gray-300 text-center font-semibold text-gray-900 focus:outline-none focus:ring-1 focus:ring-blue-500"
                                />
                                <span className="text-gray-500 font-semibold">%</span>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>

                    {/* Right Column: Mandatory Requirements */}
                    <div className="p-6 bg-gray-50">
                      <h4 className="m-0 mb-3 text-base font-semibold text-gray-900">
                        Yêu cầu Bắt buộc (Hard Filters)
                      </h4>
                      <p className="text-[13.5px] text-gray-500 mb-6 leading-relaxed">
                        Hệ thống sẽ đánh dấu Ứng viên là <strong className="text-red-500 font-semibold">"Thiếu yêu cầu"</strong> nếu CV không đáp ứng các tiêu chí này. Hãy cẩn trọng để không loại nhầm ứng viên.
                      </p>

                      <div className="mb-6">
                        <label className="block font-semibold mb-1.5 text-sm text-gray-700">
                          ⚡ Kỹ năng bắt buộc
                        </label>
                        <p className="m-0 mb-2 text-xs text-gray-400">Cách nhau bằng dấu phẩy (,)</p>
                        <input
                          type="text"
                          value={formData.rankingConfig?.mandatory?.skills?.join(', ') || ''}
                          onChange={(e) => {
                            const skillsStr = e.target.value;
                            setFormData({
                              ...formData,
                              rankingConfig: {
                                ...formData.rankingConfig,
                                mandatory: {
                                  ...formData.rankingConfig?.mandatory,
                                  skills: skillsStr ? skillsStr.split(',').map(s => s.trim()).filter(s => s.length > 0) : []
                                }
                              }
                            });
                          }}
                          onInput={(e: any) => { e.target.dataset.raw = e.target.value; }}
                          placeholder="VD: Java, Spring Boot, MySQL..."
                          className="w-full px-4 py-2.5 rounded-lg border border-gray-300 focus:outline-none focus:ring-1 focus:ring-blue-500 text-[15px] shadow-sm transition-colors"
                        />
                      </div>
                      <div>
                        <label className="block font-semibold mb-2 text-sm text-gray-700">
                          ⏳ Kinh nghiệm tối thiểu
                        </label>
                        <div className="flex items-center gap-3">
                          <input
                            type="number"
                            min="0" step="0.5"
                            value={formData.rankingConfig?.mandatory?.min_experience_years || ''}
                            onChange={(e) => {
                              const val = parseFloat(e.target.value);
                              setFormData({
                                ...formData,
                                rankingConfig: {
                                  ...formData.rankingConfig,
                                  mandatory: {
                                    ...formData.rankingConfig?.mandatory,
                                    min_experience_years: isNaN(val) ? null : val
                                  }
                                }
                              });
                            }}
                            placeholder="VD: 1.5, 2..."
                            className="w-32 px-4 py-2.5 rounded-lg border border-gray-300 focus:outline-none focus:ring-1 focus:ring-blue-500 text-[15px] shadow-sm transition-colors"
                          />
                          <span className="text-gray-600 font-medium text-[15px]">Năm</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                )}
              </div>

            <div className="md:col-span-2 flex flex-wrap gap-3 mt-6 pt-6 border-t border-gray-100">
              {(() => {
                const totalWeight = Object.entries(formData.rankingConfig?.weights || {})
                  .filter(([k]) => formData.rankingConfig?.enabled_criteria?.includes(k))
                  .reduce((sum, [, v]) => sum + Number(v), 0);
                const isInvalidConfig = formData.rankingConfig?.enabled ? (totalWeight !== 100) : false;
                
                return (
                  <>
              {editingId ? (
                <button
                  type="submit"
                        disabled={saving || isInvalidConfig}
                  onClick={() => {
                    const editingStatus = jobs.find((j) => j.id === editingId)?.status?.toLowerCase();
                    if (editingStatus === 'awaiting_company' || editingStatus === 'rejected') {
                      submitTargetRef.current = 'pending_review';
                      setFormData((prev) => ({ ...prev, status: 'pending_review' }));
                    } else {
                      submitTargetRef.current = formData.status;
                    }
                  }}
                        className={`px-6 py-2.5 rounded-lg font-semibold text-[15px] transition-colors ${
                          (saving || isInvalidConfig) 
                            ? 'bg-gray-400 text-white cursor-not-allowed' 
                            : 'bg-blue-600 hover:bg-blue-700 text-white shadow-sm'
                        }`}
                >
                  {saving
                    ? 'Đang xử lý...'
                    : (jobs.find((j) => j.id === editingId)?.status?.toLowerCase() === 'awaiting_company'
                      || jobs.find((j) => j.id === editingId)?.status?.toLowerCase() === 'rejected')
                      ? 'Lưu & Nộp kiểm duyệt'
                      : 'Lưu lại'}
                </button>
              ) : (
                <>
                  <button
                    type="submit"
                          disabled={saving || isInvalidConfig}
                    onClick={() => {
                      submitTargetRef.current = 'draft';
                      setFormData((prev) => ({ ...prev, status: 'draft' }));
                    }}
                          className={`px-5 py-2.5 rounded-lg font-semibold text-[15px] border transition-colors ${
                            (saving || isInvalidConfig)
                              ? 'bg-gray-100 text-gray-400 border-gray-200 cursor-not-allowed'
                              : 'bg-white hover:bg-gray-50 text-gray-700 border-gray-300 shadow-sm'
                          }`}
                  >
                    {saving ? 'Đang xử lý...' : 'Lưu bản nháp'}
                  </button>
                  {hasApprovedJob ? (
                    <button
                      type="submit"
                            disabled={saving || isInvalidConfig}
                      onClick={() => {
                        submitTargetRef.current = 'published';
                        setFormData((prev) => ({ ...prev, status: 'published' }));
                      }}
                            className={`px-6 py-2.5 rounded-lg font-semibold text-[15px] transition-colors ${
                              (saving || isInvalidConfig)
                                ? 'bg-gray-400 text-white cursor-not-allowed'
                                : 'bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm'
                            }`}
                          >
                            {saving ? 'Đang xử lý...' : 'Đăng tin ngay (Miễn duyệt)'}
                    </button>
                  ) : (
                    <button
                      type="submit"
                            disabled={saving || isInvalidConfig}
                      onClick={() => {
                        submitTargetRef.current = 'pending_review';
                        setFormData((prev) => ({ ...prev, status: 'pending_review' }));
                      }}
                            className={`px-6 py-2.5 rounded-lg font-semibold text-[15px] transition-colors ${
                              (saving || isInvalidConfig)
                                ? 'bg-gray-400 text-white cursor-not-allowed'
                                : 'bg-blue-600 hover:bg-blue-700 text-white shadow-sm'
                            }`}
                    >
                      {saving ? 'Đang xử lý...' : 'Lưu & Nộp kiểm duyệt'}
                    </button>
                  )}
                </>
              )}
              <button
                type="button"
                onClick={() => setShowForm(false)}
                      className="px-5 py-2.5 rounded-lg font-semibold text-[15px] text-gray-600 hover:bg-gray-100 transition-colors bg-transparent"
              >
                Hủy
              </button>
                  </>
                );
              })()}
            </div>
          </form>
        </div>
        </div>
      </div>
      )}

      <div>
        <div style={{ background: '#f8fafc', padding: '20px', borderRadius: '10px', border: '1px solid #e2e8f0', marginBottom: '24px' }}>
          <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', alignItems: 'center', marginBottom: '16px' }}>
            <div style={{ flex: '1 1 300px', position: 'relative' }}>
              <input
                type="text"
                placeholder="Tìm kiếm theo tiêu đề vị trí, địa điểm, kỹ năng..."
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  fontSize: '0.95rem',
                  outline: 'none',
                  boxShadow: '0 1px 2px rgba(0,0,0,0.05)'
                }}
              />
            </div>
            {searchInput && (
              <button
                type="button"
                onClick={() => setSearchInput('')}
                style={{ background: 'transparent', border: 'none', color: '#64748b', cursor: 'pointer', fontSize: '0.875rem' }}
              >
                ✕ Xóa tìm kiếm
              </button>
            )}
          </div>

          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            {[
              { key: 'ALL', label: 'Tất cả', count: allJobsForCount.length },
              { key: 'AWAITING_COMPANY', label: 'Tin vi phạm cần sửa', count: allJobsForCount.filter(j => (j.status?.toUpperCase() || '') === 'AWAITING_COMPANY').length },
              { key: 'PENDING_REVIEW', label: 'Chờ duyệt', count: allJobsForCount.filter(j => (j.status?.toUpperCase() || '') === 'PENDING_REVIEW').length },
              { key: 'PUBLISHED', label: 'Đang tuyển', count: allJobsForCount.filter(j => (j.status?.toUpperCase() || '') === 'PUBLISHED' || (j.status?.toUpperCase() || '') === 'ACTIVE').length },
              { key: 'CLOSED', label: 'Đã đóng', count: allJobsForCount.filter(j => (j.status?.toUpperCase() || '') === 'CLOSED').length },
              { key: 'EXPIRED', label: 'Hết hạn', count: allJobsForCount.filter(j => (j.status?.toUpperCase() || '') === 'EXPIRED').length },
              { key: 'DRAFT', label: 'Bản nháp / Yêu cầu sửa', count: allJobsForCount.filter(j => (j.status?.toUpperCase() || '') === 'DRAFT' || (j.status?.toUpperCase() || '') === 'REJECTED').length },
              { key: 'ARCHIVED', label: 'Đã lưu trữ', count: allJobsForCount.filter(j => (j.status?.toUpperCase() || '') === 'ARCHIVED').length },
            ].map((tab) => {
              const active = statusFilter === tab.key;
              return (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setStatusFilter(tab.key as any)}
                  style={{
                    background: active ? '#2563eb' : '#ffffff',
                    color: active ? '#ffffff' : '#475569',
                    border: active ? '1px solid #2563eb' : '1px solid #cbd5e1',
                    padding: '6px 14px',
                    borderRadius: '20px',
                    fontSize: '0.875rem',
                    fontWeight: 600,
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '6px'
                  }}
                >
                  {tab.label}
                  <span style={{
                    background: active ? 'rgba(255,255,255,0.2)' : '#f1f5f9',
                    color: active ? '#ffffff' : '#64748b',
                    padding: '2px 8px',
                    borderRadius: '12px',
                    fontSize: '0.75rem'
                  }}>
                    {tab.count}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {(() => {
          return (
            <>
              <h2 style={{ fontSize: '1.3rem', color: '#1e293b', marginBottom: '16px' }}>
                Danh sách tin tuyển dụng
              </h2>

              {jobs.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '48px 24px', background: '#f8fafc', borderRadius: '8px', border: '1px dashed #cbd5e1' }}>
                  <p style={{ color: '#64748b', fontSize: '1.05rem', margin: '0 0 16px 0' }}>
                    {allJobsForCount.length === 0 ? 'Công ty chưa có tin tuyển dụng nào được đăng trên hệ thống.' : 'Không tìm thấy tin tuyển dụng nào phù hợp với điều kiện lọc.'}
                  </p>
                  {allJobsForCount.length === 0 && isVerified && (
                    <button
                      onClick={handleOpenAdd}
                      style={{ background: '#2563eb', color: '#fff', border: 'none', padding: '10px 20px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.9rem', boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)' }}
                    >
                      + Tạo tin tuyển dụng đầu tiên
                    </button>
                  )}
                </div>
              ) : (
                <>
                  <div style={{ display: 'grid', gap: '12px' }}>
                    {jobs.map((job) => {
                    const st = job.status?.toLowerCase() || 'draft';
                    const statusBg = st === 'published' || st === 'active' ? '#ecfdf5' : st === 'pending_review' ? '#eff6ff' : st === 'awaiting_company' ? '#fff7ed' : st === 'rejected' ? '#fef2f2' : st === 'expired' ? '#fef3c7' : st === 'draft' ? '#f8fafc' : st === 'archived' ? '#f3f4f6' : '#f1f5f9';
                    const statusColor = st === 'published' || st === 'active' ? '#047857' : st === 'pending_review' ? '#1d4ed8' : st === 'awaiting_company' ? '#c2410c' : st === 'rejected' ? '#b91c1c' : st === 'expired' ? '#b45309' : st === 'draft' ? '#475569' : st === 'archived' ? '#374151' : '#64748b';
                    const statusBorder = st === 'published' || st === 'active' ? '#a7f3d0' : st === 'pending_review' ? '#bfdbfe' : st === 'awaiting_company' ? '#fed7aa' : st === 'rejected' ? '#fecaca' : st === 'expired' ? '#fde68a' : st === 'draft' ? '#cbd5e1' : st === 'archived' ? '#d1d5db' : '#e2e8f0';
                    const statusDot = st === 'published' || st === 'active' ? '#10b981' : st === 'pending_review' ? '#3b82f6' : st === 'awaiting_company' ? '#ea580c' : st === 'rejected' ? '#ef4444' : st === 'expired' ? '#f59e0b' : st === 'draft' ? '#94a3b8' : st === 'archived' ? '#6b7280' : '#64748b';
                    const statusLabel = st === 'published' || st === 'active' ? 'Đang tuyển' : st === 'pending_review' ? 'Chờ kiểm duyệt' : st === 'awaiting_company' ? 'Chờ công ty kiểm tra' : st === 'rejected' ? 'Yêu cầu chỉnh sửa' : st === 'expired' ? 'Hết hạn' : st === 'draft' ? 'Bản nháp' : st === 'archived' ? 'Đã lưu trữ' : 'Đã đóng';

                    return (
                      <div key={job.id} className="bg-white border border-gray-200 rounded-xl p-5 shadow-sm hover:shadow-md transition-all duration-200 flex flex-col lg:flex-row justify-between items-start gap-5">
                        <div className="flex-1 w-full lg:w-auto">
                          <div className="flex flex-col gap-2 mb-3">
                            <h3 className="m-0 text-lg text-gray-900 font-bold break-words line-clamp-2" title={job.title}>
                              {job.title}
                            </h3>
                            <div className="flex">
                            <span style={{
                              background: statusBg,
                              color: statusColor,
                              border: `1px solid ${statusBorder}`,
                              }} className="px-3 py-1 rounded-full text-xs font-semibold inline-flex items-center gap-2">
                                <span style={{ background: statusDot }} className="w-1.5 h-1.5 rounded-full"></span>
                              {statusLabel}
                            </span>
                            </div>
                          </div>

                          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-gray-500 text-sm mb-4">
                            <span className="flex items-center gap-1.5">
                              <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"></path><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
                              <strong className="text-gray-700 font-medium">{job.location || 'Hà Nội'}</strong>
                            </span>
                            <span className="hidden sm:inline text-gray-300">•</span>
                            <span className="flex items-center gap-1.5">
                              <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                              <strong className="text-emerald-600 font-medium">{job.salaryType === 'negotiable' ? 'Thỏa thuận' : `${job.salaryMin ? job.salaryMin.toLocaleString() : 0} - ${job.salaryMax ? job.salaryMax.toLocaleString() : 0} VNĐ`}</strong>
                            </span>
                            <span className="hidden sm:inline text-gray-300">•</span>
                            <span className="flex items-center gap-1.5">
                              <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"></path></svg>
                              <strong className="text-gray-700 font-medium">{job.vacancies || 1}</strong>
                            </span>
                            <span className="hidden sm:inline text-gray-300">•</span>
                            <span className="flex items-center gap-1.5">
                              <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path></svg>
                              <strong className="text-gray-700 font-medium">{job.viewsCount || 0}</strong>
                            </span>
                            
                            {job.deadline && (
                              <>
                                <span className="hidden sm:inline text-gray-300">•</span>
                                <span className="flex items-center gap-1.5">
                                  <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"></path></svg>
                                  Hạn nộp: <strong className="text-gray-700 font-medium">{new Date(job.deadline).toLocaleDateString('vi-VN')}</strong>
                                </span>
                              </>
                            )}
                          </div>

                    {job.skills && job.skills.length > 0 && (
                      <div className="flex gap-2 flex-wrap mb-2">
                        {job.skills.map((s, idx) => (
                          <span key={idx} className="bg-slate-100 text-slate-700 px-3 py-1 rounded-md text-xs font-medium border border-slate-200">
                            {s}
                          </span>
                        ))}
                      </div>
                    )}

                    {st === 'rejected' && (
                      <div style={{ marginTop: '16px', background: '#fef2f2', border: '1px solid #fecaca', borderLeft: '3px solid #dc2626', padding: '14px 16px', borderRadius: '6px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
                          <span style={{ fontWeight: 600, color: '#991b1b', fontSize: '0.875rem' }}>
                            Yêu cầu chỉnh sửa từ Bộ phận kiểm duyệt
                          </span>
                        </div>
                        <div style={{ background: '#ffffff', padding: '10px 12px', borderRadius: '4px', border: '1px solid #fee2e2', color: '#7f1d1d', fontSize: '0.875rem', lineHeight: 1.5, marginBottom: '6px' }}>
                          {job.rejectionReason || 'Vui lòng rà soát lại thông tin vị trí tuyển dụng theo quy định.'}
                        </div>
                        <div style={{ fontSize: '0.8rem', color: '#991b1b', opacity: 0.9 }}>
                          Vui lòng nhấn nút "Cập nhật & Nộp lại" để hoàn thiện hồ sơ và gửi duyệt lại.
                        </div>
                      </div>
                    )}

                    {st === 'awaiting_company' && (
                      <div style={{ marginTop: '16px', background: '#fff7ed', border: '1px solid #fed7aa', borderLeft: '3px solid #ea580c', padding: '14px 16px', borderRadius: '6px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
                          <span style={{ fontWeight: 600, color: '#9a3412', fontSize: '0.875rem' }}>
                            Tin bị báo cáo — Admin yêu cầu chỉnh sửa
                          </span>
                        </div>
                        <div style={{ background: '#ffffff', padding: '10px 12px', borderRadius: '4px', border: '1px solid #ffedd5', color: '#7c2d12', fontSize: '0.875rem', lineHeight: 1.5, marginBottom: '6px' }}>
                          {job.rejectionReason || 'Vui lòng kiểm tra nội dung tin tuyển dụng theo phản hồi từ Admin.'}
                        </div>
                        {job.reportFixDeadline && (
                          <div style={{ fontSize: '0.85rem', color: '#9a3412', fontWeight: 600, marginBottom: '6px' }}>
                            Hạn chỉnh sửa: {new Date(job.reportFixDeadline).toLocaleString('vi-VN')}. Quá hạn tin sẽ bị gỡ tự động.
                          </div>
                        )}
                        <div style={{ fontSize: '0.8rem', color: '#9a3412', opacity: 0.9 }}>
                          Chỉnh sửa tin rồi nhấn "Cập nhật & Gửi lại duyệt" trong hạn 3 ngày để Admin kiểm tra lại.
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="flex flex-wrap items-center gap-2 mt-4 lg:mt-0">
                    <Link
                      to={`/employer/jobs/${job.id}/applications`}
                      className="inline-flex items-center gap-1.5 bg-blue-50 hover:bg-blue-100 border border-blue-200 text-blue-700 px-3 py-1.5 rounded-lg font-semibold text-sm transition-colors"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"></path></svg>
                      Xem ứng viên ({job.applicationsCount || 0})
                    </Link>
                    {isVerified && st === 'draft' && (
                      <button
                        onClick={() => handleSubmitForReview(job.id, job.title)}
                        className={`inline-flex items-center px-3 py-1.5 rounded-lg font-semibold text-sm text-white transition-colors shadow-sm ${hasApprovedJob ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-blue-600 hover:bg-blue-700'}`}
                      >
                        {hasApprovedJob ? 'Đăng tin ngay (Miễn duyệt)' : 'Nộp kiểm duyệt'}
                      </button>
                    )}
                    {isVerified && (st === 'rejected' || st === 'awaiting_company') && (
                      <button
                        onClick={() => handleOpenEdit(job)}
                        className={`inline-flex items-center px-3 py-1.5 rounded-lg font-semibold text-sm text-white transition-colors shadow-sm ${st === 'awaiting_company' ? 'bg-orange-600 hover:bg-orange-700' : 'bg-red-600 hover:bg-red-700'}`}
                      >
                        {st === 'awaiting_company' ? 'Cập nhật & Gửi lại duyệt' : 'Cập nhật & Nộp lại'}
                      </button>
                    )}
                    {isVerified && st !== 'rejected' && st !== 'awaiting_company' && (
                      <button
                        onClick={() => handleOpenEdit(job)}
                        className="inline-flex items-center px-3 py-1.5 rounded-lg font-semibold text-sm bg-gray-50 hover:bg-gray-100 border border-gray-300 text-gray-700 transition-colors"
                      >
                        Chỉnh sửa
                      </button>
                    )}
                    {isVerified && (st === 'closed' || st === 'expired') && (
                      <button
                        onClick={() => handleReopenJob(job)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-semibold text-sm bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 text-emerald-700 transition-colors"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 11V7a4 4 0 118 0m-4 8v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2z"></path></svg>
                        Mở lại tin
                      </button>
                    )}

                    <button
                      onClick={() => setViewingJob(job)}
                      className="inline-flex items-center px-3 py-1.5 rounded-lg font-semibold text-sm bg-white hover:bg-gray-50 border border-gray-200 text-gray-600 transition-colors"
                    >
                      Xem chi tiết
                    </button>

                  </div>
                </div>
              );
            })}
          </div>

          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', gap: '8px', marginTop: '24px' }}>
              {Array.from({ length: totalPages }, (_, i) => (
                <button
                  key={i + 1}
                  onClick={() => setCurrentPage(i + 1)}
                  style={{
                    background: currentPage === i + 1 ? '#2563eb' : '#fff',
                    color: currentPage === i + 1 ? '#fff' : '#475569',
                    border: '1px solid #cbd5e1',
                    padding: '6px 14px',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    fontWeight: 600,
                    transition: 'all 0.2s'
                  }}
                >
                  {i + 1}
                </button>
              ))}
            </div>
              )}
                </>
              )}
            </>
          );
        })()}
      </div>
    </section>

    {viewingJob && (
      <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-[1000] p-4">
        <div className="bg-white rounded-2xl w-full max-w-4xl max-h-[90vh] overflow-y-auto shadow-2xl relative flex flex-col">
          <div className="p-6 border-b border-gray-100 flex justify-between items-center sticky top-0 bg-white/95 backdrop-blur z-10">
            <h2 className="m-0 text-xl font-bold text-gray-900">Chi tiết tin tuyển dụng</h2>
            <button 
              onClick={() => setViewingJob(null)} 
              className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
            </button>
          </div>

          <div className="p-6 lg:p-8 flex-1">
            <h3 className="text-2xl font-bold text-gray-900 mb-6">{viewingJob.title}</h3>

            <div className="grid grid-cols-2 md:grid-cols-3 gap-6 mb-8 bg-gray-50 p-6 rounded-xl border border-gray-100">
              <div>
                <div className="text-sm font-medium text-gray-500 mb-1">Mức lương</div>
                <div className="font-semibold text-emerald-600">
                  {viewingJob.salaryType === 'negotiable' ? 'Thỏa thuận' : `${viewingJob.salaryMin?.toLocaleString() || 0} - ${viewingJob.salaryMax?.toLocaleString() || 0} VNĐ`}
                </div>
              </div>
              <div>
                <div className="text-sm font-medium text-gray-500 mb-1">Địa điểm</div>
                <div className="font-semibold text-gray-900">{viewingJob.location || 'Hà Nội'}</div>
              </div>
              <div>
                <div className="text-sm font-medium text-gray-500 mb-1">Kinh nghiệm</div>
                <div className="font-semibold text-gray-900">{viewingJob.experienceLevel}</div>
              </div>
              <div>
                <div className="text-sm font-medium text-gray-500 mb-1">Hình thức</div>
                <div className="font-semibold text-gray-900">{viewingJob.workMode} / {viewingJob.jobType}</div>
              </div>
              <div>
                <div className="text-sm font-medium text-gray-500 mb-1">Số lượng</div>
                <div className="font-semibold text-gray-900">{viewingJob.vacancies} người</div>
              </div>
              <div>
                <div className="text-sm font-medium text-gray-500 mb-1">Hạn nộp hồ sơ</div>
                <div className="font-semibold text-red-600">{viewingJob.deadline ? new Date(viewingJob.deadline).toLocaleDateString('vi-VN') : 'Không giới hạn'}</div>
              </div>
            </div>

            <div className="space-y-8">
              <section>
                <h4 className="text-lg font-bold text-gray-900 border-b border-gray-100 pb-2 mb-4">Mô tả công việc</h4>
                <div className="whitespace-pre-wrap text-gray-600 leading-relaxed text-[15px]">
                {viewingJob.description}
              </div>
              </section>

              <section>
                <h4 className="text-lg font-bold text-gray-900 border-b border-gray-100 pb-2 mb-4">Yêu cầu công việc</h4>
                <div className="whitespace-pre-wrap text-gray-600 leading-relaxed text-[15px]">
                {viewingJob.requirements?.join('\n') || viewingJob.skills?.join(', ')}
              </div>
              </section>

              <section>
                <h4 className="text-lg font-bold text-gray-900 border-b border-gray-100 pb-2 mb-4">Quyền lợi</h4>
                <div className="whitespace-pre-wrap text-gray-600 leading-relaxed text-[15px]">
                {viewingJob.benefits || 'Theo quy định của công ty'}
              </div>
              </section>

              <section>
                <h4 className="text-lg font-bold text-gray-900 border-b border-gray-100 pb-2 mb-4">Kỹ năng chuyên môn</h4>
                <div className="flex gap-2 flex-wrap mt-2">
                {viewingJob.skills?.map((s, idx) => (
                    <span key={idx} className="bg-gray-100 text-gray-700 px-3 py-1.5 rounded-lg text-sm font-medium border border-gray-200">
                    {s}
                  </span>
                ))}
              </div>
              </section>

              <section>
                <h4 className="text-lg font-bold text-gray-900 border-b border-gray-100 pb-2 mb-4">Thời gian làm việc</h4>
                <div className="text-gray-600 text-[15px]">
                {viewingJob.workingTime || 'Giờ hành chính'}
              </div>
              </section>
            </div>

            {/* Footer with Đóng tin button */}
            <div className="mt-8 pt-6 border-t border-gray-100 flex justify-end gap-3">
              {isVerified && (viewingJob.status?.toLowerCase() === 'published' || viewingJob.status?.toLowerCase() === 'active') && (
                <button
                  onClick={async () => {
                    await handleCloseJob(viewingJob.id, viewingJob.title);
                    setViewingJob(null);
                  }}
                  className="inline-flex items-center gap-2 bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 px-5 py-2.5 rounded-lg font-semibold transition-colors"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8V7a4 4 0 00-8 0v4h8z"></path></svg>
                  Đóng tin tuyển dụng
                </button>
              )}
              <button
                onClick={() => setViewingJob(null)}
                className="bg-gray-100 hover:bg-gray-200 text-gray-700 px-6 py-2.5 rounded-lg font-semibold transition-colors"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      </div>
    )}
    </>
  );
}

export default EmployerJobsPage;
