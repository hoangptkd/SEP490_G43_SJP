export default function AdminDashboardPage() {
  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <h1>Dashboard</h1>
        <p className="muted">Overview of platform activity and quick actions.</p>
      </header>

      <div className="admin-metric-grid">
        <article className="admin-metric-card">
          <span>Total Users</span>
          <strong>--</strong>
        </article>
        <article className="admin-metric-card">
          <span>Active Jobs</span>
          <strong>--</strong>
        </article>
        <article className="admin-metric-card">
          <span>Pending Moderation</span>
          <strong>--</strong>
        </article>
        <article className="admin-metric-card">
          <span>Applications Today</span>
          <strong>--</strong>
        </article>
      </div>

      <div className="admin-placeholder-card">
        <h2>Welcome to Admin Home</h2>
        <p>Use the sidebar to manage users, moderate jobs, view statistics, and configure system settings.</p>
      </div>
    </section>
  );
}
