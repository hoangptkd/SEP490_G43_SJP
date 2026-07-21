import { useEffect, useState } from 'react';
import { NavLink, Navigate, Outlet, useNavigate } from 'react-router-dom';
import { authService } from '../../services/authService';
import { clearAuthSession, getStoredUser, getToken } from '../../utils/authStorage';
import type { User } from '../../types/auth';

const menuItems = [
  { to: '/admin', label: 'Bảng điều khiển', end: true },
  { to: '/admin/companies', label: 'Duyệt hồ sơ công ty' },
  { to: '/admin/jobs', label: 'Quản lý việc làm' },
  { to: '/admin/billing', label: 'Gói & Thanh toán' },
  { to: '/admin/statistics', label: 'Thống kê' },
  { to: '/admin/audit-logs', label: 'Báo cáo hoạt động' },
  { to: '/admin/users', label: 'Quản lý người dùng' },
  { to: '/admin/settings', label: 'Cài đặt hệ thống' },
  { to: '/admin/profile', label: 'Hồ sơ' },
];

export function AdminProtected({ children }: { children: JSX.Element }) {
  const token = getToken();
  const user = getStoredUser();

  if (!token || user?.role !== 'ADMIN') {
    return <Navigate to="/admin/login" replace />;
  }

  return children;
}

export default function AdminLayout() {
  const navigate = useNavigate();
  const [user, setUser] = useState<User | null>(getStoredUser());

  useEffect(() => {
    if (!getToken()) return;
    authService.getCurrentUser()
      .then(setUser)
      .catch(() => undefined);
  }, []);

  function logout() {
    clearAuthSession();
    navigate('/admin/login');
  }

  return (
    <div className="admin-shell">
      <aside className="admin-nav">
        <div className="admin-nav-header">
          <span className="admin-nav-badge">Quản trị</span>
          <strong>Smart Recruitment Portal</strong>
          {user && <p className="admin-nav-user">{user.email}</p>}
        </div>

        <nav className="admin-nav-links">
          {menuItems.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end}>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <button type="button" className="admin-logout" onClick={logout}>
          Đăng xuất
        </button>
      </aside>

      <main className="admin-main">
        <Outlet />
      </main>
    </div>
  );
}
