import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminCompanyDetail } from '../../types/admin';
import type { CompanyDocument } from '../../types/job';

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

export default function AdminCompanyDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [detail, setDetail] = useState<AdminCompanyDetail | null>(null);
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
    adminService.getCompanyDetail(id)
      .then(setDetail)
      .catch((err) => setError(readError(err)))
      .finally(() => setLoading(false));
  }, [id]);

  async function handleApprove() {
    if (!id || !detail) return;
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

  const verification = verificationLabel(detail?.company.verificationStatus);
  const canReview = detail?.company.verificationStatus?.toLowerCase() === 'pending';
  const backStatus = searchParams.get('status') || 'pending';
  const backPage = searchParams.get('page') || '1';
  const backTo = `/admin/companies?status=${backStatus}&page=${backPage}`;

  return (
    <section className="admin-page">
      <div className="admin-detail-topbar">
        <button type="button" className="outline" onClick={() => navigate(backTo)}>
          Quay lại
        </button>
        <Link className="button-link outline" to={backTo}>
          Danh sách hồ sơ
        </Link>
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
            <div>
              <h1>{detail.company.name}</h1>
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
            <h3>Tài liệu pháp lý ({detail.documents.length})</h3>
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
              <button type="button" className="danger" onClick={() => setShowRejectForm((value) => !value)} disabled={submitting}>
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
