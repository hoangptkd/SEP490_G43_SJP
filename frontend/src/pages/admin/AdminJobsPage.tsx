import { Link, useSearchParams } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminJobReport, AdminJobSummary, JobReviewFilter } from '../../types/admin';

const PAGE_SIZE = 8;

const filters: { value: JobReviewFilter; label: string }[] = [
  { value: 'pending_review', label: 'Chờ duyệt' },
  { value: 'published', label: 'Đang công khai' },
  { value: 'reports', label: 'Tin bị báo cáo' },
  { value: 'closed', label: 'Đã tạm dừng' },
  { value: 'rejected', label: 'Bị từ chối' },
];

function readFilter(value: string | null): JobReviewFilter {
  if (value === 'violations' || value === 'violating') return 'reports';
  return filters.some((item) => item.value === value) ? (value as JobReviewFilter) : 'pending_review';
}

function readPage(value: string | null) {
  const page = Number(value);
  return Number.isInteger(page) && page > 0 ? page : 1;
}

function jobStatusLabel(status?: string) {
  const value = status?.toLowerCase() || 'draft';
  switch (value) {
    case 'published':
    case 'active':
      return { text: 'Đang công khai', className: 'status-verified' };
    case 'pending_review':
      return { text: 'Chờ duyệt', className: 'status-pending' };
    case 'rejected':
      return { text: 'Bị từ chối', className: 'status-rejected' };
    case 'draft':
      return { text: 'Bản nháp', className: 'status-unverified' };
    case 'closed':
      return { text: 'Đã tạm dừng', className: 'status-rejected' };
    case 'removed':
      return { text: 'Đã gỡ do vi phạm', className: 'status-rejected' };
    case 'awaiting_company':
      return { text: 'Đang chờ Công ty kiểm tra', className: 'status-pending' };
    default:
      return { text: status || '—', className: 'status-unverified' };
  }
}

function reportStatusLabel(status?: string) {
  switch ((status || '').toLowerCase()) {
    case 'pending':
      return { text: 'Chờ xử lý', className: 'status-pending' };
    case 'awaiting_company':
      return { text: 'Chờ công ty sửa', className: 'status-pending' };
    case 'resubmitted':
      return { text: 'Công ty đã gửi lại', className: 'status-verified' };
    case 'resolved':
      return { text: 'Đã gỡ tin', className: 'status-rejected' };
    case 'dismissed':
      return { text: 'Đã bỏ qua', className: 'status-unverified' };
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

function formatMoney(min?: number, max?: number) {
  if (!min && !max) return 'Thỏa thuận';
  if (min && max) {
    return `${new Intl.NumberFormat('vi-VN').format(min)} - ${new Intl.NumberFormat('vi-VN').format(max)} VND`;
  }
  if (min) return `Từ ${new Intl.NumberFormat('vi-VN').format(min)} VND`;
  return `Đến ${new Intl.NumberFormat('vi-VN').format(max!)} VND`;
}

function formatDate(value?: string) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN');
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

function jobMatches(job: AdminJobSummary, query: string) {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return [job.title, job.companyName, job.employerEmail, job.employerName, job.location, job.status]
    .some((value) => String(value ?? '').toLowerCase().includes(q));
}

function reportMatches(report: AdminJobReport, query: string) {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return [
    report.jobTitle,
    report.companyName,
    report.reporterName,
    report.reporterEmail,
    report.reporterPhone,
    report.reason,
    reportReasonLabel(report.reason),
    report.description,
    report.adminNote,
    report.status,
  ].some((value) => String(value ?? '').toLowerCase().includes(q));
}

export default function AdminJobsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [filter, setFilter] = useState<JobReviewFilter>(() => readFilter(searchParams.get('status')));
  const [page, setPage] = useState(() => readPage(searchParams.get('page')));
  const [jobs, setJobs] = useState<AdminJobSummary[]>([]);
  const [reports, setReports] = useState<AdminJobReport[]>([]);
  const [reportStatus, setReportStatus] = useState('pending');
  const [loadingList, setLoadingList] = useState(true);
  const [actingId, setActingId] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [notifyReportId, setNotifyReportId] = useState('');
  const [notifyNote, setNotifyNote] = useState('');
  const [search, setSearch] = useState(() => searchParams.get('q') || '');

  const isReportsView = filter === 'reports';

  const loadJobs = useCallback(async () => {
    setLoadingList(true);
    setError('');
    try {
      if (filter === 'reports') {
        const data = await adminService.listJobReports(reportStatus);
        setReports(data);
        setJobs([]);
      } else {
        const data = await adminService.listJobs(filter);
        setJobs(data);
        setReports([]);
      }
    } catch (err) {
      setError(readError(err));
      setJobs([]);
      setReports([]);
    } finally {
      setLoadingList(false);
    }
  }, [filter, reportStatus]);

  useEffect(() => {
    loadJobs();
  }, [loadJobs]);

  useEffect(() => {
    const params: Record<string, string> = { status: filter, page: String(page) };
    if (search.trim()) params.q = search.trim();
    setSearchParams(params, { replace: true });
  }, [filter, page, search, setSearchParams]);

  const filteredJobs = useMemo(
    () => jobs.filter((item) => jobMatches(item, search)),
    [jobs, search],
  );

  const filteredReports = useMemo(
    () => reports.filter((item) => reportMatches(item, search)),
    [reports, search],
  );

  useEffect(() => {
    const total = isReportsView ? filteredReports.length : filteredJobs.length;
    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
    if (page > totalPages) setPage(totalPages);
  }, [filteredJobs.length, filteredReports.length, isReportsView, page]);

  async function suspendJob(job: AdminJobSummary) {
    const reason = window.prompt(`Lý do tạm dừng tin "${job.title}"?`, 'Tạm ẩn theo quyết định quản trị') || '';
    if (!reason.trim()) return;
    setActingId(job.id);
    setError('');
    setSuccess('');
    try {
      await adminService.closeJob(job.id, reason.trim());
      setSuccess('Đã tạm dừng tin tuyển dụng.');
      if (filter === 'published') {
        setJobs((prev) => prev.filter((item) => item.id !== job.id));
      } else {
        await loadJobs();
      }
    } catch (err) {
      setError(readError(err));
    } finally {
      setActingId('');
    }
  }

  async function reopenJob(job: AdminJobSummary) {
    if (!window.confirm(`Mở lại tin "${job.title}" lên trạng thái công khai?`)) return;
    setActingId(job.id);
    setError('');
    setSuccess('');
    try {
      await adminService.reopenJob(job.id);
      setSuccess('Đã mở lại tin tuyển dụng.');
      if (filter === 'closed') {
        setJobs((prev) => prev.filter((item) => item.id !== job.id));
      } else {
        await loadJobs();
      }
    } catch (err) {
      setError(readError(err));
    } finally {
      setActingId('');
    }
  }

  async function dismissReport(report: AdminJobReport) {
    const note = window.prompt('Ghi chú (không vi phạm, tin tiếp tục hoạt động):', 'Không phát hiện vi phạm') || '';
    if (!note.trim()) return;
    setActingId(report.id);
    setError('');
    setSuccess('');
    try {
      await adminService.dismissJobReport(report.id, note.trim());
      setSuccess('Đã bỏ qua báo cáo. Tin tiếp tục hoạt động bình thường.');
      await loadJobs();
    } catch (err) {
      setError(readError(err));
    } finally {
      setActingId('');
    }
  }

  async function resolveReport(report: AdminJobReport) {
    if (!window.confirm(`Xác nhận tin "${report.jobTitle}" vi phạm và GỠ tin này?`)) return;
    const note = window.prompt('Lý do gỡ tin:', 'Vi phạm chính sách — đã gỡ tin') || '';
    if (!note.trim()) return;
    setActingId(report.id);
    setError('');
    setSuccess('');
    try {
      await adminService.resolveJobReport(report.id, note.trim());
      setSuccess('Đã xác nhận vi phạm và gỡ tin tuyển dụng.');
      await loadJobs();
    } catch (err) {
      setError(readError(err));
    } finally {
      setActingId('');
    }
  }

  async function notifyCompany(report: AdminJobReport) {
    if (!notifyNote.trim()) {
      setError('Vui lòng nhập lý do thông báo cho công ty.');
      return;
    }
    setActingId(report.id);
    setError('');
    setSuccess('');
    try {
      await adminService.notifyCompanyJobReport(report.id, notifyNote.trim());
      setSuccess('Đã thông báo công ty. Công ty có 3 ngày để sửa và gửi lại; quá hạn tin sẽ bị gỡ.');
      setNotifyReportId('');
      setNotifyNote('');
      await loadJobs();
    } catch (err) {
      setError(readError(err));
    } finally {
      setActingId('');
    }
  }

  const listLength = isReportsView ? filteredReports.length : filteredJobs.length;
  const sourceLength = isReportsView ? reports.length : jobs.length;
  const totalPages = Math.max(1, Math.ceil(listLength / PAGE_SIZE));
  const paginatedJobs = filteredJobs.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const paginatedReports = filteredReports.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <section className="admin-page">
      <header className="admin-page-intro">
        <div className="admin-page-intro-copy">
          <p className="admin-page-intro-eyebrow">Kiểm duyệt tin tuyển dụng</p>
          <h1>Quản lý việc làm</h1>
          <p>
            {isReportsView
              ? 'Tiếp nhận báo cáo từ ứng viên — bỏ qua, yêu cầu công ty sửa trong 3 ngày, hoặc gỡ tin nếu xác nhận vi phạm.'
              : filter === 'closed'
                ? 'Các tin đã tạm dừng (có thể mở lại khi cần).'
                : 'Duyệt tin mới, tạm dừng tin, và xử lý báo cáo vi phạm.'}
          </p>
        </div>
        <div className="admin-page-intro-aside">
          <div className="admin-page-intro-stat">
            <span>Đang xem</span>
            <strong>{filters.find((item) => item.value === filter)?.label || '—'}</strong>
          </div>
          <div className="admin-page-intro-stat">
            <span>{isReportsView ? 'Số báo cáo' : 'Số tin'}</span>
            <strong>{listLength}</strong>
          </div>
        </div>
      </header>

      <div className="admin-toolbar">
        <div className="admin-toolbar-group" role="tablist" aria-label="Lọc tin tuyển dụng">
          {filters.map((item) => (
            <button
              key={item.value}
              type="button"
              role="tab"
              aria-selected={filter === item.value}
              className={filter === item.value ? 'active' : 'outline'}
              onClick={() => {
                setFilter(item.value);
                setPage(1);
                setSuccess('');
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
        <Link className="button-link outline admin-toolbar-refresh" to="/admin/jobs?status=reports&page=1">
          Xem tin bị báo cáo
        </Link>
      </div>

      {isReportsView && (
        <div className="admin-toolbar">
          <div className="admin-toolbar-group" role="tablist" aria-label="Lọc trạng thái báo cáo">
            {[
              { value: 'pending', label: 'Chờ xử lý' },
              { value: 'awaiting_company', label: 'Chờ công ty sửa' },
              { value: 'resubmitted', label: 'Công ty đã gửi lại' },
              { value: 'dismissed', label: 'Đã bỏ qua' },
              { value: 'resolved', label: 'Đã gỡ tin' },
              { value: 'all', label: 'Tất cả' },
            ].map((item) => (
              <button
                key={item.value}
                type="button"
                role="tab"
                aria-selected={reportStatus === item.value}
                className={reportStatus === item.value ? 'active' : 'outline'}
                onClick={() => {
                  setReportStatus(item.value);
                  setPage(1);
                }}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      <div className="admin-review-layout">
        <section className="admin-company-list-panel admin-review-list-panel">
          <div className="admin-company-list-header">
            <h2>
              {isReportsView
                ? 'Báo cáo từ ứng viên'
                : filter === 'closed'
                  ? 'Tin đã tạm dừng'
                  : 'Danh sách tin tuyển dụng'}
            </h2>
            <span>
              {search.trim()
                ? `${listLength}/${sourceLength} ${isReportsView ? 'báo cáo' : 'tin'}`
                : `${sourceLength} ${isReportsView ? 'báo cáo' : 'tin'}`}
            </span>
          </div>

          <div className="admin-company-list-header" style={{ marginBottom: 12 }}>
            <input
              type="search"
              className="admin-search-input"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(1);
              }}
              placeholder={
                isReportsView
                  ? 'Tìm theo tin, công ty, người báo cáo, email, lý do...'
                  : 'Tìm theo tiêu đề tin, công ty, email NTD, địa điểm...'
              }
              style={{ flex: 1, minWidth: 240, maxWidth: 520 }}
              aria-label="Tìm kiếm việc làm"
            />
          </div>

          {loadingList ? (
            <p className="loading">Đang tải danh sách...</p>
          ) : isReportsView ? (
            filteredReports.length === 0 ? (
              <div className="admin-placeholder-card">
                <p>{search.trim() ? 'Không tìm thấy báo cáo phù hợp.' : 'Không có báo cáo phù hợp.'}</p>
              </div>
            ) : (
              <div className="admin-review-list">
                {paginatedReports.map((report) => (
                  <article key={report.id} className="admin-review-row admin-user-row">
                    <div className="admin-review-main" style={{ flex: 1 }}>
                      <div className="admin-review-title-line">
                        <strong>{report.jobTitle}</strong>
                        <span className={`admin-status-badge ${reportStatusLabel(report.status).className}`}>
                          {reportStatusLabel(report.status).text}
                        </span>
                      </div>
                      <div className="admin-review-meta">
                        <span>{report.companyName}</span>
                        <span>{reportReasonLabel(report.reason)}</span>
                        <span>{formatDate(report.createdAt)}</span>
                      </div>
                      <div className="admin-review-meta">
                        <span>Người báo cáo: {report.reporterName || '—'}</span>
                        <span>
                          Tuổi: {report.reporterAge != null ? report.reporterAge : '—'}
                        </span>
                        <span>SĐT: {report.reporterPhone || '—'}</span>
                        <span>{report.reporterEmail}</span>
                      </div>
                      {report.description && <p className="muted">{report.description}</p>}
                      {report.adminNote && <p className="muted">Ghi chú admin: {report.adminNote}</p>}
                      {report.status === 'awaiting_company' && report.companyFixDeadline && (
                        <p className="muted" style={{ color: '#c2410c', fontWeight: 600 }}>
                          Hạn sửa: {formatDate(report.companyFixDeadline)} (nếu quá hạn hệ thống sẽ tự gỡ tin)
                        </p>
                      )}
                      {notifyReportId === report.id && (
                        <div style={{ marginTop: 12, display: 'grid', gap: 8, maxWidth: 560 }}>
                          <label style={{ display: 'grid', gap: 6, fontSize: '0.9rem' }}>
                            Lý do thông báo công ty sửa *
                            <textarea
                              value={notifyNote}
                              onChange={(e) => setNotifyNote(e.target.value)}
                              rows={3}
                              placeholder="Ví dụ: Nội dung tin không khớp thực tế. Công ty có 3 ngày để chỉnh sửa và gửi lại; quá hạn tin sẽ bị gỡ."
                              style={{ width: '100%', padding: 10, borderRadius: 6, border: '1px solid #cbd5e1' }}
                            />
                          </label>
                          <div className="admin-company-actions">
                            <button type="button" disabled={actingId === report.id} onClick={() => notifyCompany(report)}>
                              {actingId === report.id ? '...' : 'Gửi thông báo'}
                            </button>
                            <button
                              type="button"
                              className="outline"
                              disabled={actingId === report.id}
                              onClick={() => {
                                setNotifyReportId('');
                                setNotifyNote('');
                              }}
                            >
                              Hủy
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                    <div className="admin-company-actions">
                      {report.status === 'pending' && (
                        <>
                          <button type="button" className="outline" disabled={actingId === report.id} onClick={() => dismissReport(report)}>
                            {actingId === report.id ? '...' : 'Không vi phạm'}
                          </button>
                          <button
                            type="button"
                            disabled={actingId === report.id}
                            onClick={() => {
                              setNotifyReportId(report.id);
                              setNotifyNote(
                                'Tin tuyển dụng có dấu hiệu vi phạm. Vui lòng kiểm tra và chỉnh sửa trong vòng 3 ngày, rồi gửi lại để duyệt. Quá hạn hệ thống sẽ tự động gỡ tin.',
                              );
                              setError('');
                              setSuccess('');
                            }}
                          >
                            Thông báo công ty sửa
                          </button>
                          <button type="button" className="danger" disabled={actingId === report.id} onClick={() => resolveReport(report)}>
                            {actingId === report.id ? '...' : 'Gỡ tin'}
                          </button>
                        </>
                      )}
                      <Link className="button-link outline" to={`/admin/jobs/${report.jobId}?status=reports&page=${page}&reportId=${report.id}`}>
                        Chi tiết tin
                      </Link>
                    </div>
                  </article>
                ))}
              </div>
            )
          ) : filteredJobs.length === 0 ? (
            <div className="admin-placeholder-card">
              <p>{search.trim() ? 'Không tìm thấy tin phù hợp.' : 'Không có tin phù hợp bộ lọc.'}</p>
            </div>
          ) : (
            <div className="admin-review-list">
              {paginatedJobs.map((job) => {
                const badge = jobStatusLabel(job.status);
                const status = job.status?.toLowerCase() || '';
                return (
                  <article key={job.id} className="admin-review-row admin-user-row">
                    <Link
                      to={`/admin/jobs/${job.id}?status=${filter}&page=${page}`}
                      className="admin-review-main"
                      style={{ color: 'inherit', flex: 1 }}
                    >
                      <div className="admin-review-title-line">
                        <strong>{job.title}</strong>
                        <span className={`admin-status-badge ${badge.className}`}>{badge.text}</span>
                      </div>
                      <div className="admin-review-meta">
                        <span>{job.companyName || '—'}</span>
                        <span>{job.employerEmail || '—'}</span>
                        <span>{job.location || '—'}</span>
                        <span>{formatMoney(job.salaryMin, job.salaryMax)}</span>
                      </div>
                    </Link>
                    <div className="admin-company-actions">
                      {(status === 'published' || status === 'active') && (
                        <button type="button" className="danger" disabled={actingId === job.id} onClick={() => suspendJob(job)}>
                          {actingId === job.id ? '...' : 'Tạm dừng'}
                        </button>
                      )}
                      {status === 'closed' && (
                        <button type="button" disabled={actingId === job.id} onClick={() => reopenJob(job)}>
                          {actingId === job.id ? '...' : 'Mở lại'}
                        </button>
                      )}
                      <Link className="button-link outline" to={`/admin/jobs/${job.id}?status=${filter}&page=${page}`}>
                        Chi tiết
                      </Link>
                    </div>
                  </article>
                );
              })}
            </div>
          )}

          {listLength > PAGE_SIZE && (
            <div className="admin-pagination">
              <button type="button" className="outline" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>Trước</button>
              <span>Trang {page}/{totalPages}</span>
              <button type="button" className="outline" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>Sau</button>
            </div>
          )}
        </section>
      </div>
    </section>
  );
}
