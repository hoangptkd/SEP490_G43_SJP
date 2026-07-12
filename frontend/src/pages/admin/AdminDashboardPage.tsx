export default function AdminDashboardPage() {
  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <h1>Bảng điều khiển</h1>
        <p className="muted">Tổng quan hoạt động nền tảng và các thao tác nhanh.</p>
      </header>

      <div className="admin-metric-grid">
        <article className="admin-metric-card">
          <span>Tổng người dùng</span>
          <strong>--</strong>
        </article>
        <article className="admin-metric-card">
          <span>Việc làm đang hoạt động</span>
          <strong>--</strong>
        </article>
        <article className="admin-metric-card">
          <span>Chờ kiểm duyệt</span>
          <strong>--</strong>
        </article>
        <article className="admin-metric-card">
          <span>Ứng tuyển hôm nay</span>
          <strong>--</strong>
        </article>
      </div>

      <div className="admin-placeholder-card">
        <h2>Chào mừng đến trang quản trị</h2>
        <p>Sử dụng menu bên trái để quản lý người dùng, kiểm duyệt việc làm, xem thống kê và cấu hình hệ thống.</p>
      </div>
    </section>
  );
}
