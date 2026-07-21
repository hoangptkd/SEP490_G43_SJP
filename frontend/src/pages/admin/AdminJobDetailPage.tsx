import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminJobDetail } from '../../types/admin';

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
      return { text: 'Đã ẩn', className: 'status-unverified' };
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

export default function AdminJobDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [detail, setDetail] = useState<AdminJobDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    setError('');
    adminService.getJobDetail(id)
      .then(setDetail)
      .catch((err) => setError(readError(err)))
      .finally(() => setLoading(false));
  }, [id]);

  async function handleApprove() {
    if (!id || !detail) return;
    if (!window.confirm(`Phê duyệt tin tuyển dụng "${detail.job.title}"?`)) return;

    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.approveJob(id);
      setDetail(updated);
      setSuccess('Đã phê duyệt tin tuyển dụng. Tin sẽ hiển thị công khai cho ứng viên.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReject(event: FormEvent) {
    event.preventDefault();
    if (!id || !detail) return;
    if (!rejectReason.trim()) {
      setError('Vui lòng nhập lý do từ chối.');
      return;
    }

    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.rejectJob(id, rejectReason.trim());
      setDetail(updated);
      setSuccess('Đã từ chối tin tuyển dụng.');
      setShowRejectForm(false);
      setRejectReason('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleClose() {
    if (!id || !detail) return;
    const reason = window.prompt('Nhập lý do ẩn tin tuyển dụng', 'Vi phạm nội dung / yêu cầu ẩn bởi admin') || '';
    if (!reason.trim()) return;
    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.closeJob(id, reason.trim());
      setDetail(updated);
      setSuccess('Đã ẩn tin tuyển dụng khỏi danh sách công khai.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReopen() {
    if (!id || !detail) return;
    if (!window.confirm(`Mở lại tin "${detail.job.title}" lên trạng thái công khai?`)) return;
    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.reopenJob(id);
      setDetail(updated);
      setSuccess('Đã mở lại tin tuyển dụng.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  }

  const status = jobStatusLabel(detail?.job.status);
  const currentStatus = detail?.job.status?.toLowerCase() || '';
  const canReview = currentStatus === 'pending_review';
  const canClose = currentStatus === 'published' || currentStatus === 'active';
  const canReopen = currentStatus === 'closed';
  const backStatus = searchParams.get('status') || 'pending_review';
  const backPage = searchParams.get('page') || '1';
  const backTo = `/admin/jobs?status=${backStatus}&page=${backPage}`;

  return (
    <section className="admin-page">
      <div className="admin-detail-topbar">
        <button type="button" className="outline" onClick={() => navigate(backTo)}>
          Quay lại
        </button>
        <Link className="button-link outline" to={backTo}>
          Danh sách việc làm
        </Link>
      </div>

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      {loading ? (
        <p className="loading">Đang tải chi tiết tin...</p>
      ) : !detail ? (
        <div className="admin-placeholder-card">
          <p>Không tìm thấy tin tuyển dụng.</p>
        </div>
      ) : (
        <section className="admin-company-detail-panel admin-readable-detail">
          <div className="admin-company-detail-header">
            <div>
              <h1>{detail.job.title}</h1>
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
              <button type="button" className="danger" onClick={() => setShowRejectForm((value) => !value)} disabled={submitting}>
                Từ chối tin
              </button>
            </div>
          )}

          {(canClose || canReopen) && (
            <div className="admin-company-actions">
              {canClose && (
                <button type="button" className="danger" onClick={handleClose} disabled={submitting}>
                  {submitting ? 'Đang xử lý...' : 'Ẩn tin công khai'}
                </button>
              )}
              {canReopen && (
                <button type="button" onClick={handleReopen} disabled={submitting}>
                  {submitting ? 'Đang xử lý...' : 'Mở lại tin'}
                </button>
              )}
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
                <button type="button" className="outline" onClick={() => setShowRejectForm(false)} disabled={submitting}>
                  Hủy
                </button>
              </div>
            </form>
          )}
        </section>
      )}
    </section>
  );
}
