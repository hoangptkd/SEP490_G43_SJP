import { Link, useSearchParams } from 'react-router-dom';
import { useCallback, useEffect, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminJobSummary, JobReviewFilter } from '../../types/admin';

const PAGE_SIZE = 8;

const filters: { value: JobReviewFilter; label: string }[] = [
  { value: 'pending_review', label: 'Chờ duyệt' },
  { value: 'published', label: 'Đang công khai' },
  { value: 'closed', label: 'Tin vi phạm / Đã tạm dừng' },
  { value: 'rejected', label: 'Bị từ chối' },
];

function readFilter(value: string | null): JobReviewFilter {
  if (value === 'violations' || value === 'violating') return 'closed';
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
    default:
      return { text: status || '—', className: 'status-unverified' };
  }
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
  const [searchParams, setSearchParams] = useSearchParams();
  const [filter, setFilter] = useState<JobReviewFilter>(() => readFilter(searchParams.get('status')));
  const [page, setPage] = useState(() => readPage(searchParams.get('page')));
  const [jobs, setJobs] = useState<AdminJobSummary[]>([]);
  const [loadingList, setLoadingList] = useState(true);
  const [actingId, setActingId] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadJobs = useCallback(async () => {
    setLoadingList(true);
    setError('');
    try {
      const data = await adminService.listJobs(filter);
      setJobs(data);
    } catch (err) {
      setError(readError(err));
      setJobs([]);
    } finally {
      setLoadingList(false);
    }
  }, [filter]);

  useEffect(() => {
    loadJobs();
  }, [loadJobs]);

  useEffect(() => {
    setSearchParams({ status: filter, page: String(page) }, { replace: true });
  }, [filter, page, setSearchParams]);

  useEffect(() => {
    const totalPages = Math.max(1, Math.ceil(jobs.length / PAGE_SIZE));
    if (page > totalPages) {
      setPage(totalPages);
    }
  }, [jobs.length, page]);

  async function suspendJob(job: AdminJobSummary) {
    const reason = window.prompt(`Lý do tạm dừng tin "${job.title}"?`, 'Vi phạm nội dung / chính sách') || '';
    if (!reason.trim()) return;
    setActingId(job.id);
    setError('');
    setSuccess('');
    try {
      await adminService.closeJob(job.id, reason.trim());
      setSuccess('Đã tạm dừng tin vi phạm.');
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

  const totalPages = Math.max(1, Math.ceil(jobs.length / PAGE_SIZE));
  const paginatedJobs = jobs.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const isViolationView = filter === 'closed';

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <h1>Quản lý việc làm</h1>
          <p className="muted">
            {isViolationView
              ? 'Manage Violating Job Listing — tạm dừng / mở lại các tin bị ẩn vì vi phạm.'
              : 'Duyệt tin mới và xử lý tin vi phạm (Suspend Job Posting).'}
          </p>
        </div>
        <Link className="button-link outline" to="/admin/jobs?status=closed&page=1">
          Xem tin vi phạm
        </Link>
      </header>

      <div className="admin-filter-tabs">
        {filters.map((item) => (
          <button
            key={item.value}
            type="button"
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

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      <div className="admin-review-layout">
        <section className="admin-company-list-panel admin-review-list-panel">
          <div className="admin-company-list-header">
            <h2>{isViolationView ? 'Tin đã tạm dừng' : 'Danh sách tin tuyển dụng'}</h2>
            <span>{jobs.length} tin</span>
          </div>

          {loadingList ? (
            <p className="loading">Đang tải danh sách...</p>
          ) : jobs.length === 0 ? (
            <div className="admin-placeholder-card">
              <p>{isViolationView ? 'Không có tin vi phạm đang tạm dừng.' : 'Không có tin phù hợp bộ lọc.'}</p>
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

          {totalPages > 1 && (
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
