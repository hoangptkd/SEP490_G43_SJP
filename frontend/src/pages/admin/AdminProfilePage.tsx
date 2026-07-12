import { useEffect, useState } from 'react';
import { authService } from '../../services/authService';
import type { User } from '../../types/auth';

const roleLabels: Record<string, string> = {
  ADMIN: 'Quản trị',
  CANDIDATE: 'Ứng viên',
  EMPLOYER: 'Nhà tuyển dụng',
};

const statusLabels: Record<string, string> = {
  ACTIVE: 'Hoạt động',
  SUSPENDED: 'Tạm khóa',
  PENDING_VERIFICATION: 'Chờ xác minh',
};

export default function AdminProfilePage() {
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    authService.getCurrentUser().then(setUser).catch(() => setUser(null));
  }, []);

  if (!user) {
    return <p className="loading">Đang tải...</p>;
  }

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <h1>Hồ sơ</h1>
        <p className="muted">Thông tin tài khoản quản trị của bạn.</p>
      </header>

      <div className="admin-profile-card">
        <div><span>Email</span><strong>{user.email}</strong></div>
        <div><span>Vai trò</span><strong>{roleLabels[user.role] || user.role}</strong></div>
        <div><span>Trạng thái</span><strong>{statusLabels[user.status] || user.status}</strong></div>
        <div><span>Email đã xác minh</span><strong>{user.emailVerified ? 'Có' : 'Không'}</strong></div>
      </div>
    </section>
  );
}
