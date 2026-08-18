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
  const [visibleTasksCount, setVisibleTasksCount] = useState(6);
  
  // Dual-view mode tab state: 'today' or 'analytics'
  const [activeTab, setActiveTab] = useState<'today' | 'analytics'>('today');

  // Analytics Date Filter
  const [dateFilter, setDateFilter] = useState<{ start: string; end: string } | null>(null);
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');

  useEffect(() => {
    employerService.getCompanyProfile().then(setCompany).catch(() => null);
  }, []);

  useEffect(() => {
    setLoading(true);
    employerService.getDashboardStats(dateFilter?.start, dateFilter?.end)
      .then(setStats)
      .catch(err => setError(readError(err)))
      .finally(() => setLoading(false));
  }, [dateFilter]);

  const handleQuickFilter = (days: number) => {
    if (days === 0) {
      const today = new Date().toISOString().split('T')[0];
      setDateFilter({ start: today, end: today });
      setCustomStart(today);
      setCustomEnd(today);
    } else if (days === -1) {
      setDateFilter(null);
      setCustomStart('');
      setCustomEnd('');
    } else {
      const end = new Date();
      const start = new Date();
      start.setDate(end.getDate() - days);
      const endStr = end.toISOString().split('T')[0];
      const startStr = start.toISOString().split('T')[0];
      setDateFilter({ start: startStr, end: endStr });
      setCustomStart(startStr);
      setCustomEnd(endStr);
    }
  };

  const applyCustomFilter = () => {
    if (customStart && customEnd) {
      setDateFilter({ start: customStart, end: customEnd });
    }
  };

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
  const pendingTasks = stats?.pendingTasks || [];

  const urgentCount = (actionSummary.pendingApplicationsCount || 0) + 
                      (actionSummary.todayInterviewsCount || 0) + 
                      (actionSummary.expiringJobsCount || 0);

  return (
    <motion.div className="topcv-dashboard" variants={fadeUp} initial="initial" animate="animate" transition={{ duration: 0.3, ease: EASE_OUT }}>
      {/* Header Bar */}
      <div className="topcv-header" style={{ marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', margin: 0, display: 'flex', alignItems: 'center', gap: 10 }}>
            <IconBriefcase size={24} /> Bảng điều khiển Tuyển dụng
            {company && (
              company.verificationStatus === 'verified' ? (
                <span style={{ fontSize: '0.85rem', padding: '4px 10px', borderRadius: '20px', background: '#dcfce7', color: '#166534', border: '1px solid #bbf7d0', marginLeft: '8px', display: 'inline-flex', alignItems: 'center', gap: '4px', fontWeight: 500 }}>
                  ✅ Đã xác thực
                </span>
              ) : null
            )}
          </h1>
          <p className="muted" style={{ margin: '4px 0 0 0', fontSize: '0.9rem' }}>
            Quản lý quy trình tuyển dụng và công việc hàng ngày của bạn
          </p>
        </div>
        <div style={{ display: 'flex', gap: 12 }}>
          <Link to="/employer/jobs?action=new" className="button primary" style={{ borderRadius: 6 }}>
            + Đăng tin mới
          </Link>
        </div>
      </div>

      {/* Dual-View Mode Segmented Tab Switcher */}
      <div className="dashboard-tab-switcher">
        <button 
          className={`dashboard-tab-btn ${activeTab === 'today' ? 'active' : ''}`}
          onClick={() => setActiveTab('today')}
          title="Xem các công việc & lịch trình cần xử lý hôm nay"
        >
          <span>📋 Công việc hôm nay</span>
          {urgentCount > 0 && (
            <span className="dashboard-tab-badge">{urgentCount}</span>
          )}
        </button>

        <button 
          className={`dashboard-tab-btn ${activeTab === 'analytics' ? 'active' : ''}`}
          onClick={() => setActiveTab('analytics')}
          title="Xem báo cáo tổng quan, hiệu quả tuyển dụng và thống kê (Lọc theo ngày)"
        >
          <span>📊 Thống kê & Tổng quan hệ thống</span>
          {dateFilter && (
            <span style={{ fontSize: '0.75rem', background: '#e2e8f0', color: '#334155', padding: '2px 6px', borderRadius: 4, fontWeight: 500 }}>
              Đang lọc
            </span>
          )}
        </button>
      </div>

      {/* VIEW 1: CẦN XỬ LÝ HÔM NAY (TODAY'S ACTION CENTER) */}
      {activeTab === 'today' && (
        <div className="topcv-layout">
          {/* Main Content */}
          <div className="topcv-main">
            
            {/* Action Cards Grid */}
            <section className="topcv-section">
              <h2 className="section-title">
                Tác vụ hôm nay <span style={{ color: 'var(--outline)', fontSize: '0.85rem', fontWeight: 400 }}>— Nhiệm vụ cần giải quyết ngay trong ngày</span>
              </h2>
              <div className="top-actions-grid">
                <TopActionCard 
                  title="Hồ sơ cần duyệt hôm nay" count={actionSummary.pendingApplicationsCount} 
                  icon={<IconProfile size={22} />} buttonText="Duyệt hồ sơ" buttonLink="/employer/applications?status=SUBMITTED" 
                  colorClass="tac-red" 
                />
                <TopActionCard 
                  title="Lịch phỏng vấn hôm nay" count={actionSummary.todayInterviewsCount} 
                  icon={<IconCalendar size={22} />} buttonText="Xem ca phỏng vấn" buttonLink="/employer/interviews" 
                  colorClass="tac-orange" 
                />
                <TopActionCard 
                  title="Tin sắp hết hạn (24-48h)" count={actionSummary.expiringJobsCount} 
                  icon={<IconAlert size={22} />} buttonText="Gia hạn tin" buttonLink="/employer/jobs" 
                  colorClass="tac-yellow" 
                />
                <TopActionCard 
                  title="Thông báo / Tin nhắn" count={actionSummary.unreadMessagesCount} 
                  icon={<IconMail size={22} />} buttonText="Hộp thư" buttonLink="/employer/notifications" 
                  colorClass="tac-blue" 
                />
              </div>
            </section>

            {/* Detailed Priority Task List */}
            <section className="topcv-section">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <div>
                  <h2 className="section-title" style={{ margin: 0 }}>
                    ⚡ Danh sách công việc ưu tiên ({pendingTasks.length})
                  </h2>
                  <p className="section-subtitle" style={{ margin: '4px 0 0 0' }}>
                    Các công việc phát sinh cần thao tác xử lý sớm
                  </p>
                </div>
                <Link to="/employer/applications" style={{ fontSize: '0.88rem', color: 'var(--primary)', textDecoration: 'none', fontWeight: 500 }}>
                  Xem tất cả ứng viên ↗
                </Link>
              </div>

              {pendingTasks.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  {pendingTasks.slice(0, visibleTasksCount).map((task: any) => {
                    let badgeColor = '#3b82f6';
                    let badgeBg = '#eff6ff';
                    let badgeText = 'Công việc';
                    let btnText = 'Xử lý ngay';

                    if (task.taskType === 'new_application') {
                      badgeColor = '#ef4444';
                      badgeBg = '#fef2f2';
                      badgeText = 'Hồ sơ mới';
                      btnText = 'Duyệt CV';
                    } else if (task.taskType === 'pending_interview') {
                      badgeColor = '#f97316';
                      badgeBg = '#fff7ed';
                      badgeText = 'Xếp lịch PV';
                      btnText = 'Lập lịch PV';
                    } else if (task.taskType === 'employer_response_needed') {
                      badgeColor = '#8b5cf6';
                      badgeBg = '#f5f3ff';
                      badgeText = 'Cần phản hồi';
                      btnText = 'Trả lời UV';
                    }

                    return (
                      <div className="task-action-card" key={task.id}>
                        <div style={{ display: 'flex', gap: 14, alignItems: 'center' }}>
                          <div style={{ 
                            padding: '10px 14px', borderRadius: 8, background: badgeBg, color: badgeColor,
                            fontSize: '0.8rem', fontWeight: 700, whiteSpace: 'nowrap', textTransform: 'uppercase'
                          }}>
                            {badgeText}
                          </div>
                          <div>
                            <div style={{ fontWeight: 600, fontSize: '0.98rem', color: 'var(--text)' }}>
                              {task.title}
                            </div>
                            <div style={{ fontSize: '0.85rem', color: 'var(--outline)', marginTop: 2 }}>
                              {task.description} • <span style={{ fontSize: '0.8rem' }}>{getRelativeTime(task.createdAt)}</span>
                            </div>
                          </div>
                        </div>
                        <div>
                          <Link 
                            to={task.actionUrl} 
                            className="button outline" 
                            style={{ padding: '6px 14px', fontSize: '0.85rem', whiteSpace: 'nowrap', textDecoration: 'none' }}
                          >
                            {btnText} →
                          </Link>
                        </div>
                      </div>
                    );
                  })}

                  {pendingTasks.length > visibleTasksCount && (
                    <div style={{ textAlign: 'center', marginTop: 8 }}>
                      <button 
                        className="button outline" 
                        onClick={() => setVisibleTasksCount(prev => prev + 5)}
                        style={{ fontSize: '0.85rem' }}
                      >
                        Xem thêm công việc ({pendingTasks.length - visibleTasksCount})
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '40px 16px', background: '#f8fafc', borderRadius: 10, border: '1px dashed #cbd5e1' }}>
                  <div style={{ fontSize: '2rem', marginBottom: 8 }}>🎉</div>
                  <h3 style={{ margin: 0, fontSize: '1.05rem', color: '#334155' }}>Không có việc cần xử lý hôm nay!</h3>
                  <p className="muted" style={{ margin: '4px 0 0 0', fontSize: '0.88rem' }}>Tất cả các tác vụ duyệt CV và xếp lịch phỏng vấn đã được hoàn tất.</p>
                </div>
              )}
            </section>
          </div>

          {/* Sidebar */}
          <div className="topcv-sidebar">
            
            {/* Lịch phỏng vấn hôm nay */}
            <div className="sidebar-box">
              <div className="sb-header">
                <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8, fontSize: '1rem' }}>
                  <IconCalendar size={18} /> Phỏng vấn diễn ra hôm nay
                </h3>
                <Link to="/employer/interviews" style={{ fontSize: '0.85rem', color: 'var(--primary)', textDecoration: 'none' }}>Tất cả ↗</Link>
              </div>
              <div className="sb-content">
                {upcomingInterviews.length > 0 ? (
                  <div className="topcv-timeline">
                    {upcomingInterviews.slice(0, 5).map((iv: any) => (
                      <div key={iv.id} className="timeline-item">
                        <div className="timeline-time" style={{ fontWeight: 600, color: 'var(--primary)' }}>
                          {new Date(iv.scheduledAt).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
                        </div>
                        <div className="timeline-line"></div>
                        <div className="ttl-content">
                          <div className="ttl-title" style={{ fontWeight: 600 }}>{iv.candidateName}</div>
                          <div className="ttl-sub">{iv.jobTitle} • <span style={{ textTransform: 'uppercase', fontSize: '0.75rem', fontWeight: 600 }}>{iv.type}</span></div>
                          {iv.meetingLink && (
                            <a 
                              href={iv.meetingLink} 
                              target="_blank" 
                              rel="noreferrer"
                              style={{ display: 'inline-block', marginTop: 4, fontSize: '0.8rem', color: '#0284c7', textDecoration: 'underline', fontWeight: 500 }}
                            >
                              🔗 Vào phòng phỏng vấn
                            </a>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div style={{ textAlign: 'center', padding: '24px 0' }}>
                    <p className="muted" style={{ fontSize: '0.88rem', margin: 0 }}>Hôm nay không có lịch phỏng vấn nào.</p>
                  </div>
                )}
              </div>
            </div>

            {/* Hoạt động gần đây */}
            <div className="sidebar-box">
              <div className="sb-header">
                <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8, fontSize: '1rem' }}>
                  <IconBell size={18} /> Nhật ký hoạt động
                </h3>
                <Link to="/employer/notifications" style={{ fontSize: '0.85rem', color: 'var(--primary)', textDecoration: 'none' }}>Tất cả ↗</Link>
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
                  <p className="muted" style={{ textAlign: 'center', fontSize: '0.88rem', margin: '20px 0' }}>Chưa có hoạt động nào.</p>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* VIEW 2: TỔNG QUAN & THỐNG KÊ HỆ THỐNG (SYSTEM ANALYTICS & OVERVIEW) */}
      {activeTab === 'analytics' && (
        <div>
          {/* Date Range Filter Bar */}
          <div style={{ background: '#fff', padding: '16px 24px', borderRadius: '12px', border: '1px solid var(--border-color)', marginBottom: '24px', display: 'flex', flexWrap: 'wrap', gap: '16px', alignItems: 'flex-end', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', alignItems: 'flex-end' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 500, marginBottom: '6px', color: 'var(--text-secondary)' }}>Từ ngày</label>
                <input type="date" value={customStart} onChange={e => setCustomStart(e.target.value)} style={{ padding: '8px 12px', border: '1px solid var(--border-color)', borderRadius: '6px', outline: 'none' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 500, marginBottom: '6px', color: 'var(--text-secondary)' }}>Đến ngày</label>
                <input type="date" value={customEnd} onChange={e => setCustomEnd(e.target.value)} style={{ padding: '8px 12px', border: '1px solid var(--border-color)', borderRadius: '6px', outline: 'none' }} />
              </div>
              <button onClick={applyCustomFilter} style={{ padding: '8px 16px', background: 'var(--primary)', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 500, height: '37.5px' }}>Lọc dữ liệu</button>
            </div>
            
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', height: '37.5px' }}>
              <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginRight: '8px', fontWeight: 500 }}>Bộ lọc nhanh:</span>
              <button onClick={() => handleQuickFilter(0)} style={{ padding: '6px 12px', background: dateFilter?.start === customStart && customStart === new Date().toISOString().split('T')[0] ? '#e2e8f0' : '#f8fafc', border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer', fontSize: '0.85rem', fontWeight: 500 }}>Hôm nay</button>
              <button onClick={() => handleQuickFilter(7)} style={{ padding: '6px 12px', background: '#f8fafc', border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer', fontSize: '0.85rem' }}>7 ngày</button>
              <button onClick={() => handleQuickFilter(30)} style={{ padding: '6px 12px', background: '#f8fafc', border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer', fontSize: '0.85rem' }}>30 ngày</button>
              <button onClick={() => handleQuickFilter(-1)} style={{ padding: '6px 12px', background: '#f8fafc', border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Tất cả</button>
            </div>
          </div>

          <div className="topcv-layout">
            {/* Main Content */}
            <div className="topcv-main">
              
              {/* Macro KPI Cards */}
              <section className="topcv-section">
                <h2 className="section-title">Tổng quan chỉ số hiệu suất tuyển dụng</h2>
                <div className="kpi-grid">
                  <KpiCard title="Tin đang chạy" value={stats.activeJobs} subtext="Tin đang công khai" />
                  <KpiCard title="Tổng số ứng viên" value={stats.totalApplications} subtext="Số lượt nộp hồ sơ" />
                  <KpiCard title="Lượt xem tin" value={activeJobs.reduce((acc: number, cur: any) => acc + cur.viewCount, 0)} subtext="Tương tác ứng viên" />
                  <KpiCard title="Lượt phỏng vấn" value={pipeline.interviewCount} subtext="Đã/Đang xếp lịch" />
                </div>
              </section>

              {/* Recruitment Pipeline Funnel */}
              <section className="topcv-section">
                <h2 className="section-title">Phễu chuyển đổi tuyển dụng (Recruitment Pipeline)</h2>
                <p className="section-subtitle">Chi tiết tỷ lệ ứng viên qua các vòng trong khoảng thời gian đã chọn</p>
                <div className="funnel-container">
                  <div className="funnel-step">
                    <div className="fs-icon mono-icon"><IconClipboard size={22} /></div>
                    <div className="fs-title">1. Ứng tuyển</div>
                    <div className="fs-value" style={{ color: '#3b82f6' }}>{pipeline.appliedCount}</div>
                    <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px', textAlign: 'center' }}>
                      (Mới nộp: {pipeline.newlyAppliedCount} | Đã xem: {pipeline.reviewedCount})
                    </div>
                  </div>
                  <div className="funnel-arrow">→</div>
                  <div className="funnel-step">
                    <div className="fs-icon mono-icon"><IconUsers size={22} /></div>
                    <div className="fs-title">2. Phỏng vấn</div>
                    <div className="fs-value" style={{ color: '#3b82f6' }}>{pipeline.interviewCount}</div>
                    <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <span>(Chờ xếp lịch: {pipeline.shortlistedCount} | Đã xếp lịch: {pipeline.interviewScheduledCount})</span>
                      {pipeline.interviewPendingResponseCount !== undefined && (
                        <span>(UV chốt: {pipeline.interviewAcceptedCount} | Đã PV: {pipeline.interviewCompletedCount})</span>
                      )}
                    </div>
                  </div>
                  <div className="funnel-arrow">→</div>
                  <div className="funnel-step">
                    <div className="fs-icon mono-icon"><IconBriefcase size={22} /></div>
                    <div className="fs-title">3. Offer</div>
                    <div className="fs-value" style={{ color: '#3b82f6' }}>{pipeline.offerCount}</div>
                    <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px', textAlign: 'center' }}>
                      (Đã phát hành Offer)
                    </div>
                  </div>
                  <div className="funnel-arrow">→</div>
                  <div className="funnel-step">
                    <div className="fs-icon mono-icon"><IconHandshake size={22} /></div>
                    <div className="fs-title">4. Nhận việc</div>
                    <div className="fs-value" style={{ color: '#16a34a' }}>{pipeline.hiredCount}</div>
                    <div style={{ fontSize: '0.75rem', color: '#16a34a', marginTop: '4px', textAlign: 'center', fontWeight: 600 }}>
                      (Tuyển dụng thành công)
                    </div>
                  </div>
                </div>
              </section>

              {/* Active Jobs Performance Table */}
              <section className="topcv-section">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                  <h2 className="section-title" style={{ margin: 0 }}>Hiệu suất Tin tuyển dụng đang chạy</h2>
                  <Link to="/employer/jobs" style={{ fontSize: '0.9rem', color: 'var(--primary)', textDecoration: 'none', fontWeight: 500 }}>
                    Quản lý tất cả tin ↗
                  </Link>
                </div>
                
                {activeJobs.length > 0 ? (
                  <div className="table-responsive">
                    <table className="topcv-table">
                      <thead>
                        <tr>
                          <th>Tên tin tuyển dụng</th>
                          <th>Trạng thái</th>
                          <th style={{ textAlign: 'center' }}>Lượt xem</th>
                          <th style={{ textAlign: 'center' }}>Hồ sơ nộp</th>
                          <th style={{ textAlign: 'center' }}>Thời hạn còn</th>
                          <th style={{ textAlign: 'center' }}>Thao tác</th>
                        </tr>
                      </thead>
                      <tbody>
                        {activeJobs.slice(0, 10).map((job: any) => (
                          <tr key={job.id}>
                            <td>
                              <div style={{ fontWeight: 600, color: 'var(--text)' }}>{job.title}</div>
                              <div style={{ fontSize: '0.85rem', color: 'var(--outline)' }}>{job.location} | {job.type}</div>
                            </td>
                            <td>
                              <span className={`status-badge ${job.status}`} style={{ textTransform: 'capitalize' }}>
                                {job.status === 'published' ? 'Đang mở' : job.status}
                              </span>
                            </td>
                            <td style={{ textAlign: 'center', fontWeight: 600 }}>{job.viewCount}</td>
                            <td style={{ textAlign: 'center', fontWeight: 600, color: 'var(--primary)' }}>{job.applicationCount}</td>
                            <td style={{ textAlign: 'center' }}>{job.daysLeft} ngày</td>
                            <td style={{ textAlign: 'center' }}>
                              <Link to={`/employer/applications?jobId=${job.id}`} className="button outline" style={{ padding: '4px 10px', fontSize: '0.85rem', textDecoration: 'none' }}>
                                Xem danh sách UV
                              </Link>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div style={{ textAlign: 'center', padding: '32px 0', border: '1px solid var(--border)', borderRadius: 8 }}>
                    <p className="muted">Chưa có tin tuyển dụng nào đang chạy.</p>
                  </div>
                )}
              </section>
            </div>

            {/* Analytics Sidebar */}
            <div className="topcv-sidebar">
              <div className="sidebar-box">
                <div className="sb-header">
                  <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8, fontSize: '1rem' }}>
                    <IconBell size={18} /> Nhật ký hệ thống
                  </h3>
                </div>
                <div className="sb-content">
                  {recentActivities.length > 0 ? (
                    <div className="activity-list">
                      {recentActivities.slice(0, 6).map((act: any) => (
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
                    <p className="muted" style={{ textAlign: 'center', fontSize: '0.88rem', margin: '20px 0' }}>Chưa có hoạt động nào.</p>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </motion.div>
  );
}

