import { useCallback, useEffect, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminJobDetail, AdminJobSummary, JobReviewFilter } from '../../types/admin';

const filters: { value: JobReviewFilter; label: string }[] = [
  { value: 'pending_review', label: 'Chờ duyệt' },
  { value: 'published', label: 'Đã duyệt' },
  { value: 'rejected', label: 'Bị từ chối' },
];

function jobStatusLabel(status?: string) {
  const value = status?.toLowerCase() || 'draft';
  switch (value) {
    case 'published':
    case 'active':
      return { text: 'Đã duyệt', className: 'status-verified' };
    case 'pending_review':
      return { text: 'Chờ duyệt', className: 'status-pending' };
    case 'rejected':
      return { text: 'Bị từ chối', className: 'status-rejected' };
    case 'draft':
      return { text: 'Bản nháp', className: 'status-unverified' };
    case 'closed':
      return { text: 'Đã đóng', className: 'status-unverified' };
    default:
      return { text: status || '—', className: 'status-unverified' };
  }
}

function formatDate(value?: string) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN');
}

function formatMoney(min?: number, max?: number) {
  if (!min && !max) return 'Thỏa thuận';
  if (min && max) {
    return `${new Intl.NumberFormat('vi-VN').format(min)} - ${new Intl.NumberFormat('vi-VN').format(max)} VND`;
  }
  if (min) return `Từ ${new Intl.NumberFormat('vi-VN').format(min)} VND`;
  return `Đến ${new Intl.NumberFormat('vi-VN').format(max!)} VND`;
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

export default function AdminJobsPage() {
  const [filter, setFilter] = useState<JobReviewFilter>('pending_review');
  const [jobs, setJobs] = useState<AdminJobSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<AdminJobDetail | null>(null);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadJobs = useCallback(async () => {
    setLoadingList(true);
    setError('');
    try {
      const data = await adminService.listJobs(filter);
      setJobs(data);
      setSelectedId((prev) => {
        if (data.length === 0) return null;
        if (prev && data.some((item) => item.id === prev)) return prev;
        return data[0].id;
      });
    } catch (err) {
      setError(readError(err));
      setJobs([]);
      setSelectedId(null);
      setDetail(null);
    } finally {
      setLoadingList(false);
    }
  }, [filter]);

  const loadDetail = useCallback(async (id: string) => {
    setLoadingDetail(true);
    setError('');
    try {
      const data = await adminService.getJobDetail(id);
      setDetail(data);
    } catch (err) {
      setError(readError(err));
      setDetail(null);
    } finally {
      setLoadingDetail(false);
    }
  }, []);

  useEffect(() => {
    loadJobs();
  }, [loadJobs]);

  useEffect(() => {
    if (selectedId) {
      loadDetail(selectedId);
      setShowRejectForm(false);
      setRejectReason('');
      setSuccess('');
    }
  }, [selectedId, loadDetail]);

  async function handleApprove() {
    if (!selectedId || !detail) return;
    if (!window.confirm(`Phê duyệt tin tuyển dụng "${detail.job.title}"?`)) return;

    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.approveJob(selectedId);
      setDetail(updated);
      setSuccess('Đã phê duyệt tin tuyển dụng. Tin sẽ hiển thị công khai cho ứng viên.');
      await loadJobs();
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReject(event: React.FormEvent) {
    event.preventDefault();
    if (!selectedId || !detail) return;
    if (!rejectReason.trim()) {
      setError('Vui lòng nhập lý do từ chối.');
      return;
    }

    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.rejectJob(selectedId, rejectReason.trim());
      setDetail(updated);
      setSuccess('Đã từ chối tin tuyển dụng.');
      setShowRejectForm(false);
      setRejectReason('');
      await loadJobs();
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  }

  const selectedSummary = jobs.find((item) => item.id === selectedId);
  const status = jobStatusLabel(detail?.job.status);
  const canReview = detail?.job.status?.toLowerCase() === 'pending_review';

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <h1>Quản lý việc làm</h1>
        <p className="muted">Xem xét, phê duyệt hoặc từ chối tin tuyển dụng do nhà tuyển dụng đăng.</p>
      </header>

      <div className="admin-filter-tabs">
        {filters.map((item) => (
          <button
            key={item.value}
            type="button"
            className={filter === item.value ? 'active' : 'outline'}
            onClick={() => setFilter(item.value)}
          >
            {item.label}
          </button>
        ))}
        <button type="button" className="outline" onClick={loadJobs}>
          Làm mới
        </button>
      </div>

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      <div className="admin-company-layout">
        <aside className="admin-company-list-panel">
          <div className="admin-company-list-header">
            <h2>Danh sách tin tuyển dụng</h2>
            <span>{jobs.length} tin</span>
          </div>

          {loadingList ? (
            <p className="loading">Đang tải danh sách...</p>
          ) : jobs.length === 0 ? (
            <div className="admin-placeholder-card">
              <p>Không có tin nào trong mục này.</p>
            </div>
          ) : (
            <div className="admin-company-list">
              {jobs.map((job) => {
                const badge = jobStatusLabel(job.status);
                return (
                  <button
                    key={job.id}
                    type="button"
                    className={`admin-company-list-item${selectedId === job.id ? ' active' : ''}`}
                    onClick={() => setSelectedId(job.id)}
                  >
                    <div className="admin-company-list-item-top">
                      <strong>{job.title}</strong>
                      <span className={`admin-status-badge ${badge.className}`}>{badge.text}</span>
                    </div>
                    <p>{job.companyName || 'Chưa có công ty'}</p>
                    <div className="admin-company-list-item-meta">
                      <span>{job.location || 'Chưa có địa điểm'}</span>
                      <span>{formatMoney(job.salaryMin, job.salaryMax)}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </aside>

        <div className="admin-company-detail-panel">
          {!selectedId || !selectedSummary ? (
            <div className="admin-placeholder-card">
              <p>Chọn một tin tuyển dụng để xem chi tiết.</p>
            </div>
          ) : loadingDetail || !detail ? (
            <p className="loading">Đang tải chi tiết tin...</p>
          ) : (
            <>
              <div className="admin-company-detail-header">
                <div>
                  <h2>{detail.job.title}</h2>
                  <p className="muted">Cập nhật lần cuối: {formatDate(detail.updatedAt)}</p>
                </div>
                <span className={`admin-status-badge ${status.className}`}>{status.text}</span>
              </div>

              <div className="admin-company-info-grid">
                <div><span>Công ty</span><strong>{detail.job.company?.name || '—'}</strong></div>
                <div><span>Địa điểm</span><strong>{detail.job.location || '—'}</strong></div>
                <div><span>Mức lương</span><strong>{formatMoney(detail.job.salaryMin, detail.job.salaryMax)}</strong></div>
                <div><span>Kinh nghiệm</span><strong>{detail.job.experienceLevel || '—'}</strong></div>
                <div><span>Loại hình</span><strong>{detail.job.jobType || '—'}</strong></div>
                <div><span>Hình thức</span><strong>{detail.job.workMode || '—'}</strong></div>
                <div><span>Số lượng tuyển</span><strong>{detail.job.vacancies ?? '—'}</strong></div>
                <div><span>Hạn nộp</span><strong>{detail.job.deadline ? formatDate(detail.job.deadline) : '—'}</strong></div>
              </div>

              <div className="admin-company-section">
                <h3>Mô tả công việc</h3>
                <p style={{ whiteSpace: 'pre-wrap' }}>{detail.job.description || '—'}</p>
              </div>

              {detail.job.requirements?.length > 0 && (
                <div className="admin-company-section">
                  <h3>Yêu cầu</h3>
                  <ul>
                    {detail.job.requirements.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>
              )}

              {detail.job.skills?.length > 0 && (
                <div className="admin-company-section">
                  <h3>Kỹ năng</h3>
                  <p>{detail.job.skills.join(', ')}</p>
                </div>
              )}

              {detail.job.benefits && (
                <div className="admin-company-section">
                  <h3>Quyền lợi</h3>
                  <p style={{ whiteSpace: 'pre-wrap' }}>{detail.job.benefits}</p>
                </div>
              )}

              <div className="admin-company-section">
                <h3>Nhà tuyển dụng đăng tin</h3>
                <div className="admin-company-info-grid">
                  <div><span>Họ tên</span><strong>{detail.employerName || '—'}</strong></div>
                  <div><span>Email</span><strong>{detail.employerEmail || '—'}</strong></div>
                  <div><span>Chức vụ</span><strong>{detail.employerPosition || '—'}</strong></div>
                  <div><span>Ngày tạo</span><strong>{formatDate(detail.createdAt)}</strong></div>
                </div>
              </div>

              {detail.job.rejectionReason && (
                <div className="admin-company-section">
                  <h3>Lý do từ chối</h3>
                  <p className="admin-company-doc-reason">{detail.job.rejectionReason}</p>
                </div>
              )}

              {canReview && (
                <div className="admin-company-actions">
                  <button type="button" onClick={handleApprove} disabled={submitting}>
                    {submitting ? 'Đang xử lý...' : 'Phê duyệt tin'}
                  </button>
                  <button
                    type="button"
                    className="danger"
                    onClick={() => setShowRejectForm((value) => !value)}
                    disabled={submitting}
                  >
                    Từ chối tin
                  </button>
                </div>
              )}

              {showRejectForm && canReview && (
                <form className="admin-company-reject-form" onSubmit={handleReject}>
                  <label>
                    Lý do từ chối
                    <textarea
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      placeholder="Nhập lý do từ chối để nhà tuyển dụng biết và chỉnh sửa lại..."
                      required
                    />
                  </label>
                  <div className="admin-company-actions">
                    <button type="submit" className="danger" disabled={submitting}>
                      {submitting ? 'Đang xử lý...' : 'Xác nhận từ chối'}
                    </button>
                    <button
                      type="button"
                      className="outline"
                      onClick={() => {
                        setShowRejectForm(false);
                        setRejectReason('');
                      }}
                      disabled={submitting}
                    >
                      Hủy
                    </button>
                  </div>
                </form>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  );
}
