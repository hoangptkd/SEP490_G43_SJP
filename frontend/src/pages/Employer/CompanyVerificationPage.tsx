import { useEffect, useState, useRef } from 'react';
import { employerService } from '../../services/employerService';
import type { Company, CompanyDocument } from '../../types/job';

function CompanyVerificationPage() {
  const [company, setCompany] = useState<Company | null>(null);
  const [documents, setDocuments] = useState<CompanyDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setLoading(true);
    setError('');
    try {
      const [compData, docsData] = await Promise.all([
        employerService.getCompanyProfile(),
        employerService.getDocuments(),
      ]);
      setCompany(compData);
      setDocuments(docsData);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Không thể tải thông tin xác thực pháp lý');
    } finally {
      setLoading(false);
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    if (e.target.files && e.target.files.length > 0) {
      const file = e.target.files[0];
      const validTypes = ['application/pdf', 'image/jpeg', 'image/png', 'image/jpg'];
      if (!validTypes.includes(file.type) && !file.name.endsWith('.pdf')) {
        setError('Chỉ chấp nhận định dạng file PDF hoặc hình ảnh (JPG, PNG)');
        return;
      }
      if (file.size > 10 * 1024 * 1024) { // 10MB
        setError('Dung lượng file tối đa là 10MB');
        return;
      }
      setSelectedFile(file);
      setError('');
    }
  }

  async function handleUpload(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedFile) {
      setError('Vui lòng chọn file để tải lên');
      return;
    }
    setUploading(true);
    setError('');
    setSuccess('');
    try {
      await employerService.uploadDocument(selectedFile);
      setSuccess('Tải lên tài liệu thành công! Trạng thái hồ sơ đã chuyển sang Chờ duyệt.');
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      await loadData();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Lỗi khi tải lên tài liệu. Vui lòng thử lại.');
    } finally {
      setUploading(false);
    }
  }

  async function handleDelete(id: string) {
    if (!window.confirm('Bạn có chắc chắn muốn xóa tài liệu này?')) return;
    setDeletingId(id);
    setError('');
    try {
      await employerService.deleteDocument(id);
      setSuccess('Đã xóa tài liệu xác thực.');
      await loadData();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Không thể xóa tài liệu này');
    } finally {
      setDeletingId(null);
    }
  }

  function getStatusBadge(status?: string) {
    const s = status?.toUpperCase() || 'UNVERIFIED';
    switch (s) {
      case 'VERIFIED':
      case 'APPROVED':
        return {
          text: 'Đã xác thực pháp lý',
          color: '#047857',
          bg: '#ecfdf5',
          border: '#a7f3d0',
          dot: '#10b981',
          desc: 'Tuyệt vời! Doanh nghiệp của bạn đã hoàn tất kiểm duyệt pháp lý thành công. Huy hiệu xác thực được hiển thị công khai trên tất cả tin tuyển dụng.'
        };
      case 'PENDING':
      case 'PENDING_REVIEW':
        return {
          text: 'Chờ kiểm duyệt hồ sơ',
          color: '#1d4ed8',
          bg: '#eff6ff',
          border: '#bfdbfe',
          dot: '#3b82f6',
          desc: 'Tài liệu pháp lý đang được Bộ phận kiểm duyệt rà soát. Quá trình kiểm duyệt thường hoàn tất trong vòng 24 giờ làm việc.'
        };
      case 'REJECTED':
        return {
          text: 'Yêu cầu cập nhật tài liệu',
          color: '#b91c1c',
          bg: '#fef2f2',
          border: '#fecaca',
          dot: '#ef4444',
          desc: 'Tài liệu không đạt yêu cầu hoặc chưa đủ thông tin. Vui lòng kiểm tra phản hồi từ bộ phận kiểm duyệt và gửi lại hồ sơ hợp lệ.'
        };
      default:
        return {
          text: 'Chưa xác thực',
          color: '#475569',
          bg: '#f8fafc',
          border: '#cbd5e1',
          dot: '#94a3b8',
          desc: 'Vui lòng tải lên Giấy phép đăng ký kinh doanh hoặc Mã số thuế doanh nghiệp (File PDF hoặc hình ảnh) để tiến hành xác thực.'
        };
    }
  }

  const badgeInfo = getStatusBadge(company?.verificationStatus);

  if (loading) {
    return (
      <div className="card" style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
        <p style={{ margin: 0, fontSize: '1rem' }}>Đang tải thông tin xác thực doanh nghiệp...</p>
      </div>
    );
  }

  return (
    <div className="card" style={{ padding: '32px', borderRadius: '10px', border: '1px solid #e2e8f0', background: '#fff', boxShadow: '0 1px 3px rgba(0,0,0,0.02)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', flexWrap: 'wrap', gap: '16px', borderBottom: '1px solid #e2e8f0', paddingBottom: '16px' }}>
        <div>
          <h1 style={{ margin: '0 0 8px 0', fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>Xác thực pháp lý doanh nghiệp</h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.95rem' }}>
            Tải lên Giấy chứng nhận đăng ký doanh nghiệp hoặc tài liệu thuế định danh tổ chức
          </p>
        </div>
        <button
          onClick={loadData}
          style={{
            background: '#f8fafc',
            border: '1px solid #cbd5e1',
            color: '#334155',
            padding: '8px 16px',
            borderRadius: '6px',
            cursor: 'pointer',
            fontSize: '0.875rem',
            fontWeight: 600,
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            transition: 'all 0.2s'
          }}
        >
          Làm mới dữ liệu
        </button>
      </div>

      {error && (
        <div style={{ background: '#fef2f2', color: '#991b1b', padding: '14px 18px', borderRadius: '8px', marginBottom: '20px', border: '1px solid #fecaca', borderLeft: '4px solid #dc2626', fontSize: '0.9rem' }}>
          {error}
        </div>
      )}

      {success && (
        <div style={{ background: '#ecfdf5', color: '#047857', padding: '14px 18px', borderRadius: '8px', marginBottom: '20px', border: '1px solid #a7f3d0', borderLeft: '4px solid #10b981', fontSize: '0.9rem' }}>
          {success}
        </div>
      )}

      {/* Status Banner */}
      <div style={{
        background: badgeInfo.bg,
        color: badgeInfo.color,
        border: `1px solid ${badgeInfo.border}`,
        padding: '22px 24px',
        borderRadius: '8px',
        marginBottom: '28px',
        display: 'flex',
        alignItems: 'flex-start',
        gap: '16px'
      }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px', flexWrap: 'wrap' }}>
            <span style={{ fontWeight: 700, fontSize: '0.95rem', color: '#0f172a' }}>Trạng thái hồ sơ:</span>
            <span style={{
              background: '#ffffff',
              color: badgeInfo.color,
              border: `1px solid ${badgeInfo.border}`,
              padding: '4px 14px',
              borderRadius: '20px',
              fontWeight: 600,
              fontSize: '0.85rem',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px'
            }}>
              <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: badgeInfo.dot }}></span>
              {badgeInfo.text}
            </span>
          </div>
          <p style={{ margin: 0, fontSize: '0.9rem', lineHeight: 1.6, opacity: 0.9 }}>{badgeInfo.desc}</p>
        </div>
      </div>

      {/* Upload Box */}
      <div style={{
        border: '2px dashed #cbd5e1',
        borderRadius: '10px',
        padding: '36px 24px',
        textAlign: 'center',
        background: '#f8fafc',
        marginBottom: '32px',
        transition: 'border-color 0.2s'
      }}>
        <h3 style={{ margin: '0 0 8px 0', fontSize: '1.1rem', color: '#0f172a', fontWeight: 600 }}>
          Tải lên tài liệu xác thực (PDF, JPG, PNG)
        </h3>
        <p style={{ margin: '0 0 20px 0', color: '#64748b', fontSize: '0.875rem' }}>
          Hỗ trợ tệp tin tối đa 10MB. Tài liệu được mã hóa và bảo mật tuyệt đối trên hệ thống cloud.
        </p>

        <form onSubmit={handleUpload} style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center', gap: '14px' }}>
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
            style={{ display: 'none' }}
            id="doc-upload-input"
          />
          <label
            htmlFor="doc-upload-input"
            style={{
              background: '#ffffff',
              border: '1px solid #cbd5e1',
              padding: '10px 22px',
              borderRadius: '6px',
              cursor: 'pointer',
              fontWeight: 500,
              color: '#334155',
              fontSize: '0.9rem',
              boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
              display: 'inline-block',
              transition: 'all 0.2s'
            }}
          >
            {selectedFile ? `Đã chọn: ${selectedFile.name} (${(selectedFile.size / 1024 / 1024).toFixed(2)} MB)` : '+ Chọn tệp từ máy tính'}
          </label>

          {selectedFile && (
            <button
              type="submit"
              disabled={uploading}
              style={{
                background: '#2563eb',
                color: '#fff',
                border: 'none',
                padding: '10px 26px',
                borderRadius: '6px',
                cursor: uploading ? 'not-allowed' : 'pointer',
                fontWeight: 600,
                fontSize: '0.9rem',
                boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)',
                transition: 'background-color 0.2s'
              }}
            >
              {uploading ? 'Đang tải lên hệ thống...' : 'Tải lên & Nộp kiểm duyệt'}
            </button>
          )}
        </form>
      </div>

      {/* Document List */}
      <h3 style={{ fontSize: '1.2rem', margin: '0 0 16px 0', color: '#0f172a', fontWeight: 700, borderBottom: '1px solid #e2e8f0', paddingBottom: '12px' }}>
        Danh sách tài liệu đã gửi ({documents.length})
      </h3>

      {documents.length === 0 ? (
        <p style={{ textAlign: 'center', padding: '36px 0', color: '#64748b', margin: 0, fontSize: '0.95rem' }}>
          Chưa có tài liệu nào được tải lên. Vui lòng chọn và tải lên tài liệu pháp lý ở khung phía trên.
        </p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {documents.map((doc) => {
            const docStatus = doc.status?.toLowerCase() || 'pending';
            const statusStyle = docStatus === 'approved' || docStatus === 'verified'
              ? { bg: '#ecfdf5', color: '#047857', border: '#a7f3d0', label: 'Đã hợp lệ' }
              : docStatus === 'rejected'
              ? { bg: '#fef2f2', color: '#b91c1c', border: '#fecaca', label: 'Yêu cầu cập nhật' }
              : { bg: '#eff6ff', color: '#1d4ed8', border: '#bfdbfe', label: 'Chờ kiểm duyệt' };

            return (
              <div
                key={doc.id}
                style={{
                  border: '1px solid #e2e8f0',
                  borderRadius: '8px',
                  padding: '18px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: '#ffffff',
                  boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
                  flexWrap: 'wrap',
                  gap: '16px'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flex: '1 1 300px' }}>
                  <div style={{
                    width: '48px',
                    height: '48px',
                    borderRadius: '8px',
                    background: doc.fileType === 'pdf' ? '#fef2f2' : '#eff6ff',
                    color: doc.fileType === 'pdf' ? '#dc2626' : '#2563eb',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '0.85rem',
                    fontWeight: 700,
                    border: doc.fileType === 'pdf' ? '1px solid #fecaca' : '1px solid #bfdbfe',
                    flexShrink: 0
                  }}>
                    {doc.fileType === 'pdf' ? 'PDF' : 'IMG'}
                  </div>
                  <div>
                    <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem', marginBottom: '6px' }}>
                      {doc.fileName}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#64748b', display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
                      <span>Thời gian: {new Date(doc.uploadedAt).toLocaleString('vi-VN')}</span>
                      <span style={{
                        background: statusStyle.bg,
                        color: statusStyle.color,
                        border: `1px solid ${statusStyle.border}`,
                        padding: '2px 10px',
                        borderRadius: '12px',
                        fontWeight: 600,
                        fontSize: '0.75rem'
                      }}>
                        {statusStyle.label}
                      </span>
                    </div>
                    {doc.rejectReason && (
                      <div style={{ color: '#b91c1c', fontSize: '0.85rem', marginTop: '8px', background: '#fef2f2', border: '1px solid #fecaca', padding: '8px 12px', borderRadius: '6px', lineHeight: 1.4 }}>
                        <strong>Phản hồi:</strong> {doc.rejectReason}
                      </div>
                    )}
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                  <a
                    href={doc.fileType === 'pdf' || doc.fileName?.toLowerCase().endsWith('.pdf')
                      ? `https://docs.google.com/gview?url=${encodeURIComponent(doc.fileUrl)}`
                      : doc.fileUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{
                      background: '#f8fafc',
                      color: '#334155',
                      border: '1px solid #cbd5e1',
                      padding: '8px 14px',
                      borderRadius: '6px',
                      textDecoration: 'none',
                      fontSize: '0.85rem',
                      fontWeight: 600,
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                      transition: 'all 0.2s'
                    }}
                  >
                    Xem chi tiết
                  </a>

                  <a
                    href={doc.fileUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    download={doc.fileName}
                    style={{
                      background: '#ffffff',
                      color: '#475569',
                      border: '1px solid #cbd5e1',
                      padding: '8px 14px',
                      borderRadius: '6px',
                      textDecoration: 'none',
                      fontSize: '0.85rem',
                      fontWeight: 500,
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                      transition: 'all 0.2s'
                    }}
                  >
                    Tải về
                  </a>

                  {docStatus === 'pending' && (
                    <button
                      onClick={() => handleDelete(doc.id)}
                      disabled={deletingId === doc.id}
                      style={{
                        background: '#ffffff',
                        color: '#ef4444',
                        border: '1px solid #fecaca',
                        padding: '8px 14px',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        fontSize: '0.85rem',
                        fontWeight: 500,
                        transition: 'all 0.2s'
                      }}
                    >
                      {deletingId === doc.id ? 'Đang xử lý...' : 'Xóa'}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default CompanyVerificationPage;
