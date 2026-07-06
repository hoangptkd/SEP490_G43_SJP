import { FormEvent, useEffect, useState, useRef } from 'react';
import { employerService } from '../../services/employerService';
import type { Company, CompanyLocation, Job } from '../../types/job';
import { Link } from 'react-router-dom';

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
  const [skillsInput, setSkillsInput] = useState('');
  const [reqsInput, setReqsInput] = useState('');
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
      companyLocationId: job.companyLocationId || '',
      status: job.status?.toLowerCase() || 'draft',
    });
    submitTargetRef.current = job.status?.toLowerCase() || 'draft';
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
      setMessage(`Đã gửi duyệt tin "${title}" thành công. Vui lòng chờ Admin kiểm duyệt.`);
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Không thể gửi duyệt tin tuyển dụng này.');
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
        setMessage('Cập nhật tin tuyển dụng thành công!');
      } else {
        const created = await employerService.createJob(payload);
        setJobs([created, ...jobs]);
        setMessage('Đăng tin tuyển dụng mới thành công!');
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

  return (
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
                background: '#52b788',
                color: '#fff',
                border: 'none',
                padding: '10px 20px',
                borderRadius: '8px',
                fontWeight: 700,
                fontSize: '1rem',
                cursor: 'pointer',
                boxShadow: '0 4px 10px rgba(0,0,0,0.2)',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}
            >
              <span>➕</span> Đăng tin tuyển dụng mới
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
      ) : showForm ? (
        <div style={{
          background: '#f8fafc',
          border: '1px solid #e2e8f0',
          borderRadius: '10px',
          padding: '24px',
          marginBottom: '30px'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', borderBottom: '1px solid #e2e8f0', paddingBottom: '12px' }}>
            <h2 style={{ margin: 0, color: '#1e293b', fontSize: '1.4rem' }}>
              {editingId ? '✏️ Chỉnh sửa tin tuyển dụng' : '➕ Đăng tin tuyển dụng mới'}
            </h2>
            <button
              type="button"
              onClick={() => setShowForm(false)}
              style={{ background: 'transparent', border: '1px solid #cbd5e1', color: '#64748b', padding: '6px 12px', borderRadius: '6px', cursor: 'pointer' }}
            >
              ✕ Hủy bỏ
            </button>
          </div>

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
              <div className="wide" style={{ padding: '10px 14px', background: '#e2e8f0', borderRadius: '6px', color: '#475569', fontSize: '0.9rem', alignSelf: 'center' }}>
                ℹ️ Mức lương sẽ hiển thị là "Thỏa thuận" đối với ứng viên.
              </div>
            )}

            <label>
              Địa điểm / Chi nhánh làm việc
              {locations.length > 0 ? (
                <select
                  value={formData.companyLocationId}
                  onChange={(e) => {
                    const loc = locations.find((l) => l.id === e.target.value);
                    setFormData({
                      ...formData,
                      companyLocationId: e.target.value,
                      location: loc ? loc.branchName : formData.location,
                    });
                  }}
                >
                  <option value="">-- Chọn chi nhánh --</option>
                  {locations.map((loc) => (
                    <option key={loc.id} value={loc.id}>
                      {loc.branchName} ({loc.city || 'Chưa rõ thành phố'}) {loc.headquarter ? '★ HQ' : ''}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  value={formData.location}
                  onChange={(e) => setFormData({ ...formData, location: e.target.value })}
                  placeholder="Nhập địa điểm làm việc"
                />
              )}
            </label>

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
              <input
                value={formData.workingTime}
                onChange={(e) => setFormData({ ...formData, workingTime: e.target.value })}
                placeholder="Ví dụ: Thứ 2 - Thứ 6 (08:00 - 17:30)"
              />
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

            <label>
              Trạng thái tin đăng
              <select
                value={formData.status}
                onChange={(e) => {
                  setFormData({ ...formData, status: e.target.value });
                  submitTargetRef.current = e.target.value;
                }}
              >
                <option value="draft">🟡 Bản nháp (Draft)</option>
                <option value="pending_review">⏳ Gửi duyệt tin (Pending Review)</option>
                {formData.status === 'published' || formData.status === 'active' ? (
                  <option value="published">🟢 Đang hiển thị (Published)</option>
                ) : null}
              </select>
            </label>

            <div className="wide" style={{ display: 'flex', gap: '12px', marginTop: '16px', flexWrap: 'wrap' }}>
              <button
                type="submit"
                disabled={saving}
                onClick={() => {
                  submitTargetRef.current = 'draft';
                  setFormData((prev) => ({ ...prev, status: 'draft' }));
                }}
                style={{
                  background: '#64748b',
                  color: '#fff',
                  border: 'none',
                  padding: '12px 20px',
                  borderRadius: '6px',
                  fontWeight: 700,
                  cursor: saving ? 'wait' : 'pointer'
                }}
              >
                {saving ? '⏳ Đang lưu...' : '💾 Lưu bản nháp'}
              </button>
              <button
                type="submit"
                disabled={saving}
                onClick={() => {
                  submitTargetRef.current = 'pending_review';
                  setFormData((prev) => ({ ...prev, status: 'pending_review' }));
                }}
                style={{
                  background: '#0d6efd',
                  color: '#fff',
                  border: 'none',
                  padding: '12px 24px',
                  borderRadius: '6px',
                  fontWeight: 700,
                  cursor: saving ? 'wait' : 'pointer',
                  boxShadow: '0 4px 6px rgba(13, 110, 253, 0.25)'
                }}
              >
                {saving ? '⏳ Đang lưu...' : '🚀 Lưu & Gửi duyệt ngay'}
              </button>
              {formData.status === 'published' || formData.status === 'active' ? (
                <button
                  type="submit"
                  disabled={saving}
                  onClick={() => {
                    submitTargetRef.current = 'published';
                    setFormData((prev) => ({ ...prev, status: 'published' }));
                  }}
                  style={{
                    background: '#245d43',
                    color: '#fff',
                    border: 'none',
                    padding: '12px 20px',
                    borderRadius: '6px',
                    fontWeight: 700,
                    cursor: saving ? 'wait' : 'pointer'
                  }}
                >
                  {saving ? '⏳ Đang lưu...' : '✅ Lưu tin đang hiển thị'}
                </button>
              ) : null}
              <button
                type="button"
                onClick={() => setShowForm(false)}
                style={{
                  background: '#e2e8f0',
                  color: '#475569',
                  border: 'none',
                  padding: '12px 20px',
                  borderRadius: '6px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Hủy
              </button>
            </div>
          </form>
        </div>
      ) : null}

      <div>
        <h2 style={{ fontSize: '1.3rem', color: '#1e293b', marginBottom: '16px' }}>
          Danh sách tin tuyển dụng ({jobs.length})
        </h2>

        {jobs.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px', background: '#f8fafc', borderRadius: '8px', border: '1px dashed #cbd5e1' }}>
            <p style={{ color: '#64748b', fontSize: '1.1rem', margin: '0 0 16px 0' }}>
              Công ty chưa có tin tuyển dụng nào được đăng.
            </p>
            {isVerified && (
              <button
                onClick={handleOpenAdd}
                style={{ background: '#245d43', color: '#fff', border: 'none', padding: '10px 20px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer' }}
              >
                ➕ Đăng tin tuyển dụng đầu tiên
              </button>
            )}
          </div>
        ) : (
          <div style={{ display: 'grid', gap: '16px' }}>
            {jobs.map((job) => {
              const st = job.status?.toLowerCase() || 'draft';
              const statusBg = st === 'published' || st === 'active' ? '#d1e7dd' : st === 'pending_review' ? '#cff4fc' : st === 'rejected' ? '#f8d7da' : st === 'draft' ? '#fff3cd' : '#e2e3e5';
              const statusColor = st === 'published' || st === 'active' ? '#0f5132' : st === 'pending_review' ? '#055160' : st === 'rejected' ? '#842029' : st === 'draft' ? '#856404' : '#41464b';
              const statusLabel = st === 'published' || st === 'active' ? '🟢 Đang công khai' : st === 'pending_review' ? '⏳ Đang chờ duyệt' : st === 'rejected' ? '❌ Bị từ chối duyệt' : st === 'draft' ? '🟡 Bản nháp' : '⚫ Đã đóng';

              return (
                <div key={job.id} style={{
                  border: '1px solid #e2e8f0',
                  borderRadius: '8px',
                  padding: '20px',
                  background: '#fff',
                  boxShadow: '0 2px 4px rgba(0,0,0,0.03)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'flex-start',
                  flexWrap: 'wrap',
                  gap: '16px',
                  transition: 'border-color 0.2s'
                }}>
                  <div style={{ flex: '1 1 400px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px', flexWrap: 'wrap' }}>
                      <h3 style={{ margin: 0, fontSize: '1.25rem', color: '#0f172a' }}>
                        {job.title}
                      </h3>
                      <span style={{
                        background: statusBg,
                        color: statusColor,
                        padding: '4px 10px',
                        borderRadius: '20px',
                        fontSize: '0.8rem',
                        fontWeight: 700
                      }}>
                        {statusLabel}
                      </span>
                    </div>

                    <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', color: '#64748b', fontSize: '0.9rem', marginBottom: '12px' }}>
                      <span>📍 {job.location || 'Hà Nội'}</span>
                      <span>💰 {job.salaryType === 'negotiable' ? 'Thỏa thuận' : `${job.salaryMin ? job.salaryMin.toLocaleString() : 0} - ${job.salaryMax ? job.salaryMax.toLocaleString() : 0} VNĐ`}</span>
                      <span>👥 Tuyển {job.vacancies || 1} người</span>
                      <span>👁️ {job.viewsCount || 0} lượt xem</span>
                      {job.deadline && <span>⏰ Hạn nộp: {new Date(job.deadline).toLocaleDateString('vi-VN')}</span>}
                    </div>

                    {job.skills && job.skills.length > 0 && (
                      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        {job.skills.map((s, idx) => (
                          <span key={idx} style={{ background: '#f1f5f9', color: '#334155', padding: '2px 8px', borderRadius: '4px', fontSize: '0.8rem', fontWeight: 600 }}>
                            {s}
                          </span>
                        ))}
                      </div>
                    )}

                    {st === 'rejected' && (
                      <div style={{ marginTop: '12px', background: '#fff8f6', border: '1px solid #ffd8d0', borderLeft: '4px solid #e11d48', padding: '10px 14px', borderRadius: '6px', color: '#9f1239', fontSize: '0.9rem' }}>
                        <div style={{ fontWeight: 700, marginBottom: '4px' }}>⚠️ Tin tuyển dụng bị từ chối duyệt:</div>
                        <div>{job.rejectionReason || 'Vui lòng chỉnh sửa lại nội dung theo yêu cầu và gửi duyệt lại.'}</div>
                      </div>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
                    {isVerified && (st === 'draft' || st === 'rejected') && (
                      <button
                        onClick={() => handleSubmitForReview(job.id, job.title)}
                        style={{
                          background: '#0d6efd',
                          border: 'none',
                          color: '#fff',
                          padding: '8px 14px',
                          borderRadius: '6px',
                          fontWeight: 700,
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px',
                          boxShadow: '0 2px 4px rgba(13, 110, 253, 0.2)'
                        }}
                      >
                        🚀 Gửi duyệt
                      </button>
                    )}
                    {isVerified && (
                      <button
                        onClick={() => handleOpenEdit(job)}
                        style={{
                          background: '#f8fafc',
                          border: '1px solid #cbd5e1',
                          color: '#334155',
                          padding: '8px 14px',
                          borderRadius: '6px',
                          fontWeight: 600,
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px'
                        }}
                      >
                        ✏️ Sửa
                      </button>
                    )}
                    <button
                      onClick={() => handleDelete(job.id, job.title)}
                      style={{
                        background: '#fff5f5',
                        border: '1px solid #feb2b2',
                        color: '#c53030',
                        padding: '8px 14px',
                        borderRadius: '6px',
                        fontWeight: 600,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px'
                      }}
                    >
                      🗑️ Xóa
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </section>
  );
}

export default EmployerJobsPage;
