import { useCallback, useEffect, useMemo, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminAuditLog } from '../../types/admin';

const PAGE_SIZE = 8;

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
    JOB_REPORT_NOTIFY_COMPANY: 'Nhắc công ty sửa tin',
    JOB_REPORT_DISMISS: 'Bỏ qua báo cáo tin',
    JOB_REPORT_RESOLVE: 'Gỡ tin do vi phạm',
    JOB_REPORT_AUTO_REMOVE: 'Tự động gỡ tin (hết hạn sửa)',
    PLAN_CREATE: 'Tạo gói',
    PLAN_UPDATE: 'Cập nhật gói',
    PLAN_DELETE: 'Xóa gói',
    SUBSCRIPTION_CANCEL: 'Hủy đăng ký',
    SUBSCRIPTION_ACTIVATE: 'Kích hoạt đăng ký',
    PAYMENT_CONFIRM: 'Xác nhận thanh toán',
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
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setLogs(await adminService.listAuditLogs(targetType, 300));
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

  const filteredLogs = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return logs;
    return logs.filter((log) => {
      const haystack = [
        actionLabel(log.action),
        log.action,
        log.targetType,
        log.actorEmail,
        log.targetId,
        log.oldValueJson,
        log.newValueJson,
        log.ipAddress,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return haystack.includes(q);
    });
  }, [logs, search]);

  useEffect(() => {
    const totalPages = Math.max(1, Math.ceil(filteredLogs.length / PAGE_SIZE));
    if (page > totalPages) {
      setPage(totalPages);
    }
  }, [filteredLogs.length, page]);

  const totalPages = Math.max(1, Math.ceil(filteredLogs.length / PAGE_SIZE));
  const paginatedLogs = filteredLogs.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <section className="admin-page">
      <header className="admin-page-intro">
        <div className="admin-page-intro-copy">
          <p className="admin-page-intro-eyebrow">Nhật ký hệ thống</p>
          <h1>Báo cáo hoạt động</h1>
          <p>Theo dõi thao tác admin trên công ty, việc làm, gói dịch vụ và cấu hình.</p>
        </div>
        <div className="admin-page-intro-aside">
          <div className="admin-page-intro-stat">
            <span>Đối tượng</span>
            <strong>
              {{
                all: 'Tất cả',
                company: 'Công ty',
                job: 'Việc làm',
                plan: 'Gói',
                subscription: 'Đăng ký',
                category: 'Danh mục',
                system_settings: 'Cài đặt',
              }[targetType] || targetType}
            </strong>
          </div>
          <div className="admin-page-intro-stat">
            <span>Số bản ghi</span>
            <strong>{filteredLogs.length}</strong>
          </div>
        </div>
      </header>

      <div className="admin-toolbar">
        <div className="admin-toolbar-group" role="tablist" aria-label="Lọc loại đối tượng">
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
              role="tab"
              aria-selected={targetType === item.value}
              className={targetType === item.value ? 'active' : 'outline'}
              onClick={() => {
                setTargetType(item.value);
                setPage(1);
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
        <button type="button" className="outline admin-toolbar-refresh" onClick={load} disabled={loading}>
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </div>

      {error && <p className="error admin-inline-message">{error}</p>}

      <section className="admin-company-list-panel">
        <div className="admin-company-list-header">
          <h2>Hoạt động gần đây</h2>
          <span>
            {search.trim()
              ? `${filteredLogs.length}/${logs.length} bản ghi`
              : `${logs.length} bản ghi`}
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
            placeholder="Tìm theo hành động, email admin, loại đối tượng, ID..."
            style={{ flex: 1, minWidth: 240, maxWidth: 520 }}
            aria-label="Tìm kiếm báo cáo hoạt động"
          />
        </div>

        {loading ? (
          <p className="loading">Đang tải nhật ký...</p>
        ) : (
          <>
            <div className="admin-review-list">
              {paginatedLogs.map((log) => (
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
              {filteredLogs.length === 0 && (
                <div className="admin-placeholder-card">
                  <p>
                    {search.trim()
                      ? 'Không tìm thấy bản ghi phù hợp.'
                      : 'Chưa có nhật ký. Các thao tác duyệt/ẩn tin/cập nhật cấu hình sẽ xuất hiện tại đây.'}
                  </p>
                </div>
              )}
            </div>

            {filteredLogs.length > PAGE_SIZE && (
              <div className="admin-pagination">
                <button
                  type="button"
                  className="outline"
                  disabled={page === 1}
                  onClick={() => setPage((value) => Math.max(1, value - 1))}
                >
                  Trước
                </button>
                <span>
                  Trang {page} / {totalPages}
                </span>
                <button
                  type="button"
                  className="outline"
                  disabled={page === totalPages}
                  onClick={() => setPage((value) => Math.min(totalPages, value + 1))}
                >
                  Sau
                </button>
              </div>
            )}
          </>
        )}
      </section>
    </section>
  );
}
