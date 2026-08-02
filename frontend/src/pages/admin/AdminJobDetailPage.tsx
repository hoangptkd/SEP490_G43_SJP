import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminJobDetail, AdminJobReport } from '../../types/admin';

function jobStatusLabel(status?: string) {
  const value = status?.toLowerCase() || 'draft';
  switch (value) {
    case 'published':
    case 'active':
      return { text: 'Đã duyệt', className: 'status-verified' };
    case 'pending_review':
      return { text: 'Chờ duyệt', className: 'status-pending' };
    case 'awaiting_company':
      return { text: 'Đang chờ Công ty kiểm tra', className: 'status-pending' };
    case 'rejected':
      return { text: 'Bị từ chối', className: 'status-rejected' };
    case 'draft':
      return { text: 'Bản nháp', className: 'status-unverified' };
    case 'closed':
      return { text: 'Đã ẩn', className: 'status-unverified' };
    case 'removed':
      return { text: 'Đã gỡ do vi phạm', className: 'status-rejected' };
    default:
      return { text: status || '—', className: 'status-unverified' };
  }
}

function reportReasonLabel(reason?: string) {
  const map: Record<string, string> = {
    spam: 'Spam / tin rác',
    scam: 'Lừa đảo / nghi ngờ',
    offensive: 'Nội dung phản cảm',
    misleading: 'Thông tin sai lệch',
    discrimination: 'Phân biệt đối xử',
    other: 'Khác',
  };
  return map[reason || ''] || reason || '—';
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
  const [reports, setReports] = useState<AdminJobReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showNotifyForm, setShowNotifyForm] = useState(false);
  const [notifyNote, setNotifyNote] = useState(
    'Tin tuyển dụng có dấu hiệu vi phạm. Vui lòng kiểm tra và chỉnh sửa trong vòng 3 ngày, rồi gửi lại để duyệt. Quá hạn hệ thống sẽ tự động gỡ tin.'
  );

  const reportIdFromQuery = searchParams.get('reportId');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    setError('');
    Promise.all([
      adminService.getJobDetail(id),
      adminService.listJobReports('all').catch(() => [] as AdminJobReport[]),
    ])
      .then(([jobDetail, allReports]) => {
        setDetail(jobDetail);
        setReports(allReports.filter((item) => item.jobId === id));
      })
      .catch((err) => setError(readError(err)))
      .finally(() => setLoading(false));
  }, [id]);

  const actionableReport =
    reports.find((item) => item.id === reportIdFromQuery && item.status === 'pending')
    || reports.find((item) => item.status === 'pending')
    || null;

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

  async function handleNotifyCompany() {
    if (!actionableReport || !detail) return;
    if (!notifyNote.trim()) {
      setError('Vui lòng nhập lý do thông báo cho công ty.');
      return;
    }

    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const updatedReport = await adminService.notifyCompanyJobReport(actionableReport.id, notifyNote.trim());
      setReports((prev) => prev.map((item) => (
        item.jobId === updatedReport.jobId && (item.status === 'pending' || item.status === 'awaiting_company')
          ? {
              ...item,
              status: 'awaiting_company',
              adminNote: updatedReport.adminNote,
              jobStatus: 'awaiting_company',
              companyFixDeadline: updatedReport.companyFixDeadline,
            }
          : item
      )));
      const refreshed = await adminService.getJobDetail(detail.job.id);
      setDetail(refreshed);
      setShowNotifyForm(false);
      setSuccess('Đã thông báo công ty. Công ty có 3 ngày để sửa và gửi lại; quá hạn tin sẽ bị gỡ.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDismissReport() {
    if (!actionableReport) return;
    const note = window.prompt('Ghi chú (không vi phạm, tin tiếp tục hoạt động):', 'Không phát hiện vi phạm') || '';
    if (!note.trim()) return;
    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.dismissJobReport(actionableReport.id, note.trim());
      setReports((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
      setSuccess('Đã bỏ qua báo cáo. Tin tiếp tục hoạt động bình thường.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResolveReport() {
    if (!actionableReport || !detail) return;
    if (!window.confirm(`Xác nhận tin "${detail.job.title}" vi phạm và GỠ tin này?`)) return;
    const note = window.prompt('Lý do gỡ tin:', 'Vi phạm chính sách — đã gỡ tin') || '';
    if (!note.trim()) return;
    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      await adminService.resolveJobReport(actionableReport.id, note.trim());
      const refreshed = await adminService.getJobDetail(detail.job.id);
      setDetail(refreshed);
      setReports((prev) => prev.map((item) => (
        item.jobId === detail.job.id && item.status === 'pending'
          ? { ...item, status: 'resolved', adminNote: note.trim() }
          : item
      )));
      setSuccess('Đã xác nhận vi phạm và gỡ tin tuyển dụng.');
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
      <header className="admin-page-intro">
        <div className="admin-page-intro-copy">
          <p className="admin-page-intro-eyebrow">Chi tiết tin tuyển dụng</p>
          <h1>{loading ? 'Đang tải...' : detail?.job.title || 'Tin tuyển dụng'}</h1>
          <p>
            {detail
              ? `${detail.job.company?.name || 'Công ty'} · Cập nhật ${formatDate(detail.updatedAt)}`
              : 'Xem xét, duyệt hoặc xử lý tin tuyển dụng.'}
          </p>
        </div>
        <div className="admin-page-intro-aside">
          <div className="admin-page-intro-stat">
            <span>Trạng thái</span>
            <strong>{detail ? status.text : '—'}</strong>
          </div>
          <div className="admin-page-intro-stat">
            <span>Báo cáo</span>
            <strong>{reports.length}</strong>
          </div>
        </div>
      </header>

      <div className="admin-toolbar">
        <div className="admin-toolbar-group">
          <button type="button" className="outline" onClick={() => navigate(backTo)}>
            ← Quay lại
          </button>
          <Link className="button-link outline" to={backTo}>
            Danh sách việc làm
          </Link>
        </div>
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
              <h2>Thông tin tin tuyển dụng</h2>
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
              <h3>{currentStatus === 'awaiting_company' ? 'Nội dung thông báo gửi công ty' : 'Lý do từ chối / ghi chú'}</h3>
              <p className="admin-company-doc-reason">{detail.job.rejectionReason}</p>
              {currentStatus === 'awaiting_company' && detail.job.reportFixDeadline && (
                <p className="muted" style={{ marginTop: 8, color: '#c2410c', fontWeight: 600 }}>
                  Hạn sửa: {formatDate(detail.job.reportFixDeadline)} — quá hạn hệ thống sẽ tự gỡ tin.
                </p>
              )}
            </div>
          )}

          {reports.length > 0 && (
            <div className="admin-company-section">
              <h3>Báo cáo liên quan ({reports.length})</h3>
              <div className="admin-review-list">
                {reports.map((report) => (
                  <article key={report.id} className="admin-review-row" style={{ marginBottom: 12 }}>
                    <div className="admin-review-main" style={{ flex: 1 }}>
                      <div className="admin-review-meta">
                        <span>{report.reporterName || report.reporterEmail}</span>
                        <span>Tuổi: {report.reporterAge != null ? report.reporterAge : '—'}</span>
                        <span>SĐT: {report.reporterPhone || '—'}</span>
                        <span>{reportReasonLabel(report.reason)}</span>
                        <span>{formatDate(report.createdAt)}</span>
                        <span>{report.status}</span>
                      </div>
                      {report.description && <p className="muted">{report.description}</p>}
                      {report.adminNote && <p className="muted">Ghi chú: {report.adminNote}</p>}
                      {report.status === 'awaiting_company' && report.companyFixDeadline && (
                        <p className="muted" style={{ color: '#c2410c', fontWeight: 600 }}>
                          Hạn sửa: {formatDate(report.companyFixDeadline)}
                        </p>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            </div>
          )}

          {actionableReport && (
            <div className="admin-company-actions">
              <button type="button" className="outline" onClick={handleDismissReport} disabled={submitting}>
                {submitting ? 'Đang xử lý...' : 'Không vi phạm'}
              </button>
              <button
                type="button"
                onClick={() => setShowNotifyForm((value) => !value)}
                disabled={submitting}
              >
                Thông báo công ty sửa
              </button>
              <button type="button" className="danger" onClick={handleResolveReport} disabled={submitting}>
                {submitting ? 'Đang xử lý...' : 'Gỡ tin'}
              </button>
            </div>
          )}

          {showNotifyForm && actionableReport && (
            <form
              className="admin-company-reject-form"
              onSubmit={(event) => {
                event.preventDefault();
                void handleNotifyCompany();
              }}
            >
              <label>
                Lý do thông báo công ty sửa *
                <textarea
                  value={notifyNote}
                  onChange={(e) => setNotifyNote(e.target.value)}
                  placeholder="Nhập lý do / yêu cầu chỉnh sửa gửi tới công ty..."
                  required
                  rows={4}
                />
              </label>
              <div className="admin-company-actions">
                <button type="submit" disabled={submitting}>
                  {submitting ? 'Đang xử lý...' : 'Gửi thông báo'}
                </button>
                <button type="button" className="outline" onClick={() => setShowNotifyForm(false)} disabled={submitting}>
                  Hủy
                </button>
              </div>
            </form>
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
