import { useCallback, useEffect, useMemo, useState } from 'react';
import { adminService } from '../../services/adminService';
import type { AdminStatItem, AdminStatistics, AdminTrendPoint } from '../../types/admin';

type StatisticsPeriod = 'all' | 'week' | 'month' | 'year';
type StatisticsSection = 'users' | 'companies' | 'jobs' | 'applications' | 'interviews' | 'ai';

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
  interviewSessions: 0,
  interviewCompleted: 0,
  interviewInProgress: 0,
  aiAnswersEvaluated: 0,
  aiRecommendations: 0,
  aiRankingJobs: 0,
  averageInterviewScore: 0,
  interviewsByStatus: [],
  interviewsTrend: [],
  updatedAt: '',
};

const sections: { value: StatisticsSection; label: string }[] = [
  { value: 'users', label: 'Người dùng' },
  { value: 'companies', label: 'Công ty' },
  { value: 'jobs', label: 'Việc làm' },
  { value: 'applications', label: 'Ứng tuyển' },
  { value: 'interviews', label: 'Phỏng vấn' },
  { value: 'ai', label: 'AI Usage' },
];

function formatNumber(value?: number) {
  return new Intl.NumberFormat('vi-VN').format(value ?? 0);
}

function formatTime(value?: string) {
  if (!value) return '—';
  return new Date(value).toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatTrendDate(value: string | undefined, byMonth: boolean) {
  if (!value) return '—';
  const date = new Date(value);
  return byMonth
    ? date.toLocaleDateString('vi-VN', { month: 'short' })
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
    closed: 'Đã ẩn',
    expired: 'Hết hạn',
    applied: 'Đã ứng tuyển',
    reviewed: 'Đã xem',
    shortlisted: 'Shortlist',
    interview_scheduled: 'Hẹn PV',
    accepted: 'Đã nhận',
    withdrawn: 'Đã rút',
  };
  return labels[normalized] || key || 'Khác';
}

function findValue(items: AdminStatItem[] | undefined, ...keys: string[]) {
  const list = items ?? [];
  for (const key of keys) {
    const found = list.find((item) => item.key?.toLowerCase() === key.toLowerCase());
    if (found) return found.value;
  }
  return 0;
}

function Metric({
  label,
  value,
  hint,
}: {
  label: string;
  value: number;
  hint?: string;
}) {
  return (
    <article className="stats-metric">
      <span>{label}</span>
      <strong>{formatNumber(value)}</strong>
      {hint ? <p>{hint}</p> : null}
    </article>
  );
}

function Breakdown({ title, items }: { title: string; items?: AdminStatItem[] }) {
  const safeItems = [...(items ?? [])].sort((a, b) => b.value - a.value);
  const total = safeItems.reduce((sum, item) => sum + item.value, 0);

  return (
    <section className="stats-panel">
      <header className="stats-panel-head">
        <h2>{title}</h2>
        <span>{formatNumber(total)} tổng</span>
      </header>
      {safeItems.length === 0 ? (
        <p className="stats-empty">Chưa có dữ liệu trong khoảng thời gian này.</p>
      ) : (
        <ul className="stats-breakdown">
          {safeItems.map((item) => {
            const percent = total > 0 ? Math.round((item.value / total) * 100) : 0;
            return (
              <li key={item.key}>
                <div className="stats-breakdown-top">
                  <span>{labelFor(item.key)}</span>
                  <strong>
                    {formatNumber(item.value)}
                    <em>{percent}%</em>
                  </strong>
                </div>
                <div className="stats-breakdown-track">
                  <div style={{ width: `${percent}%` }} />
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

function TrendChart({
  title,
  points,
  byMonth,
  tone = 'primary',
}: {
  title: string;
  points?: AdminTrendPoint[];
  byMonth: boolean;
  tone?: string;
}) {
  const safePoints = points ?? [];
  const max = Math.max(1, ...safePoints.map((point) => point.value));
  const total = safePoints.reduce((sum, point) => sum + point.value, 0);

  return (
    <section className="stats-panel">
      <header className="stats-panel-head">
        <h2>{title}</h2>
        <span>{formatNumber(total)} lượt</span>
      </header>
      {safePoints.length === 0 ? (
        <p className="stats-empty">Chưa có dữ liệu xu hướng.</p>
      ) : (
        <div className="stats-trend">
          {safePoints.map((point) => {
            const height = Math.max(6, Math.round((point.value / max) * 140));
            return (
              <div key={point.date} className="stats-trend-col" title={`${formatNumber(point.value)}`}>
                <span className="stats-trend-value">{point.value > 0 ? formatNumber(point.value) : ''}</span>
                <div className={`stats-trend-bar ${tone}`} style={{ height }} />
                <span className="stats-trend-label">{formatTrendDate(point.date, byMonth)}</span>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function MultiTrendChart({
  title,
  series,
  byMonth,
}: {
  title: string;
  byMonth: boolean;
  series: { label: string; tone: string; points?: AdminTrendPoint[] }[];
}) {
  const safeSeries = series.map((item) => ({ ...item, points: item.points ?? [] }));
  const labels = safeSeries[0]?.points ?? [];
  const max = Math.max(1, ...safeSeries.flatMap((item) => item.points.map((point) => point.value)));

  return (
    <section className="stats-panel stats-panel-wide">
      <header className="stats-panel-head">
        <h2>{title}</h2>
        <div className="stats-legend">
          {safeSeries.map((item) => (
            <span key={item.label}>
              <i className={item.tone} />
              {item.label}
            </span>
          ))}
        </div>
      </header>
      {labels.length === 0 ? (
        <p className="stats-empty">Chưa có dữ liệu xu hướng.</p>
      ) : (
        <div className="stats-multi-trend">
          {labels.map((labelPoint, index) => (
            <div key={labelPoint.date} className="stats-multi-col">
              <div className="stats-multi-bars">
                {safeSeries.map((item) => {
                  const point = item.points[index];
                  const height = Math.max(4, Math.round(((point?.value ?? 0) / max) * 132));
                  return (
                    <div
                      key={item.label}
                      className={`stats-multi-bar ${item.tone}`}
                      style={{ height }}
                      title={`${item.label}: ${formatNumber(point?.value)}`}
                    />
                  );
                })}
              </div>
              <span>{formatTrendDate(labelPoint.date, byMonth)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

export default function AdminStatisticsPage() {
  const currentYear = new Date().getFullYear();
  const years = useMemo(() => Array.from({ length: 6 }, (_, index) => currentYear - index), [currentYear]);
  const [period, setPeriod] = useState<StatisticsPeriod>('all');
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
        period === 'month' || period === 'year' ? year : undefined,
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

  const isYearView = period === 'year' || period === 'all';
  const isAll = period === 'all';
  const rangeLabel = period === 'all'
    ? 'Toàn hệ thống'
    : period === 'week'
    ? `Tuần của ${new Date(weekDate).toLocaleDateString('vi-VN')}`
    : period === 'month'
    ? `Tháng ${month}/${year}`
    : `Năm ${year}`;
  const metricHint = isAll ? 'Tổng hiện có' : 'Phát sinh trong kỳ';

  return (
    <section className="admin-page stats-page">
      <header className="stats-hero">
        <div>
          <h1>Thống kê</h1>
          <p>Tổng quan vận hành · {rangeLabel}</p>
        </div>
        <div className="stats-hero-actions">
          <span>Cập nhật {formatTime(stats.updatedAt)}</span>
          <button type="button" className="outline" onClick={loadStats} disabled={loading}>
            {loading ? 'Đang tải...' : 'Làm mới'}
          </button>
        </div>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}

      <div className="stats-toolbar">
        <div className="stats-segment">
          {([
            { value: 'all', label: 'Tất cả' },
            { value: 'week', label: 'Tuần' },
            { value: 'month', label: 'Tháng' },
            { value: 'year', label: 'Năm' },
          ] as const).map((item) => (
            <button
              key={item.value}
              type="button"
              className={period === item.value ? 'active' : ''}
              onClick={() => setPeriod(item.value)}
            >
              {item.label}
            </button>
          ))}
        </div>

        {!isAll && (
          <div className="stats-toolbar-fields">
            {period === 'week' && (
              <label>
                Ngày
                <input type="date" value={weekDate} onChange={(event) => setWeekDate(event.target.value)} />
              </label>
            )}
            {(period === 'month' || period === 'year') && (
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
                    <option key={item} value={item}>{item}</option>
                  ))}
                </select>
              </label>
            )}
          </div>
        )}
      </div>

      <div className="stats-metric-row">
        <Metric label="Người dùng" value={stats.totalUsers} hint={metricHint} />
        <Metric label="Công ty" value={stats.totalCompanies} hint={metricHint} />
        <Metric label="Việc làm" value={stats.totalJobs} hint={metricHint} />
        <Metric label="Ứng tuyển" value={stats.totalApplications} hint={metricHint} />
        <Metric label="Lượt xem" value={stats.totalViews} hint={isAll ? 'Tổng lượt xem' : 'Views tin trong kỳ'} />
      </div>

      <nav className="stats-tabs" aria-label="Phân nhóm thống kê">
        {sections.map((item) => (
          <button
            key={item.value}
            type="button"
            className={section === item.value ? 'active' : ''}
            onClick={() => setSection(item.value)}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {section === 'users' && (
        <div className="stats-content">
          <div className="stats-content-grid">
            <Breakdown title="Theo vai trò" items={stats.usersByRole} />
            <Breakdown title="Theo trạng thái" items={stats.usersByStatus} />
          </div>
          <div className="stats-highlight-row">
            <Metric label="Ứng viên" value={findValue(stats.usersByRole, 'job_seeker', 'candidate')} />
            <Metric label="Nhà tuyển dụng" value={findValue(stats.usersByRole, 'employer')} />
            <Metric label="Đang hoạt động" value={findValue(stats.usersByStatus, 'active')} />
          </div>
          <MultiTrendChart
            title={isAll ? 'Xu hướng 12 tháng gần nhất' : 'Xu hướng tham gia'}
            byMonth={isYearView}
            series={[
              { label: 'Tổng', tone: 'primary', points: stats.usersTrend },
              { label: 'Ứng viên', tone: 'blue', points: stats.candidateUsersTrend },
              { label: 'NTD', tone: 'green', points: stats.employerUsersTrend },
            ]}
          />
        </div>
      )}

      {section === 'companies' && (
        <div className="stats-content">
          <div className="stats-content-grid">
            <Breakdown title="Theo trạng thái xác thực" items={stats.companiesByVerification} />
            <section className="stats-panel stats-summary-panel">
              <header className="stats-panel-head">
                <h2>Tóm tắt công ty</h2>
              </header>
              <div className="stats-summary-list">
                <div>
                  <span>{isAll ? 'Tổng công ty' : 'Tổng trong kỳ'}</span>
                  <strong>{formatNumber(stats.totalCompanies)}</strong>
                </div>
                <div>
                  <span>Đã xác thực</span>
                  <strong>{formatNumber(findValue(stats.companiesByVerification, 'verified'))}</strong>
                </div>
                <div>
                  <span>Chờ duyệt</span>
                  <strong>{formatNumber(findValue(stats.companiesByVerification, 'pending'))}</strong>
                </div>
                <div>
                  <span>Bị từ chối</span>
                  <strong>{formatNumber(findValue(stats.companiesByVerification, 'rejected'))}</strong>
                </div>
              </div>
            </section>
          </div>
        </div>
      )}

      {section === 'jobs' && (
        <div className="stats-content">
          <div className="stats-content-grid">
            <Breakdown title="Theo trạng thái tin" items={stats.jobsByStatus} />
            <section className="stats-panel stats-summary-panel">
              <header className="stats-panel-head">
                <h2>Tóm tắt việc làm</h2>
              </header>
              <div className="stats-summary-list">
                <div>
                  <span>{isAll ? 'Tổng tin' : 'Tổng tin trong kỳ'}</span>
                  <strong>{formatNumber(stats.totalJobs)}</strong>
                </div>
                <div>
                  <span>Đã duyệt</span>
                  <strong>{formatNumber(findValue(stats.jobsByStatus, 'published'))}</strong>
                </div>
                <div>
                  <span>Chờ duyệt</span>
                  <strong>{formatNumber(findValue(stats.jobsByStatus, 'pending_review', 'pending'))}</strong>
                </div>
                <div>
                  <span>Lượt xem</span>
                  <strong>{formatNumber(stats.totalViews)}</strong>
                </div>
              </div>
            </section>
          </div>
          <TrendChart
            title={isAll ? 'Tin tạo mới · 12 tháng gần nhất' : 'Tin tạo mới theo thời gian'}
            points={stats.jobsLast7Days}
            byMonth={isYearView}
            tone="amber"
          />
        </div>
      )}

      {section === 'applications' && (
        <div className="stats-content">
          <div className="stats-content-grid">
            <Breakdown title="Theo trạng thái hồ sơ" items={stats.applicationsByStatus} />
            <section className="stats-panel stats-summary-panel">
              <header className="stats-panel-head">
                <h2>Tóm tắt ứng tuyển</h2>
              </header>
              <div className="stats-summary-list">
                <div>
                  <span>Tổng hồ sơ</span>
                  <strong>{formatNumber(stats.totalApplications)}</strong>
                </div>
                <div>
                  <span>Đã nộp</span>
                  <strong>{formatNumber(findValue(stats.applicationsByStatus, 'applied'))}</strong>
                </div>
                <div>
                  <span>Shortlist</span>
                  <strong>{formatNumber(findValue(stats.applicationsByStatus, 'shortlisted'))}</strong>
                </div>
                <div>
                  <span>Đã nhận</span>
                  <strong>{formatNumber(findValue(stats.applicationsByStatus, 'accepted'))}</strong>
                </div>
              </div>
            </section>
          </div>
          <TrendChart
            title={isAll ? 'Ứng tuyển · 12 tháng gần nhất' : 'Ứng tuyển theo thời gian'}
            points={stats.applicationsLast7Days}
            byMonth={isYearView}
            tone="blue"
          />
        </div>
      )}

      {section === 'interviews' && (
        <div className="stats-content">
          <div className="stats-highlight-row">
            <Metric label="Tổng phiên PV" value={stats.interviewSessions ?? 0} hint={metricHint} />
            <Metric label="Đã hoàn thành" value={stats.interviewCompleted ?? 0} />
            <Metric label="Đang diễn ra" value={stats.interviewInProgress ?? 0} />
            <Metric label="Điểm TB" value={Math.round(stats.averageInterviewScore ?? 0)} hint="/100" />
          </div>
          <div className="stats-content-grid">
            <Breakdown title="Theo trạng thái phiên" items={stats.interviewsByStatus} />
            <section className="stats-panel stats-summary-panel">
              <header className="stats-panel-head">
                <h2>Interview statistics</h2>
              </header>
              <div className="stats-summary-list">
                <div>
                  <span>Phiên phỏng vấn</span>
                  <strong>{formatNumber(stats.interviewSessions)}</strong>
                </div>
                <div>
                  <span>Hoàn thành</span>
                  <strong>{formatNumber(stats.interviewCompleted)}</strong>
                </div>
                <div>
                  <span>Đang chạy</span>
                  <strong>{formatNumber(stats.interviewInProgress)}</strong>
                </div>
                <div>
                  <span>Điểm trung bình</span>
                  <strong>{(stats.averageInterviewScore ?? 0).toFixed(1)}</strong>
                </div>
              </div>
            </section>
          </div>
          <TrendChart
            title={isAll ? 'Phiên phỏng vấn · 12 tháng gần nhất' : 'Phiên phỏng vấn theo thời gian'}
            points={stats.interviewsTrend}
            byMonth={isYearView}
            tone="primary"
          />
        </div>
      )}

      {section === 'ai' && (
        <div className="stats-content">
          <div className="stats-highlight-row">
            <Metric label="Câu trả lời AI đã chấm" value={stats.aiAnswersEvaluated ?? 0} hint={metricHint} />
            <Metric label="Gợi ý việc làm AI" value={stats.aiRecommendations ?? 0} />
            <Metric label="Ranking jobs AI" value={stats.aiRankingJobs ?? 0} />
            <Metric label="Phiên PV AI" value={stats.interviewSessions ?? 0} />
          </div>
          <section className="stats-panel">
            <header className="stats-panel-head">
              <h2>Monitor AI Usage Stats</h2>
            </header>
            <div className="stats-summary-list">
              <div>
                <span>Feedback AI đã hoàn tất</span>
                <strong>{formatNumber(stats.aiAnswersEvaluated)}</strong>
              </div>
              <div>
                <span>Recommendations đã tạo</span>
                <strong>{formatNumber(stats.aiRecommendations)}</strong>
              </div>
              <div>
                <span>AI ranking jobs</span>
                <strong>{formatNumber(stats.aiRankingJobs)}</strong>
              </div>
              <div>
                <span>Tỷ lệ hoàn thành PV</span>
                <strong>
                  {(stats.interviewSessions ?? 0) > 0
                    ? `${Math.round(((stats.interviewCompleted ?? 0) / (stats.interviewSessions ?? 1)) * 100)}%`
                    : '0%'}
                </strong>
              </div>
            </div>
          </section>
        </div>
      )}
    </section>
  );
}
