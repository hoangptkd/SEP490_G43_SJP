import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { authService } from '../../services/authService';
import type { User } from '../../types/auth';

const roleLabels: Record<string, string> = {
  ADMIN: 'Quản trị viên',
  CANDIDATE: 'Ứng viên',
  EMPLOYER: 'Nhà tuyển dụng',
};

const statusLabels: Record<string, string> = {
  ACTIVE: 'Hoạt động',
  SUSPENDED: 'Tạm khóa',
  PENDING_VERIFICATION: 'Chờ xác minh',
};

function statusClass(status: string) {
  if (status === 'ACTIVE') return 'status-verified';
  if (status === 'PENDING_VERIFICATION') return 'status-pending';
  return 'status-rejected';
}

function initialsFromEmail(email: string) {
  const local = email.split('@')[0] || 'AD';
  const parts = local.replace(/[^a-zA-Z0-9]/g, ' ').trim().split(/\s+/);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }
  return local.slice(0, 2).toUpperCase();
}

export default function AdminProfilePage() {
  const [user, setUser] = useState<User | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    authService.getCurrentUser()
      .then(setUser)
      .catch(() => {
        setUser(null);
        setError('Không tải được thông tin hồ sơ.');
      });
  }, []);

  if (error) {
    return <p className="error admin-inline-message">{error}</p>;
  }

  if (!user) {
    return <p className="loading">Đang tải hồ sơ...</p>;
  }

  return (
    <section className="admin-page admin-profile-page">
      <header className="admin-page-header">
        <div>
          <h1>Hồ sơ quản trị</h1>
          <p className="muted">Thông tin tài khoản đang đăng nhập trên hệ thống.</p>
        </div>
        <Link to="/admin" className="button-link outline">← Bảng điều khiển</Link>
      </header>

      <article className="admin-profile-hero">
        <div className="admin-profile-hero-bg" aria-hidden="true" />
        <div className="admin-profile-hero-body">
          <div className="admin-profile-avatar" aria-hidden="true">
            {initialsFromEmail(user.email)}
          </div>
          <div className="admin-profile-hero-text">
            <p className="admin-profile-eyebrow">Tài khoản hệ thống</p>
            <h2>{user.email}</h2>
            <div className="admin-profile-badges">
              <span className="admin-status-badge status-pending">{roleLabels[user.role] || user.role}</span>
              <span className={`admin-status-badge ${statusClass(user.status)}`}>
                {statusLabels[user.status] || user.status}
              </span>
              <span className={`admin-status-badge ${user.emailVerified ? 'status-verified' : 'status-unverified'}`}>
                {user.emailVerified ? 'Email đã xác minh' : 'Email chưa xác minh'}
              </span>
            </div>
          </div>
        </div>
      </article>

      <div className="admin-profile-grid">
        <section className="admin-profile-panel">
          <h3>Thông tin tài khoản</h3>
          <div className="admin-profile-fields">
            <div className="admin-profile-field">
              <span>Email</span>
              <strong>{user.email}</strong>
            </div>
            <div className="admin-profile-field">
              <span>Vai trò</span>
              <strong>{roleLabels[user.role] || user.role}</strong>
            </div>
            <div className="admin-profile-field">
              <span>Trạng thái</span>
              <strong>{statusLabels[user.status] || user.status}</strong>
            </div>
            <div className="admin-profile-field">
              <span>Xác minh email</span>
              <strong>{user.emailVerified ? 'Đã xác minh' : 'Chưa xác minh'}</strong>
            </div>
            <div className="admin-profile-field">
              <span>Mã người dùng</span>
              <strong className="admin-profile-mono">{user.id}</strong>
            </div>
          </div>
        </section>

        <section className="admin-profile-panel admin-profile-aside">
          <h3>Quyền truy cập</h3>
          <ul className="admin-profile-perms">
            <li>Duyệt hồ sơ công ty & tin tuyển dụng</li>
            <li>Quản lý người dùng và danh mục</li>
            <li>Cấu hình hệ thống & gói thanh toán</li>
            <li>Xem thống kê và nhật ký hoạt động</li>
          </ul>
          <div className="admin-profile-aside-actions">
            <Link to="/admin/settings" className="button-link">Cài đặt hệ thống</Link>
            <Link to="/admin/users" className="button-link outline">Quản lý người dùng</Link>
          </div>
        </section>
      </div>
    </section>
  );
}
