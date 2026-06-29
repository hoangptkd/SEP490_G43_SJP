import { useEffect, useState } from 'react';
import { NavLink, Navigate, Outlet, useNavigate } from 'react-router-dom';
import { authService } from '../../services/authService';
import { clearAuthSession, getStoredUser, getToken } from '../../utils/authStorage';
import type { User } from '../../types/auth';

const menuItems = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/users', label: 'User Management' },
  { to: '/admin/jobs', label: 'Job Moderation' },
  { to: '/admin/statistics', label: 'Statistics' },
  { to: '/admin/settings', label: 'System Settings' },
  { to: '/admin/profile', label: 'Profile' },
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
          <span className="admin-nav-badge">Admin</span>
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
          Logout
        </button>
      </aside>

      <main className="admin-main">
        <Outlet />
      </main>
    </div>
  );
}
