import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authService } from '../../services/authService';
import { setAuthSession } from '../../utils/authStorage';

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Login failed';
  }
  return 'Login failed';
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
      const response = await authService.login({ email, password });

      if (!response.token) {
        setError('Account is not verified yet.');
        return;
      }

      if (response.user.role !== 'ADMIN') {
        setError('This account does not have admin access.');
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
        <h1>Admin Login</h1>
        <p className="muted">Sign in to access the administration dashboard.</p>

        <form onSubmit={submit} className="form-grid">
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@example.com"
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit">Sign in</button>
        </form>

        <p className="admin-auth-footer">
          <Link to="/jobs">Back to public site</Link>
        </p>
      </section>
    </div>
  );
}
