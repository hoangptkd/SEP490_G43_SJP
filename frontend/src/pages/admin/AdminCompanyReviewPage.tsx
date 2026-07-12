import { useCallback, useEffect, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminCompanyDetail, AdminCompanySummary, CompanyReviewFilter } from '../../types/admin';
import type { CompanyDocument } from '../../types/job';

const filters: { value: CompanyReviewFilter; label: string }[] = [
  { value: 'pending', label: 'Chờ duyệt' },
  { value: 'verified', label: 'Đã duyệt' },
  { value: 'rejected', label: 'Bị từ chối' },
];

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

function documentStatusLabel(status?: string) {
  const value = status?.toLowerCase() || 'pending';
  if (value === 'approved') return { text: 'Đã duyệt', className: 'status-verified' };
  if (value === 'rejected') return { text: 'Bị từ chối', className: 'status-rejected' };
  return { text: 'Chờ duyệt', className: 'status-pending' };
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

function DocumentRow({ doc }: { doc: CompanyDocument }) {
  const status = documentStatusLabel(doc.status);
  const viewUrl = doc.fileType === 'pdf' || doc.fileName?.toLowerCase().endsWith('.pdf')
    ? `https://docs.google.com/gview?url=${encodeURIComponent(doc.fileUrl)}`
    : doc.fileUrl;

  return (
    <article className="admin-company-doc">
      <div className="admin-company-doc-icon">{doc.fileType === 'pdf' ? 'PDF' : 'IMG'}</div>
      <div className="admin-company-doc-body">
        <strong>{doc.fileName}</strong>
        <div className="admin-company-doc-meta">
          <span>{formatDate(doc.uploadedAt)}</span>
          <span className={`admin-status-badge ${status.className}`}>{status.text}</span>
        </div>
        {doc.rejectReason && (
          <p className="admin-company-doc-reason">Lý do từ chối: {doc.rejectReason}</p>
        )}
      </div>
      <div className="admin-company-doc-actions">
        <a href={viewUrl} target="_blank" rel="noopener noreferrer" className="button-link outline">
          Xem
        </a>
        <a href={doc.fileUrl} target="_blank" rel="noopener noreferrer" className="button-link outline">
          Tải về
        </a>
      </div>
    </article>
  );
}

export default function AdminCompanyReviewPage() {
  const [filter, setFilter] = useState<CompanyReviewFilter>('pending');
  const [companies, setCompanies] = useState<AdminCompanySummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<AdminCompanyDetail | null>(null);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadCompanies = useCallback(async () => {
    setLoadingList(true);
    setError('');
    try {
      const data = await adminService.listCompanies(filter);
      setCompanies(data);
      setSelectedId((prev) => {
        if (data.length === 0) return null;
        if (prev && data.some((item) => item.id === prev)) return prev;
        return data[0].id;
      });
    } catch (err) {
      setError(readError(err));
      setCompanies([]);
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
      const data = await adminService.getCompanyDetail(id);
      setDetail(data);
    } catch (err) {
      setError(readError(err));
      setDetail(null);
    } finally {
      setLoadingDetail(false);
    }
  }, []);

  useEffect(() => {
    loadCompanies();
  }, [loadCompanies]);

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
    if (!window.confirm(`Phê duyệt hồ sơ công ty "${detail.company.name}"?`)) return;

    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.approveCompany(selectedId);
      setDetail(updated);
      setSuccess('Đã phê duyệt hồ sơ công ty thành công.');
      await loadCompanies();
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
      const updated = await adminService.rejectCompany(selectedId, rejectReason.trim());
      setDetail(updated);
      setSuccess('Đã từ chối hồ sơ công ty.');
      setShowRejectForm(false);
      setRejectReason('');
      await loadCompanies();
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  }

  const selectedSummary = companies.find((item) => item.id === selectedId);
  const verification = verificationLabel(detail?.company.verificationStatus);
  const canReview = detail?.company.verificationStatus?.toLowerCase() === 'pending';

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
            onClick={() => setFilter(item.value)}
          >
            {item.label}
          </button>
        ))}
        <button type="button" className="outline" onClick={loadCompanies}>
          Làm mới
        </button>
      </div>

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      <div className="admin-company-layout">
        <aside className="admin-company-list-panel">
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
            <div className="admin-company-list">
              {companies.map((company) => {
                const badge = verificationLabel(company.verificationStatus);
                return (
                  <button
                    key={company.id}
                    type="button"
                    className={`admin-company-list-item${selectedId === company.id ? ' active' : ''}`}
                    onClick={() => setSelectedId(company.id)}
                  >
                    <div className="admin-company-list-item-top">
                      <strong>{company.name}</strong>
                      <span className={`admin-status-badge ${badge.className}`}>{badge.text}</span>
                    </div>
                    <p>{company.ownerName || company.ownerEmail || 'Chưa có người đại diện'}</p>
                    <div className="admin-company-list-item-meta">
                      <span>{company.industry || 'Chưa cập nhật ngành'}</span>
                      <span>{company.pendingDocumentCount} tài liệu chờ</span>
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
              <p>Chọn một công ty để xem chi tiết hồ sơ.</p>
            </div>
          ) : loadingDetail || !detail ? (
            <p className="loading">Đang tải chi tiết hồ sơ...</p>
          ) : (
            <>
              <div className="admin-company-detail-header">
                <div>
                  <h2>{detail.company.name}</h2>
                  <p className="muted">Cập nhật lần cuối: {formatDate(detail.updatedAt)}</p>
                </div>
                <span className={`admin-status-badge ${verification.className}`}>{verification.text}</span>
              </div>

              <div className="admin-company-info-grid">
                <div><span>Ngành nghề</span><strong>{detail.company.industry || '—'}</strong></div>
                <div><span>Mã số thuế</span><strong>{detail.company.taxCode || '—'}</strong></div>
                <div><span>Quy mô</span><strong>{detail.company.companySize ? `${detail.company.companySize} nhân sự` : '—'}</strong></div>
                <div><span>Website</span><strong>{detail.company.website || '—'}</strong></div>
                <div><span>Trụ sở</span><strong>{detail.company.location || '—'}</strong></div>
                <div><span>Trạng thái hệ thống</span><strong>{detail.company.status || '—'}</strong></div>
              </div>

              {detail.company.description && (
                <div className="admin-company-section">
                  <h3>Mô tả công ty</h3>
                  <p>{detail.company.description}</p>
                </div>
              )}

              {detail.owner && (
                <div className="admin-company-section">
                  <h3>Người đại diện</h3>
                  <div className="admin-company-info-grid">
                    <div><span>Họ tên</span><strong>{detail.owner.fullName || '—'}</strong></div>
                    <div><span>Email</span><strong>{detail.owner.email}</strong></div>
                    <div><span>Số điện thoại</span><strong>{detail.owner.phone || '—'}</strong></div>
                    <div><span>Chức vụ</span><strong>{detail.owner.position || '—'}</strong></div>
                  </div>
                </div>
              )}

              <div className="admin-company-section">
                <div className="admin-company-section-header">
                  <h3>Tài liệu pháp lý ({detail.documents.length})</h3>
                </div>

                {detail.documents.length === 0 ? (
                  <p className="muted">Công ty chưa tải lên tài liệu xác thực.</p>
                ) : (
                  <div className="admin-company-doc-list">
                    {detail.documents.map((doc) => (
                      <DocumentRow key={doc.id} doc={doc} />
                    ))}
                  </div>
                )}
              </div>

              {canReview && (
                <div className="admin-company-actions">
                  <button type="button" onClick={handleApprove} disabled={submitting}>
                    {submitting ? 'Đang xử lý...' : 'Phê duyệt hồ sơ'}
                  </button>
                  <button
                    type="button"
                    className="danger"
                    onClick={() => setShowRejectForm((value) => !value)}
                    disabled={submitting}
                  >
                    Từ chối hồ sơ
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
                      placeholder="Nhập lý do từ chối để nhà tuyển dụng biết và bổ sung lại..."
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
