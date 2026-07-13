import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminDashboardStats } from '../../types/admin';

function formatNumber(value?: number) {
  return new Intl.NumberFormat('vi-VN').format(value ?? 0);
}

function formatDate(value?: string) {
  if (!value) return '—';
  return new Date(value).toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Không thể tải số liệu dashboard';
  }
  return 'Không thể tải số liệu dashboard';
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
  updatedAt: '',
};

export default function AdminDashboardPage() {
  const [stats, setStats] = useState<AdminDashboardStats>(emptyStats);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadStats = useCallback(async () => {
    try {
      const data = await adminService.getDashboardStats();
      setStats(data);
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

  return (
    <section className="admin-page">
      <header className="admin-dashboard-hero">
        <div>
          <p className="admin-dashboard-eyebrow">Tổng quan hệ thống</p>
          <h1>Bảng điều khiển</h1>
          <p>Theo dõi người dùng, hồ sơ công ty, việc làm và ứng tuyển theo dữ liệu mới nhất trong database.</p>
        </div>
        <div className="admin-dashboard-refresh">
          <span>Cập nhật: {formatDate(stats.updatedAt)}</span>
          <button type="button" className="outline" onClick={loadStats} disabled={loading}>
            {loading ? 'Đang tải...' : 'Làm mới'}
          </button>
        </div>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}

      <div className="admin-dashboard-grid">
        <article className="admin-dashboard-card primary">
          <span>Tổng người dùng</span>
          <strong>{formatNumber(stats.totalUsers)}</strong>
          <p>{formatNumber(stats.totalCandidates)} ứng viên · {formatNumber(stats.totalEmployers)} nhà tuyển dụng</p>
        </article>
        <article className="admin-dashboard-card green">
          <span>Việc làm đang hoạt động</span>
          <strong>{formatNumber(stats.activeJobs)}</strong>
          <p>Tin đã duyệt và đang hiển thị công khai</p>
        </article>
        <article className="admin-dashboard-card amber">
          <span>Chờ kiểm duyệt</span>
          <strong>{formatNumber(stats.pendingModeration)}</strong>
          <p>{formatNumber(stats.pendingCompanies)} hồ sơ công ty · {formatNumber(stats.pendingJobs)} tin tuyển dụng</p>
        </article>
        <article className="admin-dashboard-card blue">
          <span>Ứng tuyển hôm nay</span>
          <strong>{formatNumber(stats.applicationsToday)}</strong>
          <p>Tổng ứng tuyển: {formatNumber(stats.totalApplications)}</p>
        </article>
      </div>

      <div className="admin-dashboard-panels">
        <section className="admin-dashboard-panel">
          <div>
            <h2>Kiểm duyệt đang chờ xử lý</h2>
            <p>Ưu tiên xử lý hồ sơ công ty và tin tuyển dụng mới gửi duyệt.</p>
          </div>
          <div className="admin-dashboard-actions">
            <Link className="button-link" to="/admin/companies?status=pending&page=1">
              Duyệt hồ sơ công ty ({formatNumber(stats.pendingCompanies)})
            </Link>
            <Link className="button-link outline" to="/admin/jobs?status=pending_review&page=1">
              Duyệt tin tuyển dụng ({formatNumber(stats.pendingJobs)})
            </Link>
          </div>
        </section>

        <section className="admin-dashboard-panel compact">
          <h2>Tình hình công ty</h2>
          <div className="admin-dashboard-mini-grid">
            <div><span>Tổng công ty</span><strong>{formatNumber(stats.totalCompanies)}</strong></div>
            <div><span>Đã xác thực</span><strong>{formatNumber(stats.verifiedCompanies)}</strong></div>
          </div>
        </section>
      </div>
    </section>
  );
}
