import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminDashboardStats, AdminTrendPoint } from '../../types/admin';

function formatNumber(value?: number) {
  return new Intl.NumberFormat('vi-VN').format(value ?? 0);
}

function formatMoney(value?: number) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value ?? 0);
}

function formatTime(value?: string) {
  if (!value) return '—';
  return new Date(value).toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function formatDayLabel(value?: string) {
  if (!value) return '—';
  return new Date(value).toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit' });
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Không thể tải số liệu dashboard';
  }
  return 'Không thể tải số liệu dashboard';
}

function completionRate(completed?: number, total?: number) {
  if (!total) return 0;
  return Math.round(((completed ?? 0) / total) * 100);
}

const emptyStats: AdminDashboardStats = {
  totalUsers: 0,
  activeJobs: 0,
  pendingModeration: 0,
  applicationsToday: 0,
  pendingCompanies: 0,
  pendingJobs: 0,
  verifiedCompanies: 0,
  totalCompanies: 0,
  totalApplications: 0,
  totalEmployers: 0,
  totalCandidates: 0,
  revenueToday: 0,
  revenueMonth: 0,
  paidCountMonth: 0,
  activeSubscriptions: 0,
  interviewsToday: 0,
  interviewsWeek: 0,
  interviewsCompletedWeek: 0,
  closedJobs: 0,
  applicationsLast7Days: [],
  revenueLast7Days: [],
  updatedAt: '',
};

function ChartBars({
  title,
  subtitle,
  points,
  tone,
  formatValue,
}: {
  title: string;
  subtitle: string;
  points: AdminTrendPoint[];
  tone: 'blue' | 'green';
  formatValue: (value: number) => string;
}) {
  const max = Math.max(...points.map((p) => p.value), 1);
  return (
    <section className="dash-chart-card">
      <div className="dash-chart-head">
        <div>
          <h3>{title}</h3>
          <p>{subtitle}</p>
        </div>
        <span className={`dash-chart-badge ${tone === 'green' ? 'green' : ''}`}>7 ngày</span>
      </div>
      <div className="dash-chart-bars">
        {points.map((point) => {
          const height = `${Math.max(8, Math.round((point.value / max) * 100))}%`;
          return (
            <div key={point.date} className="dash-chart-col" title={formatValue(point.value)}>
              <span className="dash-chart-value">{point.value > 0 ? formatValue(point.value) : ''}</span>
              <div className={`dash-chart-bar ${tone}`} style={{ height }} />
              <span className="dash-chart-label">{formatDayLabel(point.date)}</span>
            </div>
          );
        })}
        {points.length === 0 && <p className="muted">Chưa có dữ liệu 7 ngày gần đây.</p>}
      </div>
    </section>
  );
}

export default function AdminDashboardPage() {
  const [stats, setStats] = useState<AdminDashboardStats>(emptyStats);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadStats = useCallback(async () => {
    try {
      const data = await adminService.getDashboardStats();
      setStats({ ...emptyStats, ...data });
      setError('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStats();
    const timer = window.setInterval(loadStats, 15000);
    return () => window.clearInterval(timer);
  }, [loadStats]);

  const interviewRate = useMemo(
    () => completionRate(stats.interviewsCompletedWeek, stats.interviewsWeek),
    [stats.interviewsCompletedWeek, stats.interviewsWeek],
  );

  const queueItems = [
    {
      label: 'Hồ sơ công ty chờ duyệt',
      value: stats.pendingCompanies,
      to: '/admin/companies?status=pending&page=1',
      tone: 'amber' as const,
    },
    {
      label: 'Tin tuyển dụng chờ duyệt',
      value: stats.pendingJobs,
      to: '/admin/jobs?status=pending_review&page=1',
      tone: 'amber' as const,
    },
    {
      label: 'Tin bị báo cáo',
      value: stats.closedJobs ?? 0,
      to: '/admin/jobs?status=reports&page=1',
      tone: 'danger' as const,
    },
    {
      label: 'Đăng ký gói đang active',
      value: stats.activeSubscriptions ?? 0,
      to: '/admin/billing?tab=transactions',
      tone: 'green' as const,
    },
  ];

  return (
    <section className="admin-page dash-page">
      <header className="admin-dashboard-hero dash-hero">
        <div>
          <p className="admin-dashboard-eyebrow">Tổng quan hệ thống</p>
          <h1>Bảng điều khiển</h1>
          <p>Theo dõi kiểm duyệt, doanh thu, phỏng vấn AI và xu hướng 7 ngày gần đây.</p>
        </div>
        <div className="admin-dashboard-refresh">
          <span>
            <i className="dash-live-dot" aria-hidden="true" />
            Cập nhật: {formatTime(stats.updatedAt)}
          </span>
          <button type="button" className="outline" onClick={loadStats} disabled={loading}>
            {loading ? 'Đang tải...' : 'Làm mới'}
          </button>
        </div>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}

      <div className="dash-section-label">
        <h2>Chỉ số nền tảng</h2>
        <span>Nhấn thẻ để mở chi tiết</span>
      </div>

      <div className="admin-dashboard-grid dash-kpi-grid">
        <Link className="admin-dashboard-card primary" to="/admin/users?role=all&status=all&page=1">
          <span>Tổng người dùng</span>
          <strong>{formatNumber(stats.totalUsers)}</strong>
          <p>{formatNumber(stats.totalCandidates)} ứng viên · {formatNumber(stats.totalEmployers)} NTD</p>
        </Link>
        <Link className="admin-dashboard-card green" to="/admin/jobs?status=published&page=1">
          <span>Việc làm đang mở</span>
          <strong>{formatNumber(stats.activeJobs)}</strong>
          <p>Tin đã duyệt và đang hiển thị</p>
        </Link>
        <Link
          className="admin-dashboard-card amber"
          to={stats.pendingCompanies > 0 ? '/admin/companies?status=pending&page=1' : '/admin/jobs?status=pending_review&page=1'}
        >
          <span>Chờ kiểm duyệt</span>
          <strong>{formatNumber(stats.pendingModeration)}</strong>
          <p>{formatNumber(stats.pendingCompanies)} công ty · {formatNumber(stats.pendingJobs)} tin</p>
        </Link>
        <Link className="admin-dashboard-card blue" to="/admin/statistics">
          <span>Ứng tuyển hôm nay</span>
          <strong>{formatNumber(stats.applicationsToday)}</strong>
          <p>Tổng: {formatNumber(stats.totalApplications)}</p>
        </Link>
      </div>

      <div className="dash-section-label">
        <h2>Doanh thu & vận hành</h2>
        <span>Theo dõi sức khỏe nền tảng</span>
      </div>

      <div className="admin-dashboard-grid dash-kpi-grid">
        <Link className="admin-dashboard-card dash-card-revenue" to="/admin/billing">
          <span>Doanh thu tháng này</span>
          <strong>{formatMoney(stats.revenueMonth)}</strong>
          <p>
            Hôm nay {formatMoney(stats.revenueToday)} · {formatNumber(stats.paidCountMonth)} giao dịch
          </p>
        </Link>
        <Link className="admin-dashboard-card dash-card-ai" to="/admin/statistics">
          <span>Phỏng vấn AI tuần này</span>
          <strong>{formatNumber(stats.interviewsWeek)}</strong>
          <p>
            Hôm nay {formatNumber(stats.interviewsToday)} · hoàn thành {interviewRate}%
          </p>
        </Link>
        <Link className="admin-dashboard-card dash-card-alert" to="/admin/jobs?status=reports&page=1">
          <span>Tin bị báo cáo</span>
          <strong>{formatNumber(stats.closedJobs)}</strong>
          <p>Tiếp nhận báo cáo từ ứng viên để kiểm tra</p>
        </Link>
        <Link className="admin-dashboard-card dash-card-sub" to="/admin/billing?tab=transactions">
          <span>Đăng ký gói active</span>
          <strong>{formatNumber(stats.activeSubscriptions)}</strong>
          <p>Đang có hiệu lực trên nền tảng</p>
        </Link>
      </div>

      <div className="dash-main-grid">
        <section className="dash-panel dash-queue">
          <div className="dash-panel-head">
            <div>
              <h2>Việc cần làm</h2>
              <p>Ưu tiên xử lý các mục đang chờ hoặc cần giám sát.</p>
            </div>
          </div>
          <div className="dash-queue-list">
            {queueItems.map((item) => (
              <Link key={item.label} className={`dash-queue-item ${item.tone}`} to={item.to}>
                <div>
                  <strong>{item.label}</strong>
                  <span>{item.value > 0 ? 'Cần xem' : 'Ổn định'}</span>
                </div>
                <em>{formatNumber(item.value)}</em>
              </Link>
            ))}
          </div>
        </section>

        <section className="dash-panel dash-shortcuts">
          <div className="dash-panel-head">
            <div>
              <h2>Lối tắt vận hành</h2>
              <p>Đi nhanh tới các khu vực quản trị thường dùng.</p>
            </div>
          </div>
          <div className="dash-shortcut-grid">
            <Link to="/admin/companies?status=pending&page=1">Duyệt công ty</Link>
            <Link to="/admin/jobs?status=pending_review&page=1">Duyệt tin tuyển dụng</Link>
            <Link to="/admin/billing">Gói & Thanh toán</Link>
            <Link to="/admin/statistics">Thống kê chi tiết</Link>
            <Link to="/admin/settings?tab=categories">Danh mục</Link>
            <Link to="/admin/settings">Cài đặt hệ thống</Link>
          </div>
        </section>
      </div>

      <div className="dash-chart-grid">
        <ChartBars
          title="Ứng tuyển 7 ngày"
          subtitle="Số lượt ứng tuyển mỗi ngày"
          points={stats.applicationsLast7Days || []}
          tone="blue"
          formatValue={formatNumber}
        />
        <ChartBars
          title="Doanh thu 7 ngày"
          subtitle="Tổng thanh toán thành công theo ngày"
          points={stats.revenueLast7Days || []}
          tone="green"
          formatValue={(v) => formatNumber(v)}
        />
      </div>
    </section>
  );
}
