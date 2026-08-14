import { useState, useEffect, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { employerService } from '../../services/employerService';
import axios from 'axios';
import { format } from 'date-fns';
import {
  IconAlert,
  IconBell,
  IconBriefcase,
  IconCalendar,
  IconClipboard,
  IconHandshake,
  IconMail,
  IconProfile,
  IconUsers,
} from '../../components/icons/PortalNavIcons';
import type { Company } from '../../types/job';
import '../../styles/admin.css';

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
  if (days === 1) return 'Hôm qua';
  if (days < 7) return `${days} ngày trước`;
  return format(new Date(dateString), 'dd/MM/yyyy');
}

const EASE_OUT = [0.16, 1, 0.3, 1] as const;
const fadeUp = {
  initial: { opacity: 0, y: 15 },
  animate: { opacity: 1, y: 0 },
};

function TopActionCard({ 
  title, count, icon, buttonText, buttonLink, colorClass 
}: { 
  title: string, count: number, icon: ReactNode, buttonText: string, buttonLink: string, colorClass: string 
}) {
  return (
    <div className={`top-action-card ${colorClass}`}>
      <div className="tac-header">
        <div className="tac-icon mono-icon">{icon}</div>
        <div className="tac-badge">{count}</div>
      </div>
      <div className="tac-title">{title}</div>
      <Link to={buttonLink} className="tac-btn">{buttonText}</Link>
    </div>
  );
}

function KpiCard({ title, value, subtext }: { title: string, value: number, subtext?: string }) {
  return (
    <div className="kpi-card">
      <div className="kpi-title">{title}</div>
      <div className="kpi-value">{value}</div>
      {subtext && <div className="kpi-subtext">{subtext}</div>}
    </div>
  );
}

export default function EmployerDashboardPage() {
  const [stats, setStats] = useState<any>(null);
  const [company, setCompany] = useState<Company | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [visibleTasksCount, setVisibleTasksCount] = useState(5);

  useEffect(() => {
    Promise.all([
      employerService.getDashboardStats(),
      employerService.getCompanyProfile().catch(() => null)
    ])
      .then(([statsData, compData]) => {
        setStats(statsData);
        setCompany(compData);
      })
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
        <p className="muted" style={{ fontSize: '1.1rem' }}>Đang tải bảng điều khiển...</p>
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

  const actionSummary = stats?.actionSummary || { pendingApplicationsCount: 0, todayInterviewsCount: 0, expiringJobsCount: 0, unreadMessagesCount: 0 };
  const pipeline = stats?.pipelineStats || { appliedCount: 0, reviewedCount: 0, interviewCount: 0, offerCount: 0, hiredCount: 0 };
  const activeJobs = stats?.activeJobsList || [];
  const upcomingInterviews = stats?.upcomingInterviews || [];
  const recentActivities = stats?.recentActivities || [];

  return (
    <motion.div className="topcv-dashboard" variants={fadeUp} initial="initial" animate="animate" transition={{ duration: 0.3, ease: EASE_OUT }}>
      <div className="topcv-header">
        <h1 style={{ fontSize: '1.5rem', margin: 0, display: 'flex', alignItems: 'center', gap: 10 }}>
          <IconBriefcase size={24} /> Xin chào, Nhà tuyển dụng
          {company && (
            company.verificationStatus === 'verified' ? (
              <span style={{ fontSize: '0.85rem', padding: '4px 10px', borderRadius: '20px', background: '#dcfce7', color: '#166534', border: '1px solid #bbf7d0', marginLeft: '8px', display: 'flex', alignItems: 'center', gap: '4px', fontWeight: 500 }}>
                ✅ Đã xác thực
              </span>
            ) : (
              <span style={{ fontSize: '0.85rem', padding: '4px 10px', borderRadius: '20px', background: '#fef2f2', color: '#991b1b', border: '1px solid #fecaca', marginLeft: '8px', display: 'flex', alignItems: 'center', gap: '4px', fontWeight: 500 }}>
                ⚠️ Chưa xác thực
              </span>
            )
          )}
        </h1>
        <div style={{ display: 'flex', gap: 12 }}>
          <Link to="/employer/jobs?action=new" className="button primary" style={{ borderRadius: 6 }}>
            + Đăng tin mới
          </Link>
        </div>
      </div>

      <div className="topcv-layout">
        {/* Main Content */}
        <div className="topcv-main">
          
          {/* Section 1 */}
          <section className="topcv-section">
            <h2 className="section-title">Cần xử lý hôm nay <span style={{ color: 'var(--outline)', fontSize: '0.9rem', fontWeight: 400 }}>ⓘ</span></h2>
            <div className="top-actions-grid">
              <TopActionCard 
                title="Ứng viên cần xử lý" count={actionSummary.pendingApplicationsCount} 
                icon={<IconProfile size={22} />} buttonText="Xem ngay" buttonLink="/employer/applications?status=INTERVIEW_SCHEDULED" 
                colorClass="tac-red" 
              />
              <TopActionCard 
                title="Lịch phỏng vấn hôm nay" count={actionSummary.todayInterviewsCount} 
                icon={<IconCalendar size={22} />} buttonText="Xem lịch" buttonLink="/employer/applications?status=INTERVIEW_SCHEDULED" 
                colorClass="tac-orange" 
              />
              <TopActionCard 
                title="Tin sắp hết hạn" count={actionSummary.expiringJobsCount} 
                icon={<IconAlert size={22} />} buttonText="Gia hạn" buttonLink="/employer/jobs" 
                colorClass="tac-yellow" 
              />
              <TopActionCard 
                title="Tin nhắn chưa đọc" count={actionSummary.unreadMessagesCount} 
                icon={<IconMail size={22} />} buttonText="Mở hộp thư" buttonLink="/employer/notifications" 
                colorClass="tac-blue" 
              />
            </div>
          </section>

          {/* Section 2 */}
          <section className="topcv-section">
            <h2 className="section-title">Tổng quan hiệu quả</h2>
            <div className="kpi-grid">
              <KpiCard title="Tin đang chạy" value={stats.activeJobs} />
              <KpiCard title="Tổng ứng viên" value={stats.totalApplications} subtext="Toàn thời gian" />
              <KpiCard title="Lượt xem tin" value={activeJobs.reduce((acc: number, cur: any) => acc + cur.viewCount, 0)} />
              <KpiCard title="Phỏng vấn" value={pipeline.interviewCount} />
            </div>
          </section>

          {/* Section 3 */}
          <section className="topcv-section">
            <h2 className="section-title">Quy trình tuyển dụng</h2>
            <p className="section-subtitle">Tổng quan hành trình ứng viên của tất cả tin đang chạy</p>
            <div className="funnel-container">
              <div className="funnel-step">
                <div className="fs-icon mono-icon"><IconClipboard size={22} /></div>
                <div className="fs-title">Ứng tuyển</div>
                <div className="fs-value" style={{ color: '#3b82f6' }}>{pipeline.appliedCount}</div>
                <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px', textAlign: 'center' }}>
                  (Mới nộp: {pipeline.newlyAppliedCount} | Đã xem: {pipeline.reviewedCount})
                </div>
              </div>
              <div className="funnel-arrow">→</div>
              <div className="funnel-step">
                <div className="fs-icon mono-icon"><IconUsers size={22} /></div>
                <div className="fs-title">Phỏng vấn</div>
                <div className="fs-value" style={{ color: '#3b82f6' }}>{pipeline.interviewCount}</div>
                <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <span>(Chờ xếp lịch: {pipeline.shortlistedCount} | Đã xếp lịch: {pipeline.interviewScheduledCount})</span>
                  {pipeline.interviewPendingResponseCount !== undefined && (
                    <span>(Chờ UV phản hồi: {pipeline.interviewPendingResponseCount} | Đã chốt: {pipeline.interviewAcceptedCount} | Đã PV: {pipeline.interviewCompletedCount})</span>
                  )}
                </div>
              </div>
              <div className="funnel-arrow">→</div>
              <div className="funnel-step">
                <div className="fs-icon mono-icon"><IconBriefcase size={22} /></div>
                <div className="fs-title">Offer</div>
                <div className="fs-value" style={{ color: '#3b82f6' }}>{pipeline.offerCount}</div>
                <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <span>(Đã gửi Offer)</span>
                </div>
              </div>
              <div className="funnel-arrow">→</div>
              <div className="funnel-step">
                <div className="fs-icon mono-icon"><IconHandshake size={22} /></div>
                <div className="fs-title">Nhận việc</div>
                <div className="fs-value" style={{ color: '#3b82f6' }}>{pipeline.hiredCount}</div>
                <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px', textAlign: 'center' }}>(Đã nhận việc)</div>
              </div>
            </div>
          </section>

          {/* Section 4 */}
          <section className="topcv-section">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h2 className="section-title" style={{ margin: 0 }}>Tin đang chạy</h2>
              <Link to="/employer/jobs" style={{ fontSize: '0.9rem', color: 'var(--primary)', textDecoration: 'none' }}>Xem tất cả tin ↗</Link>
            </div>
            
            {activeJobs.length > 0 ? (
              <div className="table-responsive">
                <table className="topcv-table">
                  <thead>
                    <tr>
                      <th>Tên tin</th>
                      <th>Trạng thái</th>
                      <th style={{ textAlign: 'center' }}>Lượt xem</th>
                      <th style={{ textAlign: 'center' }}>Ứng viên</th>
                      <th style={{ textAlign: 'center' }}>Còn lại</th>
                      <th style={{ textAlign: 'center' }}>Hành động</th>
                    </tr>
                  </thead>
                  <tbody>
                    {activeJobs.slice(0, 5).map((job: any) => (
                      <tr key={job.id}>
                        <td>
                          <div style={{ fontWeight: 600 }}>{job.title}</div>
                          <div style={{ fontSize: '0.85rem', color: 'var(--outline)' }}>{job.location} | {job.type}</div>
                        </td>
                        <td>
                          <span className={`status-badge ${job.status}`}>{job.status === 'published' ? 'Đang chạy' : job.status}</span>
                        </td>
                        <td style={{ textAlign: 'center' }}>{job.viewCount}</td>
                        <td style={{ textAlign: 'center' }}>{job.applicationCount}</td>
                        <td style={{ textAlign: 'center' }}>{job.daysLeft} ngày</td>
                        <td style={{ textAlign: 'center' }}>
                          <Link to={`/employer/applications?jobId=${job.id}`} className="button outline" style={{ padding: '4px 8px', fontSize: '0.85rem' }}>Xem UV</Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: '32px 0', border: '1px solid var(--border)', borderRadius: 8 }}>
                <p className="muted">Chưa có tin tuyển dụng nào đang mở.</p>
              </div>
            )}
          </section>
        </div>

        {/* Sidebar */}
        <div className="topcv-sidebar">
          
          {/* Việc cần làm */}
          {stats.pendingTasks && stats.pendingTasks.length > 0 && (
            <div className="sidebar-box" style={{ marginBottom: 24 }}>
              <div className="sb-header">
                <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                  <IconAlert size={18} /> Việc cần làm
                </h3>
              </div>
              <div className="sb-content">
                <div className="activity-list">
                  {stats.pendingTasks.slice(0, visibleTasksCount).map((task: any) => (
                    <div className="activity-item" key={task.id} style={{ padding: '12px 0' }}>
                      <div className="act-icon mono-icon" style={{ background: '#f8fafc', color: '#111827' }}>
                        {task.taskType === 'new_application' ? <IconProfile size={16} /> :
                         task.taskType === 'pending_interview' ? <IconCalendar size={16} /> :
                         task.taskType === 'employer_response_needed' ? <IconAlert size={16} /> : <IconBell size={16} />}
                      </div>
                      <div className="act-content">
                        <div className="act-title">
                          <strong style={{ color: 'var(--text)' }}>{task.title}</strong>
                        </div>
                        <div className="act-desc">{task.description}</div>
                        <div style={{ marginTop: 8 }}>
                          <Link to={task.actionUrl} className="button outline" style={{ padding: '4px 8px', fontSize: '0.8rem' }}>Xử lý ngay</Link>
                        </div>
                      </div>
                    </div>
                  ))}
                  {stats.pendingTasks.length > visibleTasksCount && (
                    <div style={{ textAlign: 'center', marginTop: 12 }}>
                      <button 
                        className="button outline" 
                        onClick={() => setVisibleTasksCount(prev => prev + 5)}
                        style={{ width: '100%', fontSize: '0.85rem' }}
                      >
                        Xem thêm ({stats.pendingTasks.length - visibleTasksCount})
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Lịch phỏng vấn */}
          <div className="sidebar-box">
            <div className="sb-header">
              <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                <IconCalendar size={18} /> Lịch phỏng vấn sắp tới
              </h3>
              <Link to="/employer/applications?status=INTERVIEW_SCHEDULED" style={{ fontSize: '0.85rem', color: 'var(--primary)', textDecoration: 'none' }}>Xem lịch ↗</Link>
            </div>
            <div className="sb-content">
              {upcomingInterviews.length > 0 ? (
                <div className="topcv-timeline">
                  {upcomingInterviews.slice(0, 5).map((iv: any) => (
                    <div key={iv.id} className="timeline-item">
                      <div className="timeline-time">{new Date(iv.scheduledAt).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}</div>
                      <div className="timeline-line"></div>
                      <div className="ttl-content">
                        <div className="ttl-title">Phỏng vấn - {iv.candidateName}</div>
                        <div className="ttl-sub">{iv.jobTitle} • {iv.type.toUpperCase()}</div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="muted" style={{ textAlign: 'center', fontSize: '0.9rem', margin: '24px 0' }}>Không có lịch phỏng vấn.</p>
              )}
            </div>
          </div>

          {/* Hoạt động */}
          <div className="sidebar-box" style={{ marginTop: 24 }}>
            <div className="sb-header">
              <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                <IconBell size={18} /> Hoạt động gần đây
              </h3>
              <Link to="/employer/notifications" style={{ fontSize: '0.85rem', color: 'var(--primary)', textDecoration: 'none' }}>Xem tất cả ↗</Link>
            </div>
            <div className="sb-content">
              {recentActivities.length > 0 ? (
                <div className="activity-list">
                  {recentActivities.slice(0, 5).map((act: any) => (
                    <div className="activity-item" key={act.id}>
                      <div className="act-icon mono-icon">
                        {act.type === 'APPLICATION' ? <IconProfile size={16} /> : act.type === 'INTERVIEW' ? <IconCalendar size={16} /> : <IconBell size={16} />}
                      </div>
                      <div className="act-content">
                        <div className="act-title">
                          <strong style={{ color: 'var(--text)' }}>{act.title}</strong>
                        </div>
                        <div className="act-desc">{act.description}</div>
                        <div className="act-time">{getRelativeTime(act.createdAt)}</div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="muted" style={{ textAlign: 'center', fontSize: '0.9rem', margin: '24px 0' }}>Chưa có hoạt động nào.</p>
              )}
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
}
