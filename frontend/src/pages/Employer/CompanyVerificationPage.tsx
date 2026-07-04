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
          text: 'Đã xác thực (Verified)',
          color: '#0f5132',
          bg: '#d1e7dd',
          border: '#badbcc',
          desc: 'Tuyệt vời! Công ty của bạn đã được kiểm duyệt pháp lý thành công. Huy hiệu Verified được hiển thị công khai.'
        };
      case 'PENDING':
      case 'PENDING_REVIEW':
        return {
          text: 'Đang chờ duyệt (Pending Review)',
          color: '#664d03',
          bg: '#fff3cd',
          border: '#ffecb5',
          desc: 'Tài liệu của bạn đang được ban quản trị kiểm tra. Quá trình duyệt thường diễn ra trong 24h làm việc.'
        };
      case 'REJECTED':
        return {
          text: 'Bị từ chối (Rejected)',
          color: '#842029',
          bg: '#f8d7da',
          border: '#f5c2c7',
          desc: 'Tài liệu không đạt yêu cầu hoặc không rõ ràng. Vui lòng xem lý do từ chối và tải lên lại tài liệu hợp lệ.'
        };
      default:
        return {
          text: 'Chưa xác thực (Unverified)',
          color: '#41464b',
          bg: '#e2e3e5',
          border: '#d3d6d8',
          desc: 'Vui lòng tải lên Giấy phép đăng ký kinh doanh hoặc Mã số thuế doanh nghiệp (File PDF hoặc Ảnh) để xác thực tài khoản.'
        };
    }
  }

  const badgeInfo = getStatusBadge(company?.verificationStatus);

  if (loading) {
    return (
      <div className="card" style={{ padding: '40px', textAlign: 'center' }}>
        <p>Đang tải thông tin xác thực pháp lý...</p>
      </div>
    );
  }

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <div>
          <h1 style={{ margin: '0 0 8px 0', fontSize: '24px', color: '#111' }}>Xác thực pháp lý doanh nghiệp</h1>
          <p style={{ margin: 0, color: '#666', fontSize: '14px' }}>
            Tải lên Giấy đăng ký kinh doanh, Giấy phép hoạt động hoặc tài liệu thuế định danh doanh nghiệp
          </p>
        </div>
        <button
          onClick={loadData}
          style={{
            background: '#f8f9fa',
            border: '1px solid #ced4da',
            padding: '8px 16px',
            borderRadius: '6px',
            cursor: 'pointer',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '6px'
          }}
        >
          🔄 Làm mới
        </button>
      </div>

      {error && (
        <div style={{ background: '#f8d7da', color: '#842029', padding: '12px 16px', borderRadius: '6px', marginBottom: '16px', border: '1px solid #f5c2c7' }}>
          ⚠️ {error}
        </div>
      )}

      {success && (
        <div style={{ background: '#d1e7dd', color: '#0f5132', padding: '12px 16px', borderRadius: '6px', marginBottom: '16px', border: '1px solid #badbcc' }}>
          ✅ {success}
        </div>
      )}

      {/* Status Banner */}
      <div style={{
        background: badgeInfo.bg,
        color: badgeInfo.color,
        border: `1px solid ${badgeInfo.border}`,
        padding: '20px',
        borderRadius: '10px',
        marginBottom: '24px',
        display: 'flex',
        alignItems: 'flex-start',
        gap: '16px'
      }}>
        <div style={{ fontSize: '28px' }}>
          {company?.verificationStatus?.toUpperCase() === 'VERIFIED' ? '🛡️' :
           company?.verificationStatus?.toUpperCase() === 'PENDING' ? '⏳' :
           company?.verificationStatus?.toUpperCase() === 'REJECTED' ? '❌' : '📋'}
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
            <span style={{ fontWeight: 'bold', fontSize: '16px' }}>Trạng thái:</span>
            <span style={{
              background: 'rgba(0,0,0,0.08)',
              padding: '4px 10px',
              borderRadius: '20px',
              fontWeight: '600',
              fontSize: '14px'
            }}>
              {badgeInfo.text}
            </span>
          </div>
          <p style={{ margin: 0, fontSize: '14px', lineHeight: '1.5' }}>{badgeInfo.desc}</p>
        </div>
      </div>

      {/* Upload Box */}
      <div style={{
        border: '2px dashed #a8b3be',
        borderRadius: '10px',
        padding: '28px 20px',
        textAlign: 'center',
        background: '#fafbfc',
        marginBottom: '28px'
      }}>
        <div style={{ fontSize: '40px', marginBottom: '12px' }}>📁</div>
        <h3 style={{ margin: '0 0 8px 0', fontSize: '16px', color: '#333' }}>
          Tải lên tài liệu xác thực (PDF, JPG, PNG)
        </h3>
        <p style={{ margin: '0 0 16px 0', color: '#6c757d', fontSize: '13px' }}>
          Hỗ trợ file tối đa 10MB. Tài liệu sẽ được lưu trữ an toàn trên Cloudinary.
        </p>

        <form onSubmit={handleUpload} style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center', gap: '12px' }}>
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
              background: '#fff',
              border: '1px solid #ced4da',
              padding: '10px 20px',
              borderRadius: '6px',
              cursor: 'pointer',
              fontWeight: 500,
              color: '#333',
              boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
              display: 'inline-block'
            }}
          >
            {selectedFile ? `📄 Đã chọn: ${selectedFile.name} (${(selectedFile.size / 1024 / 1024).toFixed(2)} MB)` : '🔍 Chọn file từ máy tính'}
          </label>

          {selectedFile && (
            <button
              type="submit"
              disabled={uploading}
              style={{
                background: '#245d43',
                color: '#fff',
                border: 'none',
                padding: '10px 24px',
                borderRadius: '6px',
                cursor: uploading ? 'not-allowed' : 'pointer',
                fontWeight: 'bold',
                fontSize: '14px',
                boxShadow: '0 2px 4px rgba(36,93,67,0.2)'
              }}
            >
              {uploading ? '☁️ Đang tải lên Cloudinary...' : '⬆️ Bắt đầu tải lên & Gửi xác thực'}
            </button>
          )}
        </form>
      </div>

      {/* Document List */}
      <h3 style={{ fontSize: '18px', margin: '0 0 16px 0', color: '#111', borderBottom: '1px solid #eee', paddingBottom: '10px' }}>
        📋 Danh sách tài liệu đã gửi ({documents.length})
      </h3>

      {documents.length === 0 ? (
        <p style={{ textAlign: 'center', padding: '30px 0', color: '#6c757d', margin: 0 }}>
          Chưa có tài liệu nào được tải lên. Vui lòng tải lên tài liệu pháp lý ở trên để bắt đầu xác thực.
        </p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {documents.map((doc) => {
            const docStatus = doc.status?.toLowerCase() || 'pending';
            const statusStyle = docStatus === 'approved' || docStatus === 'verified'
              ? { bg: '#d1e7dd', color: '#0f5132', label: 'Đã duyệt' }
              : docStatus === 'rejected'
              ? { bg: '#f8d7da', color: '#842029', label: 'Bị từ chối' }
              : { bg: '#fff3cd', color: '#664d03', label: 'Chờ duyệt' };

            return (
              <div
                key={doc.id}
                style={{
                  border: '1px solid #e9ecef',
                  borderRadius: '8px',
                  padding: '16px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: '#fff',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.02)'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                  <div style={{
                    width: '44px',
                    height: '44px',
                    borderRadius: '8px',
                    background: doc.fileType === 'pdf' ? '#fde8e8' : '#e1f5fe',
                    color: doc.fileType === 'pdf' ? '#e53e3e' : '#0288d1',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '20px',
                    fontWeight: 'bold'
                  }}>
                    {doc.fileType === 'pdf' ? 'PDF' : 'IMG'}
                  </div>
                  <div>
                    <div style={{ fontWeight: 600, color: '#333', fontSize: '15px', marginBottom: '4px' }}>
                      {doc.fileName}
                    </div>
                    <div style={{ fontSize: '12px', color: '#6c757d', display: 'flex', gap: '12px', alignItems: 'center' }}>
                      <span>🕒 {new Date(doc.uploadedAt).toLocaleString('vi-VN')}</span>
                      <span style={{
                        background: statusStyle.bg,
                        color: statusStyle.color,
                        padding: '2px 8px',
                        borderRadius: '12px',
                        fontWeight: 600,
                        fontSize: '11px'
                      }}>
                        {statusStyle.label}
                      </span>
                    </div>
                    {doc.rejectReason && (
                      <div style={{ color: '#842029', fontSize: '13px', marginTop: '6px', background: '#f8d7da', padding: '6px 10px', borderRadius: '4px' }}>
                        ⚠️ Lý do từ chối: {doc.rejectReason}
                      </div>
                    )}
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <a
                    href={doc.fileType === 'pdf' || doc.fileName?.toLowerCase().endsWith('.pdf')
                      ? `https://docs.google.com/gview?url=${encodeURIComponent(doc.fileUrl)}`
                      : doc.fileUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{
                      background: '#e3f2fd',
                      color: '#0d47a1',
                      border: '1px solid #bbdefb',
                      padding: '8px 14px',
                      borderRadius: '6px',
                      textDecoration: 'none',
                      fontSize: '13px',
                      fontWeight: 600,
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px'
                    }}
                  >
                    👁️ Xem trực tiếp
                  </a>

                  <a
                    href={doc.fileUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    download={doc.fileName}
                    style={{
                      background: '#f8f9fa',
                      color: '#495057',
                      border: '1px solid #dee2e6',
                      padding: '8px 12px',
                      borderRadius: '6px',
                      textDecoration: 'none',
                      fontSize: '13px',
                      fontWeight: 500,
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px'
                    }}
                  >
                    ⬇️ Tải về
                  </a>

                  {docStatus === 'pending' && (
                    <button
                      onClick={() => handleDelete(doc.id)}
                      disabled={deletingId === doc.id}
                      style={{
                        background: '#fff',
                        color: '#dc3545',
                        border: '1px solid #dc3545',
                        padding: '8px 14px',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        fontSize: '13px',
                        fontWeight: 500
                      }}
                    >
                      {deletingId === doc.id ? 'Đang xóa...' : '🗑️ Xóa'}
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
