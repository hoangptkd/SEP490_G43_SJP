import { useCallback, useEffect, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminStatItem, AdminStatistics, AdminTrendPoint } from '../../types/admin';

type StatisticsPeriod = 'week' | 'month' | 'year';
type StatisticsSection = 'users' | 'companies' | 'jobs' | 'applications';

const emptyStats: AdminStatistics = {
  totalUsers: 0,
  totalCompanies: 0,
  totalJobs: 0,
  totalApplications: 0,
  totalViews: 0,
  usersByRole: [],
  usersByStatus: [],
  companiesByVerification: [],
  jobsByStatus: [],
  applicationsByStatus: [],
  usersTrend: [],
  candidateUsersTrend: [],
  employerUsersTrend: [],
  applicationsLast7Days: [],
  jobsLast7Days: [],
  updatedAt: '',
};

const sections: { value: StatisticsSection; label: string; description: string }[] = [
  { value: 'users', label: 'Người dùng', description: 'Ứng viên, nhà tuyển dụng và quản trị viên' },
  { value: 'companies', label: 'Công ty', description: 'Hồ sơ công ty và trạng thái xác thực' },
  { value: 'jobs', label: 'Việc làm', description: 'Tin tuyển dụng và trạng thái kiểm duyệt' },
  { value: 'applications', label: 'Ứng tuyển', description: 'Hồ sơ ứng tuyển và trạng thái xử lý' },
];

function formatNumber(value?: number) {
  return new Intl.NumberFormat('vi-VN').format(value ?? 0);
}

function formatDate(value?: string) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN');
}

function formatTrendDate(value: string | undefined, byMonth: boolean) {
  if (!value) return '—';
  const date = new Date(value);
  return byMonth
    ? date.toLocaleDateString('vi-VN', { month: '2-digit', year: 'numeric' })
    : date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
}

function toInputDate(date: Date) {
  return date.toISOString().slice(0, 10);
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Không thể tải thống kê';
  }
  return 'Không thể tải thống kê';
}

function labelFor(key: string) {
  const normalized = key?.toLowerCase();
  const labels: Record<string, string> = {
    job_seeker: 'Ứng viên',
    candidate: 'Ứng viên',
    employer: 'Nhà tuyển dụng',
    admin: 'Quản trị',
    active: 'Hoạt động',
    inactive: 'Chưa xác minh',
    suspended: 'Đã khóa',
    unverified: 'Chưa xác thực',
    pending: 'Chờ duyệt',
    verified: 'Đã xác thực',
    rejected: 'Bị từ chối',
    draft: 'Bản nháp',
    pending_review: 'Chờ duyệt',
    published: 'Đã duyệt',
    closed: 'Đã đóng',
    expired: 'Hết hạn',
    applied: 'Đã ứng tuyển',
    reviewed: 'Đã xem',
    shortlisted: 'Vào shortlist',
    interview_scheduled: 'Đã hẹn phỏng vấn',
    accepted: 'Đã nhận',
    withdrawn: 'Đã rút',
  };
  return labels[normalized] || key || 'Không xác định';
}

function StatCard({ label, value, tone }: { label: string; value: number; tone: string }) {
  return (
    <article className={`admin-stat-card ${tone}`}>
      <span>{label}</span>
      <strong>{formatNumber(value)}</strong>
    </article>
  );
}

function DistributionCard({ title, items }: { title: string; items?: AdminStatItem[] }) {
  const safeItems = items ?? [];
  const total = safeItems.reduce((sum, item) => sum + item.value, 0);
  return (
    <section className="admin-stat-panel">
      <h2>{title}</h2>
      {safeItems.length === 0 ? (
        <p className="muted">Chưa có dữ liệu.</p>
      ) : (
        <div className="admin-stat-bars">
          {safeItems.map((item) => {
            const percent = total > 0 ? Math.round((item.value / total) * 100) : 0;
            return (
              <div key={item.key} className="admin-stat-bar-row">
                <div className="admin-stat-bar-label">
                  <span>{labelFor(item.key)}</span>
                  <strong>{formatNumber(item.value)} ({percent}%)</strong>
                </div>
                <div className="admin-stat-bar-track">
                  <div style={{ width: `${Math.max(percent, item.value > 0 ? 5 : 0)}%` }} />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function TrendCard({ title, points, byMonth }: { title: string; points?: AdminTrendPoint[]; byMonth: boolean }) {
  const safePoints = points ?? [];
  const max = Math.max(1, ...safePoints.map((point) => point.value));
  return (
    <section className="admin-stat-panel">
      <h2>{title}</h2>
      <div className="admin-stat-trend">
        {safePoints.map((point) => {
          const height = Math.max(8, Math.round((point.value / max) * 120));
          return (
            <div key={point.date} className="admin-stat-trend-item">
              <strong>{formatNumber(point.value)}</strong>
              <div style={{ height }} />
              <span>{formatTrendDate(point.date, byMonth)}</span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function MultiTrendCard({
  title,
  series,
  byMonth,
}: {
  title: string;
  byMonth: boolean;
  series: { label: string; tone: string; points?: AdminTrendPoint[] }[];
}) {
  const safeSeries = series.map((item) => ({ ...item, points: item.points ?? [] }));
  const allPoints = safeSeries.flatMap((item) => item.points);
  const max = Math.max(1, ...allPoints.map((point) => point.value));
  const labels = safeSeries[0]?.points ?? [];

  return (
    <section className="admin-stat-panel wide">
      <div className="admin-stat-panel-heading">
        <h2>{title}</h2>
        <div className="admin-stat-legend">
          {safeSeries.map((item) => (
            <span key={item.label}><i className={item.tone} />{item.label}</span>
          ))}
        </div>
      </div>
      <div className="admin-stat-multi-trend">
        {labels.map((labelPoint, index) => (
          <div key={labelPoint.date} className="admin-stat-multi-group">
            <div className="admin-stat-multi-bars">
              {safeSeries.map((item) => {
                const point = item.points[index];
                const height = Math.max(8, Math.round(((point?.value ?? 0) / max) * 130));
                return (
                  <div key={item.label} className={`admin-stat-multi-bar ${item.tone}`} style={{ height }} title={`${item.label}: ${formatNumber(point?.value)}`} />
                );
              })}
            </div>
            <span>{formatTrendDate(labelPoint.date, byMonth)}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

export default function AdminStatisticsPage() {
  const currentYear = new Date().getFullYear();
  const years = Array.from({ length: 6 }, (_, index) => currentYear - index);
  const [period, setPeriod] = useState<StatisticsPeriod>('week');
  const [section, setSection] = useState<StatisticsSection>('users');
  const [weekDate, setWeekDate] = useState(toInputDate(new Date()));
  const [year, setYear] = useState(currentYear);
  const [month, setMonth] = useState(new Date().getMonth() + 1);
  const [stats, setStats] = useState<AdminStatistics>(emptyStats);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadStats = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await adminService.getStatistics(
        period,
        year,
        period === 'month' ? month : undefined,
        period === 'week' ? weekDate : undefined,
      );
      setStats({ ...emptyStats, ...data });
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [period, year, month, weekDate]);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  const isYearView = period === 'year';
  const rangeLabel = period === 'week'
    ? `tuần chứa ngày ${new Date(weekDate).toLocaleDateString('vi-VN')}`
    : period === 'month'
    ? `tháng ${month}/${year}`
    : `năm ${year}`;

  return (
    <section className="admin-page">
      <header className="admin-stat-hero">
        <div>
          <p className="admin-dashboard-eyebrow">Báo cáo vận hành</p>
          <h1>Thống kê</h1>
          <p>Phân tích người dùng, công ty, việc làm và ứng tuyển trong {rangeLabel}.</p>
        </div>
        <div className="admin-dashboard-refresh">
          <span>Cập nhật: {formatDate(stats.updatedAt)}</span>
          <button type="button" className="outline" onClick={loadStats} disabled={loading}>
            {loading ? 'Đang tải...' : 'Làm mới'}
          </button>
        </div>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}

      <section className="admin-stat-filter-panel">
        <div>
          <h2>Chọn khoảng thời gian</h2>
          <p>Xem thống kê theo tuần, tháng hoặc năm để so sánh các khoảng thời gian trước.</p>
        </div>
        <div className="admin-stat-filter-controls">
          <label>
            Kiểu xem
            <select value={period} onChange={(event) => setPeriod(event.target.value as StatisticsPeriod)}>
              <option value="week">Theo tuần</option>
              <option value="month">Theo tháng</option>
              <option value="year">Theo năm</option>
            </select>
          </label>
          {period === 'week' && (
            <label>
              Chọn ngày trong tuần
              <input type="date" value={weekDate} onChange={(event) => setWeekDate(event.target.value)} />
            </label>
          )}
          {period !== 'week' && (
            <label>
              Năm
              <select value={year} onChange={(event) => setYear(Number(event.target.value))}>
                {years.map((item) => (
                  <option key={item} value={item}>{item}</option>
                ))}
              </select>
            </label>
          )}
          {period === 'month' && (
            <label>
              Tháng
              <select value={month} onChange={(event) => setMonth(Number(event.target.value))}>
                {Array.from({ length: 12 }, (_, index) => index + 1).map((item) => (
                  <option key={item} value={item}>Tháng {item}</option>
                ))}
              </select>
            </label>
          )}
        </div>
      </section>

      <div className="admin-stat-card-grid">
        <StatCard label="Tổng người dùng" value={stats.totalUsers} tone="primary" />
        <StatCard label="Tổng công ty" value={stats.totalCompanies} tone="green" />
        <StatCard label="Tổng việc làm" value={stats.totalJobs} tone="amber" />
        <StatCard label="Tổng ứng tuyển" value={stats.totalApplications} tone="blue" />
        <StatCard label="Lượt xem việc làm" value={stats.totalViews} tone="purple" />
      </div>

      <div className="admin-stat-section-tabs">
        {sections.map((item) => (
          <button
            key={item.value}
            type="button"
            className={section === item.value ? 'active' : ''}
            onClick={() => setSection(item.value)}
          >
            <strong>{item.label}</strong>
            <span>{item.description}</span>
          </button>
        ))}
      </div>

      {section === 'users' && (
        <>
          <div className="admin-stat-focus-grid">
            <StatCard label="Tổng người dùng mới" value={stats.totalUsers} tone="primary" />
            <StatCard label="Ứng viên tham gia" value={stats.usersByRole.find((item) => item.key === 'job_seeker' || item.key === 'candidate')?.value ?? 0} tone="blue" />
            <StatCard label="Nhà tuyển dụng tham gia" value={stats.usersByRole.find((item) => item.key === 'employer')?.value ?? 0} tone="green" />
            <StatCard label="Tài khoản đang hoạt động" value={stats.usersByStatus.find((item) => item.key === 'active')?.value ?? 0} tone="amber" />
          </div>
          <div className="admin-stat-layout">
            <DistributionCard title="Người dùng theo vai trò" items={stats.usersByRole} />
            <DistributionCard title="Người dùng theo trạng thái" items={stats.usersByStatus} />
          </div>
          <MultiTrendCard
            title={isYearView ? `Người dùng tham gia theo từng tháng trong năm ${year}` : `Người dùng tham gia theo từng ngày trong ${rangeLabel}`}
            byMonth={isYearView}
            series={[
              { label: 'Tổng người dùng', tone: 'primary', points: stats.usersTrend },
              { label: 'Ứng viên', tone: 'blue', points: stats.candidateUsersTrend },
              { label: 'Nhà tuyển dụng', tone: 'green', points: stats.employerUsersTrend },
            ]}
          />
        </>
      )}

      {section === 'companies' && (
        <div className="admin-stat-layout">
          <DistributionCard title="Công ty theo xác thực" items={stats.companiesByVerification} />
          <StatCard label="Tổng công ty trong khoảng thời gian" value={stats.totalCompanies} tone="green" />
        </div>
      )}

      {section === 'jobs' && (
        <>
          <div className="admin-stat-layout">
            <DistributionCard title="Việc làm theo trạng thái" items={stats.jobsByStatus} />
            <StatCard label="Lượt xem việc làm" value={stats.totalViews} tone="purple" />
          </div>
          <TrendCard
            title={isYearView ? `Việc làm tạo mới theo từng tháng trong năm ${year}` : `Việc làm tạo mới theo từng ngày trong ${rangeLabel}`}
            points={stats.jobsLast7Days}
            byMonth={isYearView}
          />
        </>
      )}

      {section === 'applications' && (
        <>
          <div className="admin-stat-layout">
            <DistributionCard title="Ứng tuyển theo trạng thái" items={stats.applicationsByStatus} />
            <StatCard label="Tổng ứng tuyển trong khoảng thời gian" value={stats.totalApplications} tone="blue" />
          </div>
          <TrendCard
            title={isYearView ? `Ứng tuyển theo từng tháng trong năm ${year}` : `Ứng tuyển theo từng ngày trong ${rangeLabel}`}
            points={stats.applicationsLast7Days}
            byMonth={isYearView}
          />
        </>
      )}
    </section>
  );
}
