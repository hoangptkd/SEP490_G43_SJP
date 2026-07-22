import { useCallback, useEffect, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminAuditLog } from '../../types/admin';

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

function actionLabel(action: string) {
  const map: Record<string, string> = {
    COMPANY_APPROVE: 'Duyệt công ty',
    COMPANY_REJECT: 'Từ chối công ty',
    COMPANY_DOCUMENT_APPROVE: 'Duyệt tài liệu pháp lý',
    COMPANY_DOCUMENT_REJECT: 'Từ chối tài liệu pháp lý',
    JOB_APPROVE: 'Duyệt tin tuyển dụng',
    JOB_REJECT: 'Từ chối tin tuyển dụng',
    JOB_CLOSE: 'Tạm dừng tin',
    JOB_REOPEN: 'Mở lại tin',
    JOB_REPORT_DISMISS: 'Bỏ qua báo cáo tin',
    JOB_REPORT_RESOLVE: 'Gỡ tin do vi phạm',
    PLAN_CREATE: 'Tạo gói',
    PLAN_UPDATE: 'Cập nhật gói',
    PLAN_DELETE: 'Xóa gói',
    SUBSCRIPTION_CANCEL: 'Hủy đăng ký',
    SUBSCRIPTION_ACTIVATE: 'Kích hoạt đăng ký',
    CATEGORY_CREATE: 'Tạo danh mục',
    CATEGORY_UPDATE: 'Cập nhật danh mục',
    SETTINGS_UPDATE: 'Cập nhật cài đặt',
  };
  return map[action] || action;
}

function formatDate(value?: string) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN');
}

export default function AdminAuditPage() {
  const [logs, setLogs] = useState<AdminAuditLog[]>([]);
  const [targetType, setTargetType] = useState('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setLogs(await adminService.listAuditLogs(targetType, 120));
      setError('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [targetType]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <h1>Báo cáo hoạt động</h1>
          <p className="muted">View detailed activity reports — theo dõi thao tác admin trên công ty, việc làm, gói dịch vụ và cấu hình.</p>
        </div>
        <button type="button" className="outline" onClick={load} disabled={loading}>
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}

      <div className="admin-filter-tabs">
        {[
          { value: 'all', label: 'Tất cả' },
          { value: 'company', label: 'Công ty' },
          { value: 'job', label: 'Việc làm' },
          { value: 'plan', label: 'Gói' },
          { value: 'subscription', label: 'Đăng ký' },
          { value: 'category', label: 'Danh mục' },
          { value: 'system_settings', label: 'Cài đặt' },
        ].map((item) => (
          <button
            key={item.value}
            type="button"
            className={targetType === item.value ? 'active' : 'outline'}
            onClick={() => setTargetType(item.value)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <section className="admin-company-list-panel">
        <div className="admin-company-list-header">
          <h2>Hoạt động gần đây</h2>
          <span>{logs.length} bản ghi</span>
        </div>
        <div className="admin-review-list">
          {logs.map((log) => (
            <article key={log.id} className="admin-review-row admin-audit-row">
              <div className="admin-review-main">
                <div className="admin-review-title-line">
                  <strong>{actionLabel(log.action)}</strong>
                  <span className="admin-status-badge status-pending">{log.targetType}</span>
                </div>
                <div className="admin-review-meta">
                  <span>{log.actorEmail || 'Hệ thống'}</span>
                  <span>{formatDate(log.createdAt)}</span>
                  {log.targetId && <span>ID: {log.targetId.slice(0, 8)}…</span>}
                </div>
                {(log.oldValueJson || log.newValueJson) && (
                  <p className="muted">
                    {log.oldValueJson || '—'} → {log.newValueJson || '—'}
                  </p>
                )}
              </div>
            </article>
          ))}
          {!loading && logs.length === 0 && (
            <div className="admin-placeholder-card">
              <p>Chưa có nhật ký. Các thao tác duyệt/ẩn tin/cập nhật cấu hình sẽ xuất hiện tại đây.</p>
            </div>
          )}
        </div>
      </section>
    </section>
  );
}
