import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { employerService, type EmployerDashboardStats } from '../../services/employerService';
import axios from 'axios';
import { format } from 'date-fns';

function readError(error: unknown) {
  if (axios.isAxiosError(error) && error.response?.data?.message) {
    return error.response.data.message;
  }
  if (error instanceof Error) return error.message;
  return 'Có lỗi xảy ra, vui lòng thử lại sau.';
}

function getRelativeTime(dateString: string) {
  const diff = Date.now() - new Date(dateString).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return 'Vừa xong';
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.floor(hours / 24);
  if (days === 1) return `Hôm qua`;
  if (days < 7) return `${days} ngày trước`;
  return format(new Date(dateString), 'dd/MM/yyyy');
}

const EASE_OUT = [0.16, 1, 0.3, 1] as const;

const fadeUp = {
  initial: { opacity: 0, y: 15 },
  animate: { opacity: 1, y: 0 },
};

function StatCard({ title, value, growth, icon, colorVariant }: { title: string, value: number, growth: number | undefined, icon: string, colorVariant: 'primary' | 'warning' | 'text' }) {
  const isPositive = growth !== undefined && growth > 0;
  const isNegative = growth !== undefined && growth < 0;
  const isNeutral = growth === 0 || growth === undefined;

  let trendColor = 'var(--outline)';
  if (isPositive) trendColor = 'var(--success)';
  if (isNegative) trendColor = 'var(--danger)';

  return (
    <div className="card" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: 16, borderLeft: colorVariant === 'warning' ? '4px solid var(--warning)' : 'none' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div className="muted" style={{ fontSize: '0.9rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px' }}>{title}</div>
        <div style={{ fontSize: '1.5rem', opacity: 0.8 }}>{icon}</div>
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
        <div style={{ fontSize: '2.5rem', fontWeight: 800, color: `var(--${colorVariant})`, lineHeight: 1 }}>{value}</div>
        {!isNeutral && (
          <div style={{ 
            display: 'flex', alignItems: 'center', gap: 4, 
            fontSize: '0.85rem', fontWeight: 600, 
            color: trendColor, background: `${trendColor}1A`, 
            padding: '4px 8px', borderRadius: 12 
          }}>
            {isPositive ? '↑' : '↓'} {Math.abs(growth)}%
          </div>
        )}
      </div>
    </div>
  );
}

export default function EmployerDashboardPage() {
  const [stats, setStats] = useState<EmployerDashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    employerService.getDashboardStats()
      .then(setStats)
      .catch(err => setError(readError(err)))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div style={{ padding: '64px 0', textAlign: 'center' }}>
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
          style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto 16px' }}>
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
        <p className="muted" style={{ fontSize: '1.1rem' }}>Đang tải phân tích...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="error-panel" style={{ margin: '32px 0' }}>
        <p>{error}</p>
      </div>
    );
  }

  const totalApp = stats?.totalApplications || 0;
  const statusColors: Record<string, string> = {
    'pending': 'var(--warning)',
    'reviewed': 'var(--primary)',
    'interview': 'var(--accent)',
    'offered': 'var(--success)',
    'hired': 'var(--match)',
    'rejected': 'var(--danger)',
  };

  const trendData = stats?.applicationTrend || [];
  const maxTrend = Math.max(...trendData.map(t => t.count), 5); // Ensure at least 5 for scale

  // Convert status to an ordered array for the conversion funnel
  const statusCounts = stats?.applicationsByStatus || {};
  const offeredCount = (statusCounts['offered'] || 0) + (statusCounts['hired'] || 0);
  const conversionRate = totalApp > 0 ? ((offeredCount / totalApp) * 100).toFixed(1) : '0.0';

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate" transition={{ duration: 0.3, ease: EASE_OUT }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 32 }}>
        <div>
          <h1 style={{ fontSize: '1.8rem', marginBottom: 4 }}>Tổng quan</h1>
          <p className="muted">Cập nhật lúc {format(new Date(), 'HH:mm - dd/MM/yyyy')}</p>
        </div>
        <Link to="/employer/jobs/new" className="button primary">
          + Đăng tin mới
        </Link>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 24, marginBottom: 32 }}>
        <StatCard title="Tổng số việc làm" value={stats?.totalJobs || 0} growth={stats?.jobGrowthPercentage} icon="📊" colorVariant="text" />
        <StatCard title="Việc làm đang mở" value={stats?.activeJobs || 0} growth={0} icon="🟢" colorVariant="primary" />
        <StatCard title="Tổng số ứng tuyển" value={stats?.totalApplications || 0} growth={stats?.applicationGrowthPercentage} icon="👥" colorVariant="text" />
        <StatCard title="Hồ sơ chờ duyệt" value={stats?.pendingApplications || 0} growth={0} icon="⏳" colorVariant="warning" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 24, alignItems: 'start', marginBottom: 32 }}>
        {/* Activity Chart Section */}
        <section className="card" style={{ padding: 32 }}>
          <h3 style={{ margin: '0 0 24px 0', fontSize: '1.2rem' }}>Xu hướng ứng tuyển (14 ngày qua)</h3>
          
          <div style={{ height: 240, display: 'flex', alignItems: 'flex-end', gap: 8, paddingBottom: 24, borderBottom: '1px solid var(--border)', position: 'relative' }}>
            {/* Y-axis labels */}
            <div style={{ position: 'absolute', left: 0, top: 0, bottom: 24, width: '100%', pointerEvents: 'none' }}>
              {[1, 0.5, 0].map((ratio) => (
                <div key={ratio} style={{ position: 'absolute', bottom: `${ratio * 100}%`, width: '100%', borderTop: ratio > 0 ? '1px dashed var(--border)' : 'none' }}>
                  <span style={{ position: 'absolute', top: -10, left: 0, fontSize: '0.75rem', color: 'var(--outline)', background: 'var(--surface)', paddingRight: 8 }}>
                    {Math.round(maxTrend * ratio)}
                  </span>
                </div>
              ))}
            </div>
            
            {/* Bars */}
            <div style={{ flex: 1, display: 'flex', alignItems: 'flex-end', gap: '2%', height: '100%', paddingLeft: 40 }}>
              {trendData.map((day, idx) => (
                <div key={idx} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, position: 'relative' }}>
                  <motion.div 
                    initial={{ height: 0 }}
                    animate={{ height: `${(day.count / maxTrend) * 100}%` }}
                    transition={{ duration: 0.5, delay: idx * 0.02, ease: EASE_OUT }}
                    style={{ 
                      width: '100%', 
                      background: day.count > 0 ? 'var(--primary)' : 'var(--outline)',
                      opacity: day.count > 0 ? 0.9 : 0.2,
                      borderRadius: '4px 4px 0 0',
                      cursor: 'pointer',
                      minHeight: 2
                    }}
                    title={`${format(new Date(day.date), 'dd/MM')}: ${day.count} hồ sơ`}
                  />
                  <div style={{ position: 'absolute', bottom: -28, fontSize: '0.7rem', color: 'var(--outline)', whiteSpace: 'nowrap' }}>
                    {idx % 2 === 0 ? format(new Date(day.date), 'dd/MM') : ''}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Funnel & Conversion Section */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          <section className="card" style={{ padding: 32 }}>
            <h3 style={{ margin: '0 0 8px 0', fontSize: '1.2rem' }}>Tỷ lệ chuyển đổi</h3>
            <p className="muted" style={{ fontSize: '0.9rem', marginBottom: 24 }}>Hiệu quả tuyển dụng tổng thể</p>
            
            <div style={{ display: 'flex', alignItems: 'center', gap: 24, marginBottom: 24 }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: '2.5rem', fontWeight: 800, color: 'var(--success)' }}>{conversionRate}%</div>
                <div className="muted" style={{ fontSize: '0.85rem' }}>Ứng viên nhận Offer</div>
              </div>
              <div style={{ flex: 1, textAlign: 'right' }}>
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>{offeredCount} <span className="muted" style={{ fontSize: '1rem', fontWeight: 400 }}>/ {totalApp}</span></div>
                <div className="muted" style={{ fontSize: '0.85rem' }}>Đã tuyển được</div>
              </div>
            </div>

            <div style={{ display: 'flex', height: 16, borderRadius: 8, overflow: 'hidden', marginBottom: 16 }}>
              {Object.entries(statusCounts).map(([status, count]) => {
                if (count === 0) return null;
                return (
                  <div
                    key={status}
                    style={{
                      width: `${(count / totalApp) * 100}%`,
                      background: statusColors[status.toLowerCase()] || 'var(--outline)',
                    }}
                    title={`${status.toUpperCase()}: ${count}`}
                  />
                )
              })}
            </div>
            
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px 16px' }}>
              {Object.entries(statusCounts).map(([status, count]) => (
                <div key={status} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <div style={{ width: 8, height: 8, borderRadius: '50%', background: statusColors[status.toLowerCase()] || 'var(--outline)' }} />
                    <span style={{ textTransform: 'capitalize', color: 'var(--outline)' }}>{status}</span>
                  </div>
                  <span style={{ fontWeight: 600 }}>{count}</span>
                </div>
              ))}
            </div>
          </section>
        </div>
      </div>

      {/* Recent Activity Table */}
      <section className="card" style={{ overflow: 'hidden', padding: 0 }}>
        <div style={{ padding: '24px 32px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ margin: 0, fontSize: '1.2rem' }}>Hoạt động mới nhất</h3>
          <Link to="/employer/applications" className="button-link" style={{ fontSize: '0.9rem' }}>Xem tất cả</Link>
        </div>
        {stats?.recentApplications && stats.recentApplications.length > 0 ? (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ background: 'var(--surface)' }}>
                  <th style={{ padding: '16px 32px', borderBottom: '1px solid var(--border)', fontWeight: 600, color: 'var(--outline)', fontSize: '0.85rem', textTransform: 'uppercase' }}>Ứng viên</th>
                  <th style={{ padding: '16px 32px', borderBottom: '1px solid var(--border)', fontWeight: 600, color: 'var(--outline)', fontSize: '0.85rem', textTransform: 'uppercase' }}>Vị trí ứng tuyển</th>
                  <th style={{ padding: '16px 32px', borderBottom: '1px solid var(--border)', fontWeight: 600, color: 'var(--outline)', fontSize: '0.85rem', textTransform: 'uppercase' }}>Trạng thái</th>
                  <th style={{ padding: '16px 32px', borderBottom: '1px solid var(--border)', fontWeight: 600, color: 'var(--outline)', fontSize: '0.85rem', textTransform: 'uppercase', textAlign: 'right' }}>Thời gian</th>
                </tr>
              </thead>
              <tbody>
                {stats.recentApplications.map(app => {
                  const candidateName = app.candidate?.fullName?.trim() || 'Ứng viên';
                  return (
                  <tr key={app.id} style={{ borderBottom: '1px solid var(--border)' }}>
                    <td style={{ padding: '20px 32px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <div style={{ 
                          width: 40, height: 40, borderRadius: '50%', 
                          background: 'var(--surface)', color: 'var(--primary)',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          fontWeight: 700, fontSize: '1.1rem'
                        }}>
                          {candidateName.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <div style={{ fontWeight: 600 }}>{candidateName}</div>
                          <div className="muted" style={{ fontSize: '0.85rem' }}>{app.candidate?.headline || 'Ứng viên'}</div>
                        </div>
                      </div>
                    </td>
                    <td style={{ padding: '20px 32px' }}>
                      <div style={{ fontWeight: 500 }}>{app.job.title}</div>
                      <Link to={`/employer/jobs`} className="muted" style={{ fontSize: '0.85rem', textDecoration: 'underline' }}>Mở chi tiết việc làm</Link>
                    </td>
                    <td style={{ padding: '20px 32px' }}>
                      <span className="chip" style={{
                        background: `${statusColors[app.status.toLowerCase()] || 'var(--outline)'}1A`,
                        color: statusColors[app.status.toLowerCase()] || 'var(--text)',
                        border: `1px solid ${statusColors[app.status.toLowerCase()] || 'var(--outline)'}40`
                      }}>
                        {app.status.toUpperCase()}
                      </span>
                    </td>
                    <td style={{ padding: '20px 32px', textAlign: 'right' }}>
                      <div style={{ fontWeight: 500 }}>{getRelativeTime(app.submittedAt)}</div>
                      <div className="muted" style={{ fontSize: '0.85rem' }}>{format(new Date(app.submittedAt), 'HH:mm')}</div>
                    </td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <div style={{ padding: '64px 32px', textAlign: 'center' }}>
            <div style={{ fontSize: '3rem', marginBottom: 16 }}>📭</div>
            <p className="muted" style={{ fontSize: '1.1rem' }}>Chưa có ứng viên nào nộp hồ sơ gần đây.</p>
          </div>
        )}
      </section>
    </motion.div>
  );
}
