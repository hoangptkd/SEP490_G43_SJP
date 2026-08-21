import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminCompanyDetail } from '../../types/admin';
import type { CompanyDocument } from '../../types/job';
import { downloadFile, openFileInNewTab } from '../../utils/helpers';

import { FiBuilding } from '../../components/Icons';

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

type DocumentRowProps = {
  doc: CompanyDocument;
  canReviewDoc: boolean;
  reviewingId: string | null;
  rejectDocId: string | null;
  rejectDocReason: string;
  onApprove: (doc: CompanyDocument) => void;
  onToggleReject: (docId: string | null) => void;
  onRejectReasonChange: (value: string) => void;
  onRejectSubmit: (doc: CompanyDocument) => void;
};

function DocumentRow({
  doc,
  canReviewDoc,
  reviewingId,
  rejectDocId,
  rejectDocReason,
  onApprove,
  onToggleReject,
  onRejectReasonChange,
  onRejectSubmit,
}: DocumentRowProps) {
  const status = documentStatusLabel(doc.status);
  const isPending = (doc.status || 'pending').toLowerCase() === 'pending';
  const busy = reviewingId === doc.id;
  const showRejectForm = rejectDocId === doc.id;
  const [downloading, setDownloading] = useState(false);
  const hasFile = Boolean(doc.fileUrl);

  return (
    <article className={`admin-company-doc ${isPending ? 'is-pending' : ''}`}>
      <div className="admin-company-doc-icon">{doc.fileType === 'pdf' ? 'PDF' : 'IMG'}</div>
      <div className="admin-company-doc-body">
        <strong>{doc.fileName}</strong>
        <div className="admin-company-doc-meta">
          <span>Tải lên: {formatDate(doc.uploadedAt)}</span>
          {doc.reviewedAt && <span>Duyệt: {formatDate(doc.reviewedAt)}</span>}
          <span className={`admin-status-badge ${status.className}`}>{status.text}</span>
        </div>
        {doc.rejectReason && (
          <p className="admin-company-doc-reason">Lý do từ chối: {doc.rejectReason}</p>
        )}

        {showRejectForm && (
          <form
            className="admin-company-doc-reject"
            onSubmit={(event) => {
              event.preventDefault();
              onRejectSubmit(doc);
            }}
          >
            <label>
              Lý do từ chối tài liệu này
              <textarea
                value={rejectDocReason}
                onChange={(e) => onRejectReasonChange(e.target.value)}
                placeholder="Ví dụ: Ảnh mờ, giấy hết hạn, sai MST..."
                required
              />
            </label>
            <div className="admin-company-doc-actions">
              <button type="submit" className="danger" disabled={busy}>
                {busy ? 'Đang xử lý...' : 'Xác nhận từ chối'}
              </button>
              <button type="button" className="outline" onClick={() => onToggleReject(null)} disabled={busy}>
                Hủy
              </button>
            </div>
          </form>
        )}
      </div>
      <div className="admin-company-doc-actions">
        <button
          type="button"
          className="outline"
          disabled={!hasFile}
          title={!hasFile ? 'Không có đường dẫn file' : undefined}
          onClick={() => openFileInNewTab(doc.fileUrl)}
        >
          Xem
        </button>
        <button
          type="button"
          className="outline"
          disabled={!hasFile || downloading}
          title={!hasFile ? 'Không có đường dẫn file' : undefined}
          onClick={async () => {
            setDownloading(true);
            try {
              await downloadFile(doc.fileUrl, doc.fileName);
            } finally {
              setDownloading(false);
            }
          }}
        >
          {downloading ? 'Đang tải...' : 'Tải về'}
        </button>
        {canReviewDoc && isPending && !showRejectForm && (
          <>
            <button type="button" onClick={() => onApprove(doc)} disabled={busy}>
              {busy ? '...' : 'Phê duyệt'}
            </button>
            <button type="button" className="danger" onClick={() => onToggleReject(doc.id)} disabled={busy}>
              Từ chối
            </button>
          </>
        )}
      </div>
    </article>
  );
}

export default function AdminCompanyDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [detail, setDetail] = useState<AdminCompanyDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [reviewingId, setReviewingId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [rejectDocId, setRejectDocId] = useState<string | null>(null);
  const [rejectDocReason, setRejectDocReason] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    setError('');
    adminService.getCompanyDetail(id)
      .then(setDetail)
      .catch((err) => setError(readError(err)))
      .finally(() => setLoading(false));
  }, [id]);

  const pendingDocs = useMemo(
    () => (detail?.documents || []).filter((doc) => (doc.status || 'pending').toLowerCase() === 'pending'),
    [detail],
  );
  const hasMultipleDocs = (detail?.documents.length || 0) >= 2;
  const canReviewCompany = detail?.company.verificationStatus?.toLowerCase() === 'pending'
    || detail?.company.verificationStatus?.toLowerCase() === 'rejected';
  const canReviewDocuments =
    detail?.company.verificationStatus?.toLowerCase() === 'pending'
    || detail?.company.verificationStatus?.toLowerCase() === 'rejected';
  const companyApproveBlocked =
    pendingDocs.length > 0
    || (detail?.documents || []).some((doc) => (doc.status || '').toLowerCase() === 'rejected');

  async function handleApprove() {
    if (!id || !detail) return;
    if (companyApproveBlocked) {
      setError('Hãy phê duyệt hoặc từ chối từng tài liệu pháp lý trước khi duyệt hồ sơ công ty.');
      return;
    }
    if (!window.confirm(`Phê duyệt hồ sơ công ty "${detail.company.name}"?`)) return;

    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.approveCompany(id);
      setDetail(updated);
      setSuccess('Đã phê duyệt hồ sơ công ty thành công.');
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
      const updated = await adminService.rejectCompany(id, rejectReason.trim());
      setDetail(updated);
      setSuccess('Đã từ chối hồ sơ công ty.');
      setShowRejectForm(false);
      setRejectReason('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleApproveDocument(doc: CompanyDocument) {
    if (!id) return;
    if (!window.confirm(`Phê duyệt tài liệu "${doc.fileName}"?`)) return;

    setReviewingId(doc.id);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.approveCompanyDocument(id, doc.id);
      setDetail(updated);
      const stillPending = (updated.documents || []).some(
        (d) => (d.status || 'pending').toLowerCase() === 'pending',
      );
      const hasRejected = (updated.documents || []).some(
        (d) => (d.status || '').toLowerCase() === 'rejected',
      );
      if (!stillPending && !hasRejected && updated.company.verificationStatus?.toLowerCase() === 'pending') {
        setSuccess(`Đã phê duyệt tài liệu "${doc.fileName}". Bạn có thể bấm "Phê duyệt hồ sơ công ty" để hoàn tất.`);
      } else {
        setSuccess(`Đã phê duyệt tài liệu "${doc.fileName}".`);
      }
      setRejectDocId(null);
      setRejectDocReason('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setReviewingId(null);
    }
  }

  async function handleRejectDocument(doc: CompanyDocument) {
    if (!id) return;
    if (!rejectDocReason.trim()) {
      setError('Vui lòng nhập lý do từ chối tài liệu.');
      return;
    }

    setReviewingId(doc.id);
    setError('');
    setSuccess('');
    try {
      const updated = await adminService.rejectCompanyDocument(id, doc.id, rejectDocReason.trim());
      setDetail(updated);
      setSuccess(`Đã từ chối tài liệu "${doc.fileName}".`);
      setRejectDocId(null);
      setRejectDocReason('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setReviewingId(null);
    }
  }

  const verification = verificationLabel(detail?.company.verificationStatus);
  const backStatus = searchParams.get('status') || 'pending';
  const backPage = searchParams.get('page') || '1';
  const backTo = `/admin/companies?status=${backStatus}&page=${backPage}`;

  return (
    <section className="admin-page">
      <header className="admin-page-intro">
        <div className="admin-page-intro-copy">
          <p className="admin-page-intro-eyebrow">Chi tiết hồ sơ công ty</p>
          <h1>{loading ? 'Đang tải...' : detail?.company.name || 'Hồ sơ công ty'}</h1>
          <p>
            {detail
              ? `Xem xét và xử lý hồ sơ xác thực pháp lý · Cập nhật ${formatDate(detail.updatedAt)}`
              : 'Xem xét và xử lý hồ sơ xác thực pháp lý của nhà tuyển dụng.'}
          </p>
        </div>
        <div className="admin-page-intro-aside">
          <div className="admin-page-intro-stat">
            <span>Trạng thái</span>
            <strong>{detail ? verification.text : '—'}</strong>
          </div>
          <div className="admin-page-intro-stat">
            <span>Tài liệu chờ</span>
            <strong>{detail?.documents?.filter((d) => String(d.status || '').toLowerCase() === 'pending').length ?? 0}</strong>
          </div>
        </div>
      </header>

      <div className="admin-toolbar">
        <div className="admin-toolbar-group">
          <button type="button" className="outline" onClick={() => navigate(backTo)}>
            ← Quay lại
          </button>
          <Link className="button-link outline" to={backTo}>
            Danh sách hồ sơ
          </Link>
        </div>
      </div>

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      {loading ? (
        <p className="loading">Đang tải chi tiết hồ sơ...</p>
      ) : !detail ? (
        <div className="admin-placeholder-card">
          <p>Không tìm thấy hồ sơ công ty.</p>
        </div>
      ) : (
        <section className="admin-company-detail-panel admin-readable-detail">
          <div className="admin-company-detail-header">
            <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
              {detail.company.logoUrl ? (
                <img
                  src={detail.company.logoUrl}
                  alt={detail.company.name}
                  style={{ width: 72, height: 72, borderRadius: 12, objectFit: 'cover', border: '1px solid #e2e8f0' }}
                />
              ) : (
                <div
                  style={{
                    width: 72,
                    height: 72,
                    borderRadius: 12,
                    background: '#f1f5f9',
                    display: 'grid',
                    placeItems: 'center',
                    fontWeight: 700,
                    color: '#64748b',
                  }}
                >
                  <FiBuilding className="w-8 h-8 text-slate-400" />
                </div>
              )}
              <div>
                <h2>Thông tin công ty</h2>
                <p className="muted">
                  Nộp hồ sơ: {formatDate(detail.createdAt)} · Cập nhật lần cuối: {formatDate(detail.updatedAt)}
                </p>
              </div>
            </div>
            <span className={`admin-status-badge ${verification.className}`}>{verification.text}</span>
          </div>

          <div className="admin-company-info-grid">
            <div>
              <span>Ngành nghề hoạt động</span>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginTop: '4px' }}>
                {detail.company.industries && detail.company.industries.length > 0 ? (
                  detail.company.industries.map((ind) => (
                    <span
                      key={ind.categoryId || ind.categoryName}
                      style={{
                        padding: '3px 10px',
                        borderRadius: '16px',
                        fontSize: '0.8rem',
                        fontWeight: 600,
                        background: ind.primary ? '#eff6ff' : '#f1f5f9',
                        color: ind.primary ? '#1d4ed8' : '#475569',
                        border: ind.primary ? '1px solid #bfdbfe' : '1px solid #e2e8f0',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '4px',
                      }}
                    >
                      {ind.categoryName || detail.company.industry}
                      {ind.primary && <span style={{ fontSize: '0.75rem', color: '#2563eb' }}>★ Chính</span>}
                    </span>
                  ))
                ) : (
                  <strong>{detail.company.industry || '—'}</strong>
                )}
              </div>
            </div>
            <div><span>Mã số thuế</span><strong>{detail.company.taxCode || '—'}</strong></div>
            <div><span>Quy mô</span><strong>{detail.company.companySize ? `${detail.company.companySize} nhân sự` : '—'}</strong></div>
            <div><span>Website</span><strong>{detail.company.website || '—'}</strong></div>
            <div><span>Trụ sở</span><strong>{detail.company.location || '—'}</strong></div>
            <div><span>Email liên hệ</span><strong>{detail.company.contactEmail || '—'}</strong></div>
            <div><span>SĐT liên hệ</span><strong>{detail.company.contactPhone || '—'}</strong></div>
            <div><span>Trạng thái hệ thống</span><strong>{detail.company.status || '—'}</strong></div>
          </div>

          {detail.company.locations && detail.company.locations.length > 0 && (
            <div className="admin-company-section">
              <h3>Chi nhánh / địa điểm ({detail.company.locations.length})</h3>
              <div className="admin-review-list">
                {detail.company.locations.map((loc) => (
                  <article key={loc.id} className="admin-review-row" style={{ marginBottom: 8 }}>
                    <div className="admin-review-main">
                      <strong>
                        {loc.branchName || 'Chi nhánh'}
                        {loc.headquarter && (
                          <span className="admin-status-badge status-verified" style={{ marginLeft: 8 }}>
                            Trụ sở chính
                          </span>
                        )}
                      </strong>
                      <p className="muted" style={{ margin: '4px 0 0' }}>
                        {[loc.address, loc.district, loc.city, loc.country].filter(Boolean).join(', ') || '—'}
                      </p>
                    </div>
                  </article>
                ))}
              </div>
            </div>
          )}

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
            <div className="admin-company-section-head">
              <h3>Tài liệu pháp lý ({detail.documents.length})</h3>
              {hasMultipleDocs && canReviewDocuments && (
                <p className="muted">
                  Có từ 2 tài liệu trở lên — hãy phê duyệt / từ chối từng file.
                  {pendingDocs.length > 0
                    ? ` Còn ${pendingDocs.length} tài liệu chờ xử lý.`
                    : ' Đã xử lý hết tài liệu. Bạn có thể quyết định cuối ở cấp hồ sơ công ty nếu cần.'}
                </p>
              )}
            </div>
            {detail.documents.length === 0 ? (
              <p className="muted">Công ty chưa tải lên tài liệu xác thực.</p>
            ) : (
              <div className="admin-company-doc-list">
                {detail.documents.map((doc) => (
                  <DocumentRow
                    key={doc.id}
                    doc={doc}
                    canReviewDoc={canReviewDocuments}
                    reviewingId={reviewingId}
                    rejectDocId={rejectDocId}
                    rejectDocReason={rejectDocReason}
                    onApprove={handleApproveDocument}
                    onToggleReject={(docId) => {
                      setRejectDocId(docId);
                      setRejectDocReason('');
                      setError('');
                    }}
                    onRejectReasonChange={setRejectDocReason}
                    onRejectSubmit={handleRejectDocument}
                  />
                ))}
              </div>
            )}
          </div>

          {canReviewCompany && (
            <div className="admin-company-actions">
              <button type="button" onClick={handleApprove} disabled={submitting || companyApproveBlocked}>
                {submitting ? 'Đang xử lý...' : 'Phê duyệt hồ sơ công ty'}
              </button>
              <button
                type="button"
                className="danger"
                onClick={() => setShowRejectForm((value) => !value)}
                disabled={submitting}
              >
                Từ chối hồ sơ công ty
              </button>
            </div>
          )}

          {companyApproveBlocked && (
            <p className="muted admin-inline-message">
              Không thể duyệt hồ sơ khi còn tài liệu chờ xử lý hoặc bị từ chối.
              Hãy duyệt từng tài liệu, hoặc chờ nhà tuyển dụng cập nhật lại file bị từ chối.
            </p>
          )}

          {showRejectForm && canReviewCompany && (
            <form className="admin-company-reject-form" onSubmit={handleReject}>
              <label>
                Lý do từ chối hồ sơ công ty
                <textarea
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="Nhập lý do từ chối để nhà tuyển dụng biết và bổ sung lại..."
                  required
                />
              </label>
              <div className="admin-company-actions">
                <button type="submit" className="danger" disabled={submitting}>
                  {submitting ? 'Đang xử lý...' : 'Xác nhận từ chối hồ sơ'}
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
