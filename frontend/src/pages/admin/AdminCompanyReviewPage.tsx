import { Link, useSearchParams } from 'react-router-dom';
import { useCallback, useEffect, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminCompanySummary, CompanyReviewFilter } from '../../types/admin';

const PAGE_SIZE = 8;

const filters: { value: CompanyReviewFilter; label: string }[] = [
  { value: 'pending', label: 'Chờ duyệt' },
  { value: 'verified', label: 'Đã duyệt' },
  { value: 'rejected', label: 'Bị từ chối' },
];

function readFilter(value: string | null): CompanyReviewFilter {
  return filters.some((item) => item.value === value) ? (value as CompanyReviewFilter) : 'pending';
}

function readPage(value: string | null) {
  const page = Number(value);
  return Number.isInteger(page) && page > 0 ? page : 1;
}

function verificationLabel(status?: string) {
  const value = status?.toLowerCase() || 'unverified';
  switch (value) {
    case 'verified':
      return { text: 'Đã duyệt', className: 'status-verified' };
    case 'pending':
      return { text: 'Chờ duyệt', className: 'status-pending' };
    case 'rejected':
      return { text: 'Bị từ chối', className: 'status-rejected' };
    default:
      return { text: 'Chưa xác thực', className: 'status-unverified' };
  }
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

export default function AdminCompanyReviewPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [filter, setFilter] = useState<CompanyReviewFilter>(() => readFilter(searchParams.get('status')));
  const [page, setPage] = useState(() => readPage(searchParams.get('page')));
  const [companies, setCompanies] = useState<AdminCompanySummary[]>([]);
  const [loadingList, setLoadingList] = useState(true);
  const [error, setError] = useState('');

  const loadCompanies = useCallback(async () => {
    setLoadingList(true);
    setError('');
    try {
      const data = await adminService.listCompanies(filter);
      setCompanies(data);
    } catch (err) {
      setError(readError(err));
      setCompanies([]);
    } finally {
      setLoadingList(false);
    }
  }, [filter]);

  useEffect(() => {
    loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    setSearchParams({ status: filter, page: String(page) }, { replace: true });
  }, [filter, page, setSearchParams]);

  useEffect(() => {
    const totalPages = Math.max(1, Math.ceil(companies.length / PAGE_SIZE));
    if (page > totalPages) {
      setPage(totalPages);
    }
  }, [companies.length, page]);

  const totalPages = Math.max(1, Math.ceil(companies.length / PAGE_SIZE));
  const paginatedCompanies = companies.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <h1>Duyệt hồ sơ công ty</h1>
        <p className="muted">Xem xét, phê duyệt hoặc từ chối hồ sơ xác thực pháp lý của nhà tuyển dụng.</p>
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
        <button type="button" className="outline" onClick={loadCompanies}>
          Làm mới
        </button>
      </div>

      {error && <p className="error admin-inline-message">{error}</p>}

      <div className="admin-review-layout">
        <section className="admin-company-list-panel admin-review-list-panel">
          <div className="admin-company-list-header">
            <h2>Danh sách công ty</h2>
            <span>{companies.length} hồ sơ</span>
          </div>

          {loadingList ? (
            <p className="loading">Đang tải danh sách...</p>
          ) : companies.length === 0 ? (
            <div className="admin-placeholder-card">
              <p>Không có hồ sơ nào trong mục này.</p>
            </div>
          ) : (
            <div className="admin-review-list">
              {paginatedCompanies.map((company) => {
                const badge = verificationLabel(company.verificationStatus);
                return (
                  <article
                    key={company.id}
                    className="admin-review-row"
                  >
                    <div className="admin-review-main">
                      <div className="admin-review-title-line">
                        <strong>{company.name}</strong>
                        <span className={`admin-status-badge ${badge.className}`}>{badge.text}</span>
                      </div>
                      <p>{company.ownerName || company.ownerEmail || 'Chưa có người đại diện'}</p>
                    </div>
                    <div className="admin-review-meta">
                      <span>{company.industry || 'Chưa cập nhật ngành'}</span>
                      <span>{company.pendingDocumentCount} tài liệu chờ</span>
                    </div>
                    <Link className="button-link outline" to={`/admin/companies/${company.id}?status=${filter}&page=${page}`}>
                      Xem chi tiết
                    </Link>
                  </article>
                );
              })}
            </div>
          )}

          {companies.length > PAGE_SIZE && (
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
