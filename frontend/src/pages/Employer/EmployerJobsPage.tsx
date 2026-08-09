import { FormEvent, useEffect, useState, useRef } from 'react';
import { employerService } from '../../services/employerService';
import type { Company, CompanyLocation, Job } from '../../types/job';
import { Link } from 'react-router-dom';

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
  const [jobs, setJobs] = useState<Job[]>([]);
  const [locations, setLocations] = useState<CompanyLocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const submitTargetRef = useRef<string | undefined>(undefined);

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const jobsPerPage = 7;
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'PENDING_REVIEW' | 'PUBLISHED' | 'CLOSED' | 'EXPIRED' | 'DRAFT' | 'AWAITING_COMPANY'>('ALL');
  const [viewingJob, setViewingJob] = useState<Job | null>(null);

  useEffect(() => {
    setCurrentPage(1);
  }, [statusFilter, searchTerm]);
  const [skillsInput, setSkillsInput] = useState('');
  const [reqsInput, setReqsInput] = useState('');
  const [hasApplications, setHasApplications] = useState(false);
  const [showAiConfig, setShowAiConfig] = useState(false);
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
  }, []);

  async function loadData() {
    setLoading(true);
    setError('');
    try {
      const [compData, jobsData, locsData] = await Promise.all([
        employerService.getCompanyProfile(),
        employerService.getJobs().catch(() => []),
        employerService.getLocations().catch(() => []),
      ]);
      setCompany(compData);
      setJobs(jobsData);
      setLocations(locsData);
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
      description: '',
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
      location: defaultLoc ? defaultLoc.branchName : (company?.location || 'Hà Nội'),
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
    if (!window.confirm(`Bạn có chắc chắn muốn xóa tin tuyển dụng "${title}" không?`)) return;
    try {
      await employerService.deleteJob(id);
      setJobs(jobs.filter((j) => j.id !== id));
      setMessage('Xóa tin tuyển dụng thành công.');
    } catch (err: any) {
      alert('Không thể xóa tin tuyển dụng này.');
    }
  }

  async function handleSubmitForReview(id: string, title: string) {
    if (!window.confirm(`Bạn có chắc chắn muốn gửi duyệt tin tuyển dụng "${title}" cho Admin không?`)) return;
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
      alert(err?.response?.data?.message || 'Không thể gửi duyệt tin tuyển dụng này.');
    }
  }

  async function handleCloseJob(id: string, title: string) {
    if (!window.confirm(`Bạn có chắc chắn muốn ĐÓNG tin tuyển dụng "${title}" (ngừng nhận đơn ứng tuyển) không? Các ứng viên đã nộp đơn sẽ nhận được thông báo.`)) return;
    try {
      const updated = await employerService.closeJob(id);
      setJobs(jobs.map((j) => (j.id === id ? updated : j)));
      setMessage(`Đã đóng tin tuyển dụng "${title}" thành công.`);
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Không thể đóng tin tuyển dụng này.');
    }
  }

  async function handleReopenJob(job: Job) {
    if (!window.confirm(`Bạn có muốn MỞ LẠI tin tuyển dụng "${job.title}" để tiếp tục nhận ứng viên không?`)) return;
    let newDeadline: string | undefined = undefined;
    if (job.deadline) {
      const isExpired = new Date(job.deadline).getTime() < new Date().setHours(0, 0, 0, 0);
      if (isExpired) {
        const input = window.prompt('Tin tuyển dụng này đã hết hạn. Vui lòng nhập hạn nộp hồ sơ mới (YYYY-MM-DD):', new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]);
        if (!input) {
          alert('Bạn phải cập nhật hạn nộp hồ sơ mới để mở lại tin!');
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
      alert(err?.response?.data?.message || 'Không thể mở lại tin tuyển dụng này.');
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!company) return;
    setSaving(true);
    setMessage('');
    setError('');

    const skillsArray = skillsInput
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);

    const reqsArray = reqsInput
      .split('\n')
      .map((r) => r.trim())
      .filter((r) => r.length > 0);

    const statusToUse = submitTargetRef.current || formData.status || 'draft';
    const payload: any = {
      ...formData,
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
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Có lỗi xảy ra khi lưu tin tuyển dụng.');
      }
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <p className="loading">Đang tải dữ liệu tuyển dụng...</p>;
  if (!company) return <div className="content-card"><p className="error">{error || 'Không tìm thấy thông tin công ty.'}</p></div>;

  const isVerified = company.verified || company.verificationStatus?.toLowerCase() === 'verified';
  const hasApprovedJob = jobs.some((j) => {
    const st = j.status?.toLowerCase();
    return st === 'published' || st === 'active' || st === 'closed' || st === 'expired' || st === 'archived';
  });

  return (
    <>
    <section className="content-card">
      <div style={{
        background: 'linear-gradient(135deg, #1b4332 0%, #2d6a4f 100%)',
        color: '#fff',
        padding: '28px',
        borderRadius: '10px',
        marginBottom: '24px',
        boxShadow: '0 4px 15px rgba(0,0,0,0.1)'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <h1 style={{ color: '#fff', margin: '0 0 8px 0', fontSize: '1.8rem', fontWeight: 800 }}>Quản lý & Đăng tin tuyển dụng</h1>
            <p style={{ margin: 0, opacity: 0.9 }}>
              Đăng tin tìm kiếm nhân tài và quản lý các vị trí đang mở tại {company.name}
            </p>
          </div>
          {isVerified && !showForm && (
            <button
              onClick={handleOpenAdd}
              style={{
                background: '#2563eb',
                color: '#fff',
                border: 'none',
                padding: '10px 20px',
                borderRadius: '6px',
                fontWeight: 600,
                fontSize: '0.95rem',
                cursor: 'pointer',
                boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)',
                transition: 'background-color 0.2s',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}
            >
              + Tạo tin tuyển dụng mới
            </button>
          )}
        </div>
      </div>

      {message && <p className="success" style={{ marginBottom: '16px', padding: '12px', borderRadius: '6px', background: '#d1e7dd', color: '#0f5132' }}>{message}</p>}
      {error && <p className="error" style={{ marginBottom: '16px', padding: '12px', borderRadius: '6px', background: '#f8d7da', color: '#842029' }}>{error}</p>}

      {!isVerified ? (
        <div style={{
          border: '2px solid #ffc107',
          background: '#fff9db',
          padding: '24px',
          borderRadius: '10px',
          color: '#856404',
          marginBottom: '24px',
          display: 'flex',
          flexDirection: 'column',
          gap: '16px',
          alignItems: 'flex-start'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span style={{ fontSize: '2rem' }}>🔒</span>
            <div>
              <h3 style={{ margin: '0 0 6px 0', color: '#856404', fontSize: '1.3rem' }}>
                Chức năng Đăng tin tuyển dụng yêu cầu xác thực doanh nghiệp
              </h3>
              <p style={{ margin: 0, lineHeight: 1.5 }}>
                Hiện tại công ty <strong>{company.name}</strong> có trạng thái pháp lý là:{' '}
                <span style={{
                  background: '#ffec99',
                  padding: '2px 8px',
                  borderRadius: '4px',
                  fontWeight: 700,
                  textTransform: 'uppercase'
                }}>
                  {company.verificationStatus || 'Chưa gửi duyệt'}
                </span>
                .<br />
                Theo quy định của hệ thống SJP, <strong>chỉ các doanh nghiệp đã được Admin xác thực pháp lý thành công</strong> mới được phép sử dụng tính năng tạo và đăng tin tuyển dụng.
              </p>
            </div>
          </div>
          <div style={{ display: 'flex', gap: '12px', marginTop: '4px' }}>
            <Link
              to="/employer/verification"
              style={{
                background: '#856404',
                color: '#fff',
                padding: '10px 20px',
                borderRadius: '6px',
                fontWeight: 600,
                textDecoration: 'none',
                display: 'inline-block'
              }}
            >
              → Đến trang Xác thực pháp lý
            </Link>
            <Link
              to="/employer/company-profile"
              style={{
                background: '#e9ecef',
                color: '#495057',
                padding: '10px 20px',
                borderRadius: '6px',
                fontWeight: 600,
                textDecoration: 'none',
                display: 'inline-block'
              }}
            >
              Xem hồ sơ công ty
            </Link>
          </div>
        </div>
      ) : null}
      
      {showForm && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.6)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: '20px'
        }}>
          <div style={{
            background: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: '10px',
            padding: '24px',
            width: '100%',
            maxWidth: '900px',
            maxHeight: '90vh',
            overflowY: 'auto',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)'
          }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', borderBottom: '1px solid #e2e8f0', paddingBottom: '12px' }}>
            <h2 style={{ margin: 0, color: '#0f172a', fontSize: '1.25rem', fontWeight: 700 }}>
              {editingId ? 'Chỉnh sửa tin tuyển dụng' : 'Tạo tin tuyển dụng mới'}
            </h2>
            <button
              type="button"
              onClick={() => setShowForm(false)}
              style={{ background: '#f8fafc', border: '1px solid #cbd5e1', color: '#475569', padding: '6px 14px', borderRadius: '6px', fontWeight: 500, cursor: 'pointer' }}
            >
              Hủy
            </button>
          </div>
          {editingId && (() => {
            const editingJob = jobs.find((j) => j.id === editingId);
            const st = editingJob?.status?.toLowerCase();
            if (st !== 'rejected' && st !== 'awaiting_company') return null;
            const isReportFix = st === 'awaiting_company';
            return (
            <div style={{ background: isReportFix ? '#fff7ed' : '#fef2f2', border: `1px solid ${isReportFix ? '#fed7aa' : '#fecaca'}`, borderLeft: `4px solid ${isReportFix ? '#ea580c' : '#dc2626'}`, padding: '16px 20px', borderRadius: '8px', marginBottom: '24px' }}>
              <div style={{ fontWeight: 600, color: isReportFix ? '#9a3412' : '#991b1b', fontSize: '0.95rem', marginBottom: '8px' }}>
                {isReportFix ? 'Yêu cầu chỉnh sửa từ Admin (tin bị báo cáo)' : 'Phản hồi từ Bộ phận kiểm duyệt'}
              </div>
              <div style={{ background: '#ffffff', padding: '12px 16px', borderRadius: '6px', border: `1px solid ${isReportFix ? '#ffedd5' : '#fee2e2'}`, color: isReportFix ? '#7c2d12' : '#7f1d1d', fontSize: '0.9rem', lineHeight: 1.6, marginBottom: '10px' }}>
                {editingJob?.rejectionReason || 'Vui lòng kiểm tra và hoàn thiện các nội dung chưa đạt yêu cầu trước khi gửi lại.'}
              </div>
              {isReportFix && editingJob?.reportFixDeadline && (
                <div style={{ fontSize: '0.9rem', color: '#9a3412', fontWeight: 600, marginBottom: '8px' }}>
                  Hạn chỉnh sửa: {new Date(editingJob.reportFixDeadline).toLocaleString('vi-VN')}. Quá hạn tin sẽ bị gỡ tự động.
                </div>
              )}
              <div style={{ fontSize: '0.85rem', color: isReportFix ? '#9a3412' : '#991b1b', opacity: 0.9, lineHeight: 1.5 }}>
                Anh/chị vui lòng cập nhật lại thông tin bên dưới theo yêu cầu, sau đó nhấn nút <b>"Lưu & Nộp kiểm duyệt"</b> để gửi lại cho Admin duyệt.
              </div>
            </div>
            );
          })()}

          <form onSubmit={handleSubmit} className="form-grid two">
            <label className="wide">
              Tên vị trí tuyển dụng *
              <input
                required
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                placeholder="Ví dụ: Senior Java Spring Boot Developer, Chuyên viên Marketing..."
              />
            </label>

            <label>
              Loại hình công việc
              <select
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

            <label>
              Hình thức làm việc
              <select
                value={formData.workMode}
                onChange={(e) => setFormData({ ...formData, workMode: e.target.value })}
              >
                <option value="onsite">Làm tại văn phòng (Onsite)</option>
                <option value="remote">Làm từ xa (Remote)</option>
                <option value="hybrid">Kết hợp (Hybrid)</option>
              </select>
            </label>

            <label>
              Cấp bậc kinh nghiệm
              <select
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

            <label>
              Số lượng tuyển
              <input
                type="number"
                min="1"
                required
                value={formData.vacancies}
                onChange={(e) => setFormData({ ...formData, vacancies: parseInt(e.target.value) || 1 })}
              />
            </label>

            <label>
              Hình thức trả lương
              <select
                value={formData.salaryType}
                onChange={(e) => setFormData({ ...formData, salaryType: e.target.value })}
              >
                <option value="range">Trong khoảng (Min - Max)</option>
                <option value="fixed">Mức cố định</option>
                <option value="negotiable">Thỏa thuận (Negotiable)</option>
              </select>
            </label>

            {formData.salaryType !== 'negotiable' ? (
              <>
                <label>
                  Mức lương tối thiểu (VNĐ/tháng)
                  <input
                    type="number"
                    min="0"
                    step="500000"
                    value={formData.salaryMin}
                    onChange={(e) => setFormData({ ...formData, salaryMin: parseInt(e.target.value) || 0 })}
                  />
                </label>
                <label>
                  {formData.salaryType === 'range' ? 'Mức lương tối đa (VNĐ/tháng)' : 'Mức lương cố định (VNĐ/tháng)'}
                  <input
                    type="number"
                    min="0"
                    step="500000"
                    value={formData.salaryMax}
                    onChange={(e) => setFormData({ ...formData, salaryMax: parseInt(e.target.value) || 0 })}
                  />
                </label>
              </>
            ) : (
              <div className="wide" style={{ padding: '10px 14px', background: '#f1f5f9', border: '1px solid #e2e8f0', borderRadius: '6px', color: '#475569', fontSize: '0.875rem', alignSelf: 'center' }}>
                Mức lương sẽ được hiển thị là "Thỏa thuận" đối với ứng viên.
              </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px', gridColumn: 'span 2' }}>
              <label>
                Chọn chi nhánh (Branch)
                <select
                  value={formData.companyLocationId || ''}
                  onChange={(e) => {
                    const loc = locations.find((l) => l.id === e.target.value);
                    setFormData({
                      ...formData,
                      companyLocationId: e.target.value,
                      location: loc ? `${loc.branchName}${loc.address ? ` (${loc.address})` : ''}` : formData.location,
                    });
                  }}
                  style={{ padding: '10px 12px', borderRadius: '6px', border: '1px solid #d1d5db', background: '#fff', fontSize: '0.95rem' }}
                >
                  <option value="">-- Chọn từ chi nhánh công ty --</option>
                  {locations.map((loc) => (
                    <option key={loc.id} value={loc.id}>
                      {loc.branchName} {loc.city ? `(${loc.city})` : ''} {loc.headquarter ? '(Trụ sở chính)' : ''}
                    </option>
                  ))}
                </select>
                {locations.length === 0 && (
                  <span style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '4px', display: 'block' }}>
                    Chưa có chi nhánh nào. <a href="/employer/locations" target="_blank" rel="noreferrer" style={{ color: '#2563eb', fontWeight: 600 }}>+ Quản lý/Thêm chi nhánh</a>
                  </span>
                )}
              </label>

              <label>
                Địa điểm hiển thị trên tin tuyển dụng *
                <input
                  required
                  value={formData.location || ''}
                  onChange={(e) => setFormData({ ...formData, location: e.target.value })}
                  placeholder="Ví dụ: TP. HCM, Hà Nội hoặc địa chỉ cụ thể"
                />
                <span style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '4px', display: 'block' }}>
                  Tự động điền theo chi nhánh được chọn (hoặc tự chỉnh sửa)
                </span>
              </label>
            </div>

            <label>
              Hạn nộp hồ sơ (Deadline) *
              <input
                type="date"
                required
                value={formData.deadline}
                onChange={(e) => setFormData({ ...formData, deadline: e.target.value })}
              />
            </label>

            <label className="wide">
              Thời gian làm việc
              <select
                value={PRESET_WORKING_TIMES.includes(formData.workingTime) ? formData.workingTime : 'CUSTOM'}
                onChange={(e) => {
                  const val = e.target.value;
                  if (val === 'CUSTOM') {
                    setFormData({ ...formData, workingTime: '' });
                  } else {
                    setFormData({ ...formData, workingTime: val });
                  }
                }}
                style={{ padding: '10px 12px', borderRadius: '6px', border: '1px solid #d1d5db', background: '#fff', fontSize: '0.95rem' }}
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
                  style={{ marginTop: '10px' }}
                  value={formData.workingTime}
                  onChange={(e) => setFormData({ ...formData, workingTime: e.target.value })}
                  placeholder="Nhập thời gian làm việc chi tiết (VD: Thứ 2 - Thứ 6 (07:30 - 16:30))"
                />
              )}
            </label>

            <label className="wide">
              Kỹ năng yêu cầu (Nhập các từ khóa ngăn cách bằng dấu phẩy) *
              <input
                required
                value={skillsInput}
                onChange={(e) => setSkillsInput(e.target.value)}
                placeholder="Ví dụ: Java, Spring Boot, MySQL, Docker, ReactJS"
              />
            </label>

            <label className="wide">
              Mô tả công việc (Description) *
              <textarea
                required
                rows={5}
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder="Mô tả chi tiết các trách nhiệm, công việc hàng ngày của ứng viên..."
              />
            </label>

            <label className="wide">
              Yêu cầu công việc (Requirements - Mỗi yêu cầu 1 dòng)
              <textarea
                rows={4}
                value={reqsInput}
                onChange={(e) => setReqsInput(e.target.value)}
                placeholder="Tốt nghiệp đại học chuyên ngành CNTT&#10;Có ít nhất 1 năm kinh nghiệm làm việc với Spring Boot&#10;Tư duy logic tốt, có tinh thần trách nhiệm cao"
              />
            </label>

            <label className="wide">
              Quyền lợi & Phúc lợi (Benefits)
              <textarea
                rows={4}
                value={formData.benefits}
                onChange={(e) => setFormData({ ...formData, benefits: e.target.value })}
                placeholder="Mức lương cạnh tranh, review lương 2 lần/năm&#10;Bảo hiểm chăm sóc sức khỏe toàn diện&#10;Môi trường trẻ trung, năng động"
              />
            </label>

            {/* AI Ranking Configuration Section */}
            <div style={{ width: '100%', marginTop: '32px', paddingTop: '24px', borderTop: '2px solid #e2e8f0', position: 'relative' }}>
                {hasApplications && (
                  <div style={{ position: 'absolute', top: '24px', left: 0, right: 0, bottom: 0, background: 'rgba(255,255,255,0.6)', zIndex: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', backdropFilter: 'blur(1px)' }}>
                    <div style={{ background: '#fff', padding: '16px 24px', borderRadius: '12px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', border: '1px solid #cbd5e1', textAlign: 'center', maxWidth: '400px' }}>
                      <div style={{ fontSize: '1.5rem', marginBottom: '8px' }}>🔒</div>
                      <h4 style={{ margin: '0 0 8px 0', color: '#0f172a', fontSize: '1.05rem' }}>Đã khóa Cấu hình AI</h4>
                      <p style={{ margin: 0, color: '#475569', fontSize: '0.9rem', lineHeight: '1.5' }}>Tin tuyển dụng này đã có người nộp CV. Để đảm bảo công bằng cho tất cả ứng viên, tiêu chí chấm điểm không thể thay đổi nữa.</p>
                    </div>
                  </div>
                )}
                
                <div 
                  style={{ marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '12px' }}
                >
                  <div style={{ background: '#eff6ff', padding: '10px', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.5rem', transition: 'background-color 0.2s' }}>
                    🤖
                  </div>
                  <div style={{ flex: 1 }}>
                    <h3 style={{ margin: 0, color: '#0f172a', fontSize: '1.15rem', fontWeight: 700 }}>Cấu hình AI chấm điểm (Smart Ranking)</h3>
                    <p style={{ margin: '4px 0 0 0', color: '#64748b', fontSize: '0.9rem' }}>Hệ thống tự động đánh giá độ phù hợp của CV với Yêu cầu tuyển dụng.</p>
                  </div>
                  
                  <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', background: formData.rankingConfig?.enabled ? '#dcfce7' : '#f1f5f9', padding: '8px 16px', borderRadius: '20px', border: `1px solid ${formData.rankingConfig?.enabled ? '#86efac' : '#cbd5e1'}`, transition: 'all 0.3s' }}>
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
                      style={{ cursor: 'pointer', width: '18px', height: '18px', accentColor: '#16a34a' }}
                    />
                    <span style={{ fontWeight: 600, color: formData.rankingConfig?.enabled ? '#166534' : '#475569', fontSize: '0.95rem' }}>
                      {formData.rankingConfig?.enabled ? 'Đã Bật AI' : 'Bật AI'}
                    </span>
                  </label>
                  
                  {formData.rankingConfig?.enabled && (
                    <div style={{ fontSize: '1.5rem', color: '#64748b', cursor: 'pointer', transform: showAiConfig ? 'rotate(180deg)' : 'rotate(0deg)', transition: 'transform 0.3s', padding: '8px' }} onClick={() => setShowAiConfig(!showAiConfig)}>
                      ▼
                    </div>
                  )}
                </div>

                {formData.rankingConfig?.enabled && showAiConfig && (
                <div style={{ background: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '12px', overflow: 'hidden', boxShadow: '0 2px 4px rgba(0,0,0,0.02)' }}>
                  
                  {/* Top Bar: Template Selection */}
                  <div style={{ padding: '16px 24px', background: '#f8fafc', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                    <div>
                      <h4 style={{ margin: 0, color: '#1e293b', fontSize: '0.95rem', fontWeight: 600 }}>Mẫu phân bổ trọng số (Template)</h4>
                      <p style={{ margin: '2px 0 0 0', color: '#64748b', fontSize: '0.85rem' }}>Chọn mẫu để tự động điền điểm cho các tiêu chí bên dưới.</p>
                    </div>
                    <select
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
                      style={{ padding: '10px 16px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.95rem', fontWeight: 500, color: '#334155', outline: 'none', cursor: 'pointer', background: '#fff', minWidth: '220px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)' }}
                    >
                      <option value="balanced">⚖️ Cân bằng (Balanced)</option>
                      <option value="skills_focus">🎯 Tập trung Kỹ năng</option>
                      <option value="experience_focus">⏳ Tập trung Kinh nghiệm</option>
                      <option value="project_focus">🚀 Tập trung Dự án</option>
                      <option value="custom">⚙️ Tùy chỉnh (Custom)</option>
                    </select>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))' }}>
                    
                    {/* Left Column: Scoring Criteria */}
                    <div style={{ padding: '24px', borderRight: '1px solid #e2e8f0' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                        <h4 style={{ margin: 0, color: '#1e293b', fontSize: '1rem', fontWeight: 600 }}>
                          Tiêu chí đánh giá
                        </h4>
                        {(() => {
                          const total = Object.entries(formData.rankingConfig?.weights || {})
                            .filter(([k]) => formData.rankingConfig?.enabled_criteria?.includes(k))
                            .reduce((sum, [_, v]) => sum + Number(v), 0);
                          const isError = total !== 100;
                          return (
                            <span style={{ 
                              fontSize: '0.85rem', 
                              padding: '4px 12px', 
                              borderRadius: '20px', 
                              background: isError ? '#fee2e2' : '#dcfce3', 
                              color: isError ? '#ef4444' : '#16a34a',
                              fontWeight: 600,
                              border: `1px solid ${isError ? '#fca5a5' : '#86efac'}`
                            }}>
                              Tổng: {total}% {isError && ' (Cần đúng 100%)'}
                            </span>
                          );
                        })()}
                      </div>
                      
                      {['skills', 'experience', 'projects', 'education', 'certificates'].map(criteria => {
                        const isEnabled = formData.rankingConfig?.enabled_criteria?.includes(criteria) ?? true;
                        
                        return (
                          <div key={criteria} style={{ display: 'flex', alignItems: 'center', marginBottom: '12px', padding: '12px 16px', borderRadius: '8px', background: isEnabled ? '#f8fafc' : '#ffffff', border: `1px solid ${isEnabled ? '#cbd5e1' : '#e2e8f0'}`, transition: 'all 0.2s', opacity: isEnabled ? 1 : 0.6 }}>
                            <label style={{ flex: 1, display: 'flex', alignItems: 'center', gap: '12px', cursor: 'pointer', margin: 0 }}>
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
                                style={{ cursor: 'pointer', width: '18px', height: '18px', accentColor: '#2563eb' }}
                              />
                              <span style={{ fontSize: '0.95rem', color: isEnabled ? '#0f172a' : '#64748b', fontWeight: isEnabled ? 600 : 400 }}>
                                {criteria === 'skills' ? 'Kỹ năng (Skills)' : 
                                 criteria === 'experience' ? 'Kinh nghiệm (Experience)' : 
                                 criteria === 'projects' ? 'Dự án (Projects)' : 
                                 criteria === 'education' ? 'Học vấn (Education)' : 'Chứng chỉ (Certificates)'}
                              </span>
                            </label>
                            
                            {isEnabled && (
                              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
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
                                  style={{ width: '65px', padding: '8px 10px', borderRadius: '6px', border: '1px solid #cbd5e1', textAlign: 'center', outline: 'none', fontWeight: 600, color: '#0f172a' }}
                                />
                                <span style={{ color: '#64748b', fontSize: '0.95rem', fontWeight: 600 }}>%</span>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>

                    {/* Right Column: Mandatory Requirements */}
                    <div style={{ padding: '24px', background: '#fafafa' }}>
                      <h4 style={{ margin: '0 0 12px 0', color: '#1e293b', fontSize: '1rem', fontWeight: 600 }}>
                        Yêu cầu Bắt buộc (Hard Filters)
                      </h4>
                      <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '24px', lineHeight: '1.6' }}>
                        Hệ thống sẽ đánh dấu Ứng viên là <strong style={{ color: '#ef4444', fontWeight: 600 }}>"Thiếu yêu cầu"</strong> nếu CV không đáp ứng các tiêu chí này. Hãy cẩn trọng để không loại nhầm ứng viên.
                      </p>

                      <div style={{ marginBottom: '24px' }}>
                        <label style={{ display: 'block', fontWeight: 600, marginBottom: '8px', fontSize: '0.9rem', color: '#334155' }}>
                          ⚡ Kỹ năng bắt buộc
                        </label>
                        <p style={{ margin: '0 0 8px 0', fontSize: '0.8rem', color: '#94a3b8' }}>Cách nhau bằng dấu phẩy (,)</p>
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
                          style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', border: '1px solid #cbd5e1', outline: 'none', fontSize: '0.95rem', boxShadow: 'inset 0 1px 2px rgba(0,0,0,0.02)', transition: 'border-color 0.2s' }}
                        />
                      </div>
                      <div>
                        <label style={{ display: 'block', fontWeight: 600, marginBottom: '8px', fontSize: '0.9rem', color: '#334155' }}>
                          ⏳ Kinh nghiệm tối thiểu
                        </label>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
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
                            style={{ width: '130px', padding: '12px 16px', borderRadius: '8px', border: '1px solid #cbd5e1', outline: 'none', fontSize: '0.95rem', boxShadow: 'inset 0 1px 2px rgba(0,0,0,0.02)' }}
                          />
                          <span style={{ color: '#64748b', fontSize: '0.95rem', fontWeight: 500 }}>Năm</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                )}
              </div>

            <div className="wide" style={{ display: 'flex', gap: '12px', marginTop: '20px', flexWrap: 'wrap', borderTop: '1px solid #e2e8f0', paddingTop: '16px' }}>
              {(() => {
                const totalWeight = Object.entries(formData.rankingConfig?.weights || {})
                  .filter(([k]) => formData.rankingConfig?.enabled_criteria?.includes(k))
                  .reduce((sum, [_, v]) => sum + Number(v), 0);
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
                        style={{
                          background: (saving || isInvalidConfig) ? '#94a3b8' : '#2563eb',
                          color: '#fff',
                          border: 'none',
                          padding: '10px 24px',
                          borderRadius: '6px',
                          fontWeight: 600,
                          fontSize: '0.9rem',
                          cursor: (saving || isInvalidConfig) ? 'not-allowed' : 'pointer',
                          boxShadow: (saving || isInvalidConfig) ? 'none' : '0 2px 4px rgba(37, 99, 235, 0.2)',
                          transition: 'background-color 0.2s'
                        }}
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
                          style={{
                            background: '#f1f5f9',
                            color: '#334155',
                            border: '1px solid #cbd5e1',
                            padding: '10px 18px',
                            borderRadius: '6px',
                            fontWeight: 600,
                            fontSize: '0.9rem',
                            cursor: (saving || isInvalidConfig) ? 'not-allowed' : 'pointer',
                            transition: 'all 0.2s',
                            opacity: isInvalidConfig ? 0.6 : 1
                          }}
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
                            style={{
                              background: (saving || isInvalidConfig) ? '#94a3b8' : '#059669',
                              color: '#fff',
                              border: 'none',
                              padding: '10px 22px',
                              borderRadius: '6px',
                              fontWeight: 600,
                              fontSize: '0.9rem',
                              cursor: (saving || isInvalidConfig) ? 'not-allowed' : 'pointer',
                              boxShadow: (saving || isInvalidConfig) ? 'none' : '0 2px 4px rgba(5, 150, 105, 0.2)',
                              transition: 'background-color 0.2s'
                            }}
                          >
                            {saving ? 'Đang xử lý...' : '🚀 Đăng tin ngay (Miễn kiểm duyệt)'}
                          </button>
                        ) : (
                          <button
                            type="submit"
                            disabled={saving || isInvalidConfig}
                            onClick={() => {
                              submitTargetRef.current = 'pending_review';
                              setFormData((prev) => ({ ...prev, status: 'pending_review' }));
                            }}
                            style={{
                              background: (saving || isInvalidConfig) ? '#94a3b8' : '#2563eb',
                              color: '#fff',
                              border: 'none',
                              padding: '10px 22px',
                              borderRadius: '6px',
                              fontWeight: 600,
                              fontSize: '0.9rem',
                              cursor: (saving || isInvalidConfig) ? 'not-allowed' : 'pointer',
                              boxShadow: (saving || isInvalidConfig) ? 'none' : '0 2px 4px rgba(37, 99, 235, 0.2)',
                              transition: 'background-color 0.2s'
                            }}
                          >
                            {saving ? 'Đang xử lý...' : 'Lưu & Nộp kiểm duyệt'}
                          </button>
                        )}
                      </>
                    )}
                    <button
                      type="button"
                      onClick={() => setShowForm(false)}
                      style={{
                        background: 'transparent',
                        color: '#64748b',
                        border: 'none',
                        padding: '10px 16px',
                        borderRadius: '6px',
                        fontWeight: 500,
                        fontSize: '0.9rem',
                        cursor: 'pointer'
                      }}
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
      )}

      <div>
        <div style={{ background: '#f8fafc', padding: '20px', borderRadius: '10px', border: '1px solid #e2e8f0', marginBottom: '24px' }}>
          <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', alignItems: 'center', marginBottom: '16px' }}>
            <div style={{ flex: '1 1 300px', position: 'relative' }}>
              <input
                type="text"
                placeholder="🔍 Tìm kiếm theo tiêu đề vị trí, địa điểm, kỹ năng..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
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
            {searchTerm && (
              <button
                type="button"
                onClick={() => setSearchTerm('')}
                style={{ background: 'transparent', border: 'none', color: '#64748b', cursor: 'pointer', fontSize: '0.875rem' }}
              >
                ✕ Xóa tìm kiếm
              </button>
            )}
          </div>

          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            {[
              { key: 'ALL', label: 'Tất cả', count: jobs.length },
              { key: 'AWAITING_COMPANY', label: 'Tin vi phạm cần sửa', count: jobs.filter(j => (j.status?.toUpperCase() || '') === 'AWAITING_COMPANY').length },
              { key: 'PENDING_REVIEW', label: 'Chờ duyệt', count: jobs.filter(j => (j.status?.toUpperCase() || '') === 'PENDING_REVIEW').length },
              { key: 'PUBLISHED', label: 'Đang tuyển', count: jobs.filter(j => (j.status?.toUpperCase() || '') === 'PUBLISHED' || (j.status?.toUpperCase() || '') === 'ACTIVE').length },
              { key: 'CLOSED', label: 'Đã đóng', count: jobs.filter(j => (j.status?.toUpperCase() || '') === 'CLOSED').length },
              { key: 'EXPIRED', label: 'Hết hạn', count: jobs.filter(j => (j.status?.toUpperCase() || '') === 'EXPIRED').length },
              { key: 'DRAFT', label: 'Bản nháp / Yêu cầu sửa', count: jobs.filter(j => (j.status?.toUpperCase() || '') === 'DRAFT' || (j.status?.toUpperCase() || '') === 'REJECTED').length },
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
          const filteredJobs = jobs.filter((job) => {
            const st = job.status?.toUpperCase() || 'DRAFT';
            if (statusFilter !== 'ALL') {
              if (statusFilter === 'PUBLISHED' && st !== 'PUBLISHED' && st !== 'ACTIVE') return false;
              if (statusFilter === 'PENDING_REVIEW' && st !== 'PENDING_REVIEW') return false;
              if (statusFilter === 'AWAITING_COMPANY' && st !== 'AWAITING_COMPANY') return false;
              if (statusFilter === 'CLOSED' && st !== 'CLOSED') return false;
              if (statusFilter === 'EXPIRED' && st !== 'EXPIRED') return false;
              if (statusFilter === 'DRAFT' && st !== 'DRAFT' && st !== 'REJECTED') return false;
            }
            if (searchTerm.trim()) {
              const q = searchTerm.toLowerCase();
              const matchTitle = job.title?.toLowerCase().includes(q);
              const matchLocation = job.location?.toLowerCase().includes(q);
              const matchSkills = job.skills?.some((s) => s.toLowerCase().includes(q));
              if (!matchTitle && !matchLocation && !matchSkills) return false;
            }
            return true;
          });

          const totalPages = Math.ceil(filteredJobs.length / jobsPerPage);
          const indexOfLastJob = currentPage * jobsPerPage;
          const indexOfFirstJob = indexOfLastJob - jobsPerPage;
          const currentJobs = filteredJobs.slice(indexOfFirstJob, indexOfLastJob);

          return (
            <>
              <h2 style={{ fontSize: '1.3rem', color: '#1e293b', marginBottom: '16px' }}>
                Danh sách tin tuyển dụng ({filteredJobs.length}/{jobs.length})
              </h2>

              {filteredJobs.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '48px 24px', background: '#f8fafc', borderRadius: '8px', border: '1px dashed #cbd5e1' }}>
                  <p style={{ color: '#64748b', fontSize: '1.05rem', margin: '0 0 16px 0' }}>
                    {jobs.length === 0 ? 'Công ty chưa có tin tuyển dụng nào được đăng trên hệ thống.' : 'Không tìm thấy tin tuyển dụng nào phù hợp với điều kiện lọc.'}
                  </p>
                  {jobs.length === 0 && isVerified && (
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
                    {currentJobs.map((job) => {
                    const st = job.status?.toLowerCase() || 'draft';
                    const statusBg = st === 'published' || st === 'active' ? '#ecfdf5' : st === 'pending_review' ? '#eff6ff' : st === 'awaiting_company' ? '#fff7ed' : st === 'rejected' ? '#fef2f2' : st === 'expired' ? '#fef3c7' : st === 'draft' ? '#f8fafc' : '#f1f5f9';
                    const statusColor = st === 'published' || st === 'active' ? '#047857' : st === 'pending_review' ? '#1d4ed8' : st === 'awaiting_company' ? '#c2410c' : st === 'rejected' ? '#b91c1c' : st === 'expired' ? '#b45309' : st === 'draft' ? '#475569' : '#64748b';
                    const statusBorder = st === 'published' || st === 'active' ? '#a7f3d0' : st === 'pending_review' ? '#bfdbfe' : st === 'awaiting_company' ? '#fed7aa' : st === 'rejected' ? '#fecaca' : st === 'expired' ? '#fde68a' : st === 'draft' ? '#cbd5e1' : '#e2e8f0';
                    const statusDot = st === 'published' || st === 'active' ? '#10b981' : st === 'pending_review' ? '#3b82f6' : st === 'awaiting_company' ? '#ea580c' : st === 'rejected' ? '#ef4444' : st === 'expired' ? '#f59e0b' : st === 'draft' ? '#94a3b8' : '#64748b';
                    const statusLabel = st === 'published' || st === 'active' ? 'Đang tuyển' : st === 'pending_review' ? 'Chờ kiểm duyệt' : st === 'awaiting_company' ? 'Chờ công ty kiểm tra' : st === 'rejected' ? 'Yêu cầu chỉnh sửa' : st === 'expired' ? 'Hết hạn' : st === 'draft' ? 'Bản nháp' : 'Đã đóng';

                    return (
                      <div key={job.id} style={{
                        border: '1px solid #e2e8f0',
                        borderRadius: '8px',
                        padding: '16px',
                        background: '#ffffff',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-start',
                        flexWrap: 'wrap',
                        gap: '12px',
                        transition: 'border-color 0.2s, box-shadow 0.2s'
                      }}>
                        <div style={{ flex: '1 1 420px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px', flexWrap: 'wrap' }}>
                            <h3 style={{ margin: 0, fontSize: '1.1rem', color: '#0f172a', fontWeight: 700 }}>
                              {job.title}
                            </h3>
                            <span style={{
                              background: statusBg,
                              color: statusColor,
                              border: `1px solid ${statusBorder}`,
                              padding: '4px 12px',
                              borderRadius: '20px',
                              fontSize: '0.8rem',
                              fontWeight: 600,
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '6px'
                            }}>
                              <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: statusDot }}></span>
                              {statusLabel}
                            </span>
                          </div>

                          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', color: '#64748b', fontSize: '0.875rem', marginBottom: '14px', alignItems: 'center' }}>
                            <span>Địa điểm: <strong style={{ color: '#334155' }}>{job.location || 'Hà Nội'}</strong></span>
                            <span style={{ color: '#cbd5e1' }}>•</span>
                            <span>Mức lương: <strong style={{ color: '#059669' }}>{job.salaryType === 'negotiable' ? 'Thỏa thuận' : `${job.salaryMin ? job.salaryMin.toLocaleString() : 0} - ${job.salaryMax ? job.salaryMax.toLocaleString() : 0} VNĐ`}</strong></span>
                            <span style={{ color: '#cbd5e1' }}>•</span>
                            <span>Số lượng: <strong style={{ color: '#334155' }}>{job.vacancies || 1}</strong></span>
                            <span style={{ color: '#cbd5e1' }}>•</span>
                            <span>Lượt xem: <strong style={{ color: '#334155' }}>{job.viewsCount || 0}</strong></span>
                            <span style={{ color: '#cbd5e1' }}>•</span>
                            <Link to={`/employer/jobs/${job.id}/applications`} style={{
                              background: '#eff6ff',
                              color: '#1d4ed8',
                              padding: '3px 10px',
                              borderRadius: '6px',
                              border: '1px solid #bfdbfe',
                              fontWeight: 600,
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px',
                              textDecoration: 'none'
                            }}>
                              📁 Đơn ứng tuyển: <strong style={{ fontSize: '0.95rem' }}>{job.applicationsCount || 0}</strong>
                            </Link>
                            {job.deadline && (
                              <>
                                <span style={{ color: '#cbd5e1' }}>•</span>
                                <span>Hạn nộp: <strong style={{ color: '#334155' }}>{new Date(job.deadline).toLocaleDateString('vi-VN')}</strong></span>
                              </>
                            )}
                          </div>

                    {job.skills && job.skills.length > 0 && (
                      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        {job.skills.map((s, idx) => (
                          <span key={idx} style={{ background: '#f1f5f9', color: '#334155', padding: '4px 10px', borderRadius: '6px', fontSize: '0.75rem', fontWeight: 600, border: '1px solid #e2e8f0' }}>
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

                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
                    <Link
                      to={`/employer/jobs/${job.id}/applications`}
                      style={{
                        background: '#eff6ff',
                        border: '1px solid #bfdbfe',
                        color: '#1d4ed8',
                        padding: '8px 16px',
                        borderRadius: '6px',
                        fontWeight: 600,
                        fontSize: '0.875rem',
                        textDecoration: 'none',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                        transition: 'all 0.2s'
                      }}
                    >
                      👥 Xem ứng viên ({job.applicationsCount || 0})
                    </Link>
                    {isVerified && st === 'draft' && (
                      <button
                        onClick={() => handleSubmitForReview(job.id, job.title)}
                        style={{
                          background: hasApprovedJob ? '#059669' : '#2563eb',
                          border: 'none',
                          color: '#fff',
                          padding: '8px 16px',
                          borderRadius: '6px',
                          fontWeight: 600,
                          fontSize: '0.875rem',
                          cursor: 'pointer',
                          boxShadow: hasApprovedJob ? '0 2px 4px rgba(5, 150, 105, 0.15)' : '0 2px 4px rgba(37, 99, 235, 0.15)',
                          transition: 'background-color 0.2s'
                        }}
                      >
                        {hasApprovedJob ? '🚀 Đăng tin ngay (Miễn kiểm duyệt)' : 'Nộp kiểm duyệt'}
                      </button>
                    )}
                    {isVerified && (st === 'rejected' || st === 'awaiting_company') && (
                      <button
                        onClick={() => handleOpenEdit(job)}
                        style={{
                          background: st === 'awaiting_company' ? '#ea580c' : '#dc2626',
                          border: 'none',
                          color: '#fff',
                          padding: '8px 16px',
                          borderRadius: '6px',
                          fontWeight: 600,
                          fontSize: '0.875rem',
                          cursor: 'pointer',
                          boxShadow: st === 'awaiting_company' ? '0 2px 4px rgba(234, 88, 12, 0.15)' : '0 2px 4px rgba(220, 38, 38, 0.15)',
                          transition: 'background-color 0.2s'
                        }}
                      >
                        {st === 'awaiting_company' ? 'Cập nhật & Gửi lại duyệt' : 'Cập nhật & Nộp lại'}
                      </button>
                    )}
                    {isVerified && st !== 'rejected' && st !== 'awaiting_company' && (
                      <button
                        onClick={() => handleOpenEdit(job)}
                        style={{
                          background: '#f8fafc',
                          border: '1px solid #cbd5e1',
                          color: '#334155',
                          padding: '8px 16px',
                          borderRadius: '6px',
                          fontWeight: 600,
                          fontSize: '0.875rem',
                          cursor: 'pointer',
                          transition: 'all 0.2s'
                        }}
                      >
                        Chỉnh sửa
                      </button>
                    )}
                    {isVerified && (st === 'published' || st === 'active') && (
                      <button
                        onClick={() => handleCloseJob(job.id, job.title)}
                        style={{
                          background: '#fffbeb',
                          border: '1px solid #fde68a',
                          color: '#b45309',
                          padding: '8px 14px',
                          borderRadius: '6px',
                          fontWeight: 600,
                          fontSize: '0.875rem',
                          cursor: 'pointer',
                          transition: 'all 0.2s'
                        }}
                      >
                        🔒 Đóng tin
                      </button>
                    )}
                    {isVerified && (st === 'closed' || st === 'expired') && (
                      <button
                        onClick={() => handleReopenJob(job)}
                        style={{
                          background: '#ecfdf5',
                          border: '1px solid #6ee7b7',
                          color: '#047857',
                          padding: '8px 14px',
                          borderRadius: '6px',
                          fontWeight: 600,
                          fontSize: '0.875rem',
                          cursor: 'pointer',
                          transition: 'all 0.2s'
                        }}
                      >
                        🔓 Mở lại tin
                      </button>
                    )}
                    <button
                      onClick={() => setViewingJob(job)}
                      style={{
                        background: '#ffffff',
                        border: '1px solid #e2e8f0',
                        color: '#475569',
                        padding: '8px 14px',
                        borderRadius: '6px',
                        fontWeight: 600,
                        fontSize: '0.875rem',
                        textDecoration: 'none',
                        transition: 'all 0.2s',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '4px'
                      }}
                    >
                      👀 Xem chi tiết
                    </button>
                    <button
                      onClick={() => handleDelete(job.id, job.title)}
                      style={{
                        background: '#ffffff',
                        border: '1px solid #fecaca',
                        color: '#ef4444',
                        padding: '8px 14px',
                        borderRadius: '6px',
                        fontWeight: 500,
                        fontSize: '0.875rem',
                        cursor: 'pointer',
                        transition: 'all 0.2s'
                      }}
                    >
                      Xóa
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
      <div style={{
        position: 'fixed',
        top: 0, left: 0, right: 0, bottom: 0,
        backgroundColor: 'rgba(0,0,0,0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
        padding: '20px'
      }}>
        <div style={{
          backgroundColor: '#fff',
          borderRadius: '12px',
          width: '100%',
          maxWidth: '800px',
          maxHeight: '90vh',
          overflowY: 'auto',
          boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1), 0 10px 10px -5px rgba(0,0,0,0.04)',
          position: 'relative'
        }}>
          <div style={{ padding: '20px 24px', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center', position: 'sticky', top: 0, background: '#fff', zIndex: 1 }}>
            <h2 style={{ margin: 0, fontSize: '1.25rem', color: '#0f172a' }}>Chi tiết tin tuyển dụng</h2>
            <button onClick={() => setViewingJob(null)} style={{ background: '#f1f5f9', border: 'none', padding: '6px 12px', borderRadius: '6px', cursor: 'pointer', color: '#475569', fontWeight: 600 }}>✕ Đóng</button>
          </div>

          <div style={{ padding: '24px' }}>
            <h3 style={{ fontSize: '1.5rem', margin: '0 0 16px 0', color: '#1e293b' }}>{viewingJob.title}</h3>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginBottom: '24px', background: '#f8fafc', padding: '16px', borderRadius: '8px' }}>
              <div>
                <div style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '4px' }}>Mức lương</div>
                <div style={{ fontWeight: 600, color: '#059669' }}>
                  {viewingJob.salaryType === 'negotiable' ? 'Thỏa thuận' : `${viewingJob.salaryMin?.toLocaleString() || 0} - ${viewingJob.salaryMax?.toLocaleString() || 0} VNĐ`}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '4px' }}>Địa điểm</div>
                <div style={{ fontWeight: 600, color: '#334155' }}>{viewingJob.location || 'Hà Nội'}</div>
              </div>
              <div>
                <div style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '4px' }}>Kinh nghiệm</div>
                <div style={{ fontWeight: 600, color: '#334155' }}>{viewingJob.experienceLevel}</div>
              </div>
              <div>
                <div style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '4px' }}>Hình thức</div>
                <div style={{ fontWeight: 600, color: '#334155' }}>{viewingJob.workMode} / {viewingJob.jobType}</div>
              </div>
              <div>
                <div style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '4px' }}>Số lượng</div>
                <div style={{ fontWeight: 600, color: '#334155' }}>{viewingJob.vacancies} người</div>
              </div>
              <div>
                <div style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '4px' }}>Hạn nộp hồ sơ</div>
                <div style={{ fontWeight: 600, color: '#dc2626' }}>{viewingJob.deadline ? new Date(viewingJob.deadline).toLocaleDateString('vi-VN') : 'Không giới hạn'}</div>
              </div>
            </div>

            <div style={{ marginBottom: '24px' }}>
              <h4 style={{ fontSize: '1.1rem', margin: '0 0 12px 0', color: '#1e293b', borderBottom: '2px solid #e2e8f0', paddingBottom: '8px' }}>Mô tả công việc</h4>
              <div style={{ whiteSpace: 'pre-wrap', color: '#475569', lineHeight: 1.6, fontSize: '0.95rem' }}>
                {viewingJob.description}
              </div>
            </div>

            <div style={{ marginBottom: '24px' }}>
              <h4 style={{ fontSize: '1.1rem', margin: '0 0 12px 0', color: '#1e293b', borderBottom: '2px solid #e2e8f0', paddingBottom: '8px' }}>Yêu cầu công việc</h4>
              <div style={{ whiteSpace: 'pre-wrap', color: '#475569', lineHeight: 1.6, fontSize: '0.95rem' }}>
                {viewingJob.requirements?.join('\n') || viewingJob.skills?.join(', ')}
              </div>
            </div>

            <div style={{ marginBottom: '24px' }}>
              <h4 style={{ fontSize: '1.1rem', margin: '0 0 12px 0', color: '#1e293b', borderBottom: '2px solid #e2e8f0', paddingBottom: '8px' }}>Quyền lợi</h4>
              <div style={{ whiteSpace: 'pre-wrap', color: '#475569', lineHeight: 1.6, fontSize: '0.95rem' }}>
                {viewingJob.benefits || 'Theo quy định của công ty'}
              </div>
            </div>

            <div style={{ marginBottom: '24px' }}>
              <h4 style={{ fontSize: '1.1rem', margin: '0 0 12px 0', color: '#1e293b', borderBottom: '2px solid #e2e8f0', paddingBottom: '8px' }}>Kỹ năng chuyên môn</h4>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginTop: '8px' }}>
                {viewingJob.skills?.map((s, idx) => (
                  <span key={idx} style={{ background: '#f1f5f9', color: '#334155', padding: '6px 12px', borderRadius: '20px', fontSize: '0.85rem', fontWeight: 500, border: '1px solid #e2e8f0' }}>
                    {s}
                  </span>
                ))}
              </div>
            </div>

            <div style={{ marginBottom: '12px' }}>
              <h4 style={{ fontSize: '1.1rem', margin: '0 0 12px 0', color: '#1e293b', borderBottom: '2px solid #e2e8f0', paddingBottom: '8px' }}>Thời gian làm việc</h4>
              <div style={{ color: '#475569', fontSize: '0.95rem' }}>
                {viewingJob.workingTime || 'Giờ hành chính'}
              </div>
            </div>
          </div>
        </div>
      </div>
    )}
    </>
  );
}

export default EmployerJobsPage;
