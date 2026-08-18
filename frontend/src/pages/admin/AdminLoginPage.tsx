import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authService } from '../../services/authService';
import { clearAuthSession, setAuthSession } from '../../utils/authStorage';
import '../../styles/admin.css';

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Đăng nhập thất bại';
  }
  return 'Đăng nhập thất bại';
}

export default function AdminLoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError('');

    try {
      const response = await authService.login({ email, password, portal: 'admin' });

      if (!response.token) {
        setError('Tài khoản chưa được xác minh.');
        return;
      }

      if (response.user.role !== 'ADMIN') {
        clearAuthSession();
        setError('Tài khoản này không có quyền quản trị.');
        return;
      }

      setAuthSession(response.token, response.user);
      navigate('/admin');
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <div className="admin-auth-shell">
      <section className="admin-auth-panel">
        <p className="admin-auth-eyebrow">Smart Recruitment Portal</p>
        <h1>Đăng nhập quản trị</h1>
        <p className="muted">Đăng nhập để truy cập bảng quản trị.</p>

        <form onSubmit={submit} className="form-grid" autoComplete="off">
          <input type="text" name="fake_admin_username" style={{ display: 'none' }} tabIndex={-1} autoComplete="off" />
          <input type="password" name="fake_admin_password" style={{ display: 'none' }} tabIndex={-1} autoComplete="off" />
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@example.com"
              required
              autoComplete="off"
            />
          </label>
          <label>
            Mật khẩu
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="new-password"
            />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit">Đăng nhập</button>
        </form>

        <p className="admin-auth-footer">
          <Link to="/jobs">Quay lại trang công khai</Link>
        </p>
      </section>
    </div>
  );
}
