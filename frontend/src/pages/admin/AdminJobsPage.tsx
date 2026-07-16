import { Link, useSearchParams } from 'react-router-dom';
import { useCallback, useEffect, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminJobSummary, JobReviewFilter } from '../../types/admin';

const PAGE_SIZE = 8;

const filters: { value: JobReviewFilter; label: string }[] = [
  { value: 'pending_review', label: 'Chờ duyệt' },
  { value: 'published', label: 'Đã duyệt' },
  { value: 'rejected', label: 'Bị từ chối' },
];

function readFilter(value: string | null): JobReviewFilter {
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
  const [searchParams, setSearchParams] = useSearchParams();
  const [filter, setFilter] = useState<JobReviewFilter>(() => readFilter(searchParams.get('status')));
  const [page, setPage] = useState(() => readPage(searchParams.get('page')));
  const [jobs, setJobs] = useState<AdminJobSummary[]>([]);
  const [loadingList, setLoadingList] = useState(true);
  const [error, setError] = useState('');

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

  const totalPages = Math.max(1, Math.ceil(jobs.length / PAGE_SIZE));
  const paginatedJobs = jobs.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

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
            onClick={() => {
              setFilter(item.value);
              setPage(1);
            }}
          >
            {item.label}
          </button>
        ))}
        <button type="button" className="outline" onClick={loadJobs}>
          Làm mới
        </button>
      </div>

      {error && <p className="error admin-inline-message">{error}</p>}

      <div className="admin-review-layout">
        <section className="admin-company-list-panel admin-review-list-panel">
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
            <div className="admin-review-list">
              {paginatedJobs.map((job) => {
                const badge = jobStatusLabel(job.status);
                return (
                  <article
                    key={job.id}
                    className="admin-review-row"
                  >
                    <div className="admin-review-main">
                      <div className="admin-review-title-line">
                        <strong>{job.title}</strong>
                        <span className={`admin-status-badge ${badge.className}`}>{badge.text}</span>
                      </div>
                      <p>{job.companyName || 'Chưa có công ty'}</p>
                    </div>
                    <div className="admin-review-meta">
                      <span>{job.location || 'Chưa có địa điểm'}</span>
                      <span>{formatMoney(job.salaryMin, job.salaryMax)}</span>
                    </div>
                    <Link className="button-link outline" to={`/admin/jobs/${job.id}?status=${filter}&page=${page}`}>
                      Xem chi tiết
                    </Link>
                  </article>
                );
              })}
            </div>
          )}

          {jobs.length > PAGE_SIZE && (
            <div className="admin-pagination">
              <button type="button" className="outline" disabled={page === 1} onClick={() => setPage((value) => Math.max(1, value - 1))}>
                Trước
              </button>
              <span>Trang {page} / {totalPages}</span>
              <button type="button" className="outline" disabled={page === totalPages} onClick={() => setPage((value) => Math.min(totalPages, value + 1))}>
                Sau
              </button>
            </div>
          )}
        </section>
      </div>
    </section>
  );
}
