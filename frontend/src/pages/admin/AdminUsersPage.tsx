import { useCallback, useEffect, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminUserRoleFilter, AdminUserStatusFilter, AdminUserSummary } from '../../types/admin';

const PAGE_SIZE = 8;

const roleFilters: { value: AdminUserRoleFilter; label: string }[] = [
  { value: 'all', label: 'Tất cả' },
  { value: 'candidate', label: 'Ứng viên' },
  { value: 'employer', label: 'Nhà tuyển dụng' },
  { value: 'admin', label: 'Quản trị' },
];

const statusFilters: { value: AdminUserStatusFilter; label: string }[] = [
  { value: 'all', label: 'Tất cả trạng thái' },
  { value: 'active', label: 'Đang hoạt động' },
  { value: 'suspended', label: 'Đã khóa' },
  { value: 'inactive', label: 'Chưa xác minh' },
];

const roleCapabilities: Record<string, string[]> = {
  CANDIDATE: ['Tạo và cập nhật hồ sơ ứng viên', 'Tải lên CV', 'Lưu việc làm', 'Ứng tuyển và theo dõi hồ sơ'],
  EMPLOYER: ['Cập nhật hồ sơ công ty', 'Gửi xác thực công ty', 'Đăng và gửi duyệt tin tuyển dụng', 'Quản lý ứng viên ứng tuyển'],
  ADMIN: ['Duyệt hồ sơ công ty', 'Duyệt tin tuyển dụng', 'Quản lý người dùng', 'Theo dõi thống kê hệ thống'],
};

function roleLabel(role?: string) {
  switch (role) {
    case 'ADMIN':
      return { text: 'Quản trị', className: 'status-verified' };
    case 'EMPLOYER':
      return { text: 'Nhà tuyển dụng', className: 'status-pending' };
    default:
      return { text: 'Ứng viên', className: 'status-unverified' };
  }
}

function statusLabel(status?: string) {
  switch (status) {
    case 'ACTIVE':
      return { text: 'Hoạt động', className: 'status-verified' };
    case 'SUSPENDED':
      return { text: 'Đã khóa', className: 'status-rejected' };
    default:
      return { text: 'Chưa xác minh', className: 'status-pending' };
  }
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

export default function AdminUsersPage() {
  const [role, setRole] = useState<AdminUserRoleFilter>('all');
  const [status, setStatus] = useState<AdminUserStatusFilter>('all');
  const [page, setPage] = useState(1);
  const [users, setUsers] = useState<AdminUserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [submittingId, setSubmittingId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await adminService.listUsers(role, status);
      setUsers(data);
    } catch (err) {
      setError(readError(err));
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, [role, status]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  useEffect(() => {
    const totalPages = Math.max(1, Math.ceil(users.length / PAGE_SIZE));
    if (page > totalPages) {
      setPage(totalPages);
    }
  }, [users.length, page]);

  async function handleToggleUser(user: AdminUserSummary) {
    const isSuspended = user.status === 'SUSPENDED';
    const action = isSuspended ? 'mở khóa' : 'khóa';
    if (!window.confirm(`Bạn có chắc muốn ${action} tài khoản ${user.email}?`)) return;

    setSubmittingId(user.id);
    setError('');
    setSuccess('');
    try {
      const updated = isSuspended
        ? await adminService.activateUser(user.id)
        : await adminService.suspendUser(user.id);
      setUsers((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setSuccess(`Đã ${action} tài khoản ${user.email}.`);
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmittingId(null);
    }
  }

  const totalPages = Math.max(1, Math.ceil(users.length / PAGE_SIZE));
  const paginatedUsers = users.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <h1>Quản lý người dùng</h1>
        <p className="muted">Quản lý tài khoản ứng viên, nhà tuyển dụng và quản trị.</p>
      </header>

      <div className="admin-user-tools">
        <div>
          <h2>Phân quyền tài khoản</h2>
          <p>Mỗi loại tài khoản có chức năng riêng. Admin có thể khóa tài khoản vi phạm để ngăn đăng nhập và thao tác.</p>
        </div>
        <button type="button" className="outline" onClick={loadUsers} disabled={loading}>
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </div>

      <div className="admin-role-grid">
        {Object.entries(roleCapabilities).map(([roleKey, items]) => {
          const roleInfo = roleLabel(roleKey);
          return (
            <article key={roleKey} className="admin-role-card">
              <span className={`admin-status-badge ${roleInfo.className}`}>{roleInfo.text}</span>
              <ul>
                {items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </article>
          );
        })}
      </div>

      <div className="admin-filter-tabs">
        {roleFilters.map((item) => (
          <button
            key={item.value}
            type="button"
            className={role === item.value ? 'active' : 'outline'}
            onClick={() => {
              setRole(item.value);
              setPage(1);
            }}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="admin-filter-tabs">
        {statusFilters.map((item) => (
          <button
            key={item.value}
            type="button"
            className={status === item.value ? 'active' : 'outline'}
            onClick={() => {
              setStatus(item.value);
              setPage(1);
            }}
          >
            {item.label}
          </button>
        ))}
      </div>

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      <section className="admin-company-list-panel admin-review-list-panel">
        <div className="admin-company-list-header">
          <h2>Danh sách tài khoản</h2>
          <span>{users.length} tài khoản</span>
        </div>

        {loading ? (
          <p className="loading">Đang tải danh sách...</p>
        ) : users.length === 0 ? (
          <div className="admin-placeholder-card">
            <p>Không có tài khoản nào phù hợp bộ lọc.</p>
          </div>
        ) : (
          <div className="admin-review-list">
            {paginatedUsers.map((user) => {
              const roleInfo = roleLabel(user.role);
              const statusInfo = statusLabel(user.status);
              const isSuspended = user.status === 'SUSPENDED';
              return (
                <article key={user.id} className="admin-review-row admin-user-row">
                  <div className="admin-review-main">
                    <div className="admin-review-title-line">
                      <strong>{user.fullName || user.email}</strong>
                      <span className={`admin-status-badge ${roleInfo.className}`}>{roleInfo.text}</span>
                      <span className={`admin-status-badge ${statusInfo.className}`}>{statusInfo.text}</span>
                    </div>
                    <p>{user.email}</p>
                  </div>
                  <div className="admin-review-meta">
                    <span>SĐT: {user.phone || '—'}</span>
                    <span>Email: {user.emailVerified ? 'Đã xác minh' : 'Chưa xác minh'}</span>
                    <span>Đăng nhập cuối: {formatDate(user.lastLoginAt)}</span>
                  </div>
                  <button
                    type="button"
                    className={isSuspended ? 'outline' : 'danger'}
                    onClick={() => handleToggleUser(user)}
                    disabled={submittingId === user.id}
                  >
                    {submittingId === user.id ? 'Đang xử lý...' : isSuspended ? 'Mở khóa' : 'Khóa tài khoản'}
                  </button>
                </article>
              );
            })}
          </div>
        )}

        {users.length > PAGE_SIZE && (
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
    </section>
  );
}
