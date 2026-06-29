import { useEffect, useState } from 'react';
import { authService } from '../../services/authService';
import type { User } from '../../types/auth';

export default function AdminProfilePage() {
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    authService.getCurrentUser().then(setUser).catch(() => setUser(null));
  }, []);

  if (!user) {
    return <p className="loading">Loading...</p>;
  }

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <h1>Profile</h1>
        <p className="muted">Your administrator account details.</p>
      </header>

      <div className="admin-profile-card">
        <div><span>Email</span><strong>{user.email}</strong></div>
        <div><span>Role</span><strong>{user.role}</strong></div>
        <div><span>Status</span><strong>{user.status}</strong></div>
        <div><span>Email verified</span><strong>{user.emailVerified ? 'Yes' : 'No'}</strong></div>
      </div>
    </section>
  );
}
