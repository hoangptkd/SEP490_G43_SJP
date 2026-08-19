import React, { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { employerService, EmployerInterview } from '../../services/employerService';

// ─── Status config ────────────────────────────────────────────────────────────
const IV_STATUS: Record<string, { label: string; color: string; bg: string; border: string }> = {
  SCHEDULED:            { label: 'Chờ xác nhận',          color: '#b45309', bg: '#fffbeb', border: '#fde68a' },
  PENDING_RESPONSE:     { label: 'Chờ xác nhận',          color: '#b45309', bg: '#fffbeb', border: '#fde68a' },
  ACCEPTED:             { label: 'Đã xác nhận',           color: '#15803d', bg: '#f0fdf4', border: '#bbf7d0' },
  DECLINED:             { label: 'UV từ chối',            color: '#b91c1c', bg: '#fef2f2', border: '#fecaca' },
  RESCHEDULE_REQUESTED: { label: 'UV xin đổi lịch',       color: '#c2410c', bg: '#fff7ed', border: '#fed7aa' },
  NO_RESPONSE:          { label: 'Không phản hồi',        color: '#b91c1c', bg: '#fef2f2', border: '#fecaca' },
  COMPLETED:            { label: 'Đã phỏng vấn xong',     color: '#4338ca', bg: '#eef2ff', border: '#c7d2fe' },
  NO_SHOW:              { label: 'UV không đến',          color: '#374151', bg: '#f3f4f6', border: '#e5e7eb' },
  CANCELLED:            { label: 'Đã hủy',                color: '#6b7280', bg: '#f9fafb', border: '#e5e7eb' },
};

function getStatusCfg(status: string) {
  return IV_STATUS[status] ?? { label: status, color: '#475569', bg: '#f8fafc', border: '#e2e8f0' };
}

function formatDateHeader(dateStr: string): string {
  const d = new Date(dateStr);
  const today = new Date();
  const tomorrow = new Date(today);
  tomorrow.setDate(today.getDate() + 1);
  if (d.toDateString() === today.toDateString())
    return 'Hôm nay — ' + d.toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  if (d.toDateString() === tomorrow.toDateString())
    return 'Ngày mai — ' + d.toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  return d.toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}

function isPast(scheduledAt: string): boolean {
  return new Date(scheduledAt) < new Date();
}

function groupByDate(interviews: EmployerInterview[]): Map<string, EmployerInterview[]> {
  const map = new Map<string, EmployerInterview[]>();
  for (const iv of interviews) {
    const key = new Date(iv.scheduledAt).toLocaleDateString('sv-SE');
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(iv);
  }
  return map;
}

type RangeFilter = 'all' | 'today' | 'week' | 'pending';

// --- Icons (Minimalist SVG) ---
const CalendarIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line>
  </svg>
);
const UserIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle>
  </svg>
);
const MapPinIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle>
  </svg>
);
const LinkIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
    <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
  </svg>
);
const ClockIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>
  </svg>
);
const AlertCircleIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line>
  </svg>
);

export default function EmployerInterviewsPage() {
  const navigate = useNavigate();
  const [interviews, setInterviews] = useState<EmployerInterview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [range, setRange] = useState<RangeFilter>('all');
  const [toastMsg, setToastMsg] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setToastMsg({ text, type });
    setTimeout(() => setToastMsg(null), 4000);
  };

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const apiRange = range === 'pending' ? 'all' : range;
      const data = await employerService.getInterviews(apiRange as 'today' | 'week' | 'all');
      setInterviews(data);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Không thể tải lịch phỏng vấn.');
    } finally {
      setLoading(false);
    }
  }, [range]);

  useEffect(() => { void load(); }, [load]);

  const filteredInterviews = range === 'pending'
    ? interviews.filter(iv => ['SCHEDULED', 'PENDING_RESPONSE', 'RESCHEDULE_REQUESTED', 'NO_RESPONSE'].includes(iv.status))
    : interviews;

  const groupedByDate = groupByDate(filteredInterviews);
  const sortedDates = Array.from(groupedByDate.keys()).sort();

  const todayKey = new Date().toLocaleDateString('sv-SE');
  const endOfWeekKey = (() => { const d = new Date(); d.setDate(d.getDate() + 7); return d.toLocaleDateString('sv-SE'); })();
  const todayCount = interviews.filter(iv => new Date(iv.scheduledAt).toLocaleDateString('sv-SE') === todayKey).length;
  const weekCount = interviews.filter(iv => {
    const d = new Date(iv.scheduledAt).toLocaleDateString('sv-SE');
    return d >= todayKey && d <= endOfWeekKey;
  }).length;
  const pendingCount = interviews.filter(iv => ['SCHEDULED', 'PENDING_RESPONSE', 'RESCHEDULE_REQUESTED', 'NO_RESPONSE'].includes(iv.status)).length;
  const rescheduleCount = interviews.filter(iv => iv.status === 'RESCHEDULE_REQUESTED').length;

  const rangeLabel: Record<RangeFilter, string> = {
    all: 'Tất cả',
    today: 'Hôm nay',
    week: 'Tuần này',
    pending: 'Cần xử lý',
  };

  return (
    <section style={{ padding: '32px', maxWidth: '1200px', margin: '0 auto', fontFamily: 'Inter, system-ui, sans-serif' }}>
      {/* Header */}
      <div style={{ marginBottom: '32px' }}>
        <div style={{ marginBottom: '12px' }}>
          <Link to="/employer" style={{ color: '#64748b', textDecoration: 'none', fontSize: '0.9rem', fontWeight: 500, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <span>&larr;</span> Quay lại Dashboard
          </Link>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <h1 style={{ margin: '0 0 8px', fontSize: '1.875rem', fontWeight: 600, color: '#0f172a', letterSpacing: '-0.025em' }}>
              Quản lý lịch phỏng vấn
            </h1>
            <p style={{ margin: 0, color: '#64748b', fontSize: '0.95rem' }}>
              Theo dõi và sắp xếp các lịch hẹn phỏng vấn với ứng viên.
            </p>
          </div>
          <button
            onClick={() => navigate('/employer/applications')}
            style={{
              background: '#0f172a',
              color: '#fff', border: 'none', padding: '10px 20px',
              borderRadius: '6px', fontWeight: 500, fontSize: '0.9rem',
              cursor: 'pointer', transition: 'background 0.2s',
            }}
            onMouseOver={(e) => e.currentTarget.style.background = '#1e293b'}
            onMouseOut={(e) => e.currentTarget.style.background = '#0f172a'}
          >
            Tạo lịch mới
          </button>
        </div>
      </div>

      {/* Stats bar */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: '16px', marginBottom: '32px' }}>
        {[
          { label: 'Phỏng vấn hôm nay', value: todayCount, icon: <ClockIcon />, color: '#0284c7', bg: '#f0f9ff', border: '#e0f2fe' },
          { label: 'Trong tuần này', value: weekCount, icon: <CalendarIcon />, color: '#4f46e5', bg: '#eef2ff', border: '#e0e7ff' },
          { label: 'Cần xác nhận', value: pendingCount, icon: <AlertCircleIcon />, color: '#d97706', bg: '#fffbeb', border: '#fef3c7' },
          { label: 'Yêu cầu đổi lịch', value: rescheduleCount, icon: <CalendarIcon />, color: '#ea580c', bg: '#fff7ed', border: '#ffedd5' },
        ].map(stat => (
          <div key={stat.label} style={{
            background: '#fff', borderRadius: '8px', padding: '20px',
            border: `1px solid #e2e8f0`,
            display: 'flex', flexDirection: 'column', gap: '12px',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span style={{ fontSize: '0.875rem', color: '#64748b', fontWeight: 500 }}>{stat.label}</span>
              <div style={{ color: stat.color, background: stat.bg, padding: '8px', borderRadius: '6px' }}>
                {stat.icon}
              </div>
            </div>
            <div style={{ fontSize: '1.875rem', fontWeight: 600, color: '#0f172a', lineHeight: 1 }}>{stat.value}</div>
          </div>
        ))}
      </div>

      {/* Filter tabs */}
      <div style={{
        display: 'flex', gap: '8px', marginBottom: '24px',
        borderBottom: '1px solid #e2e8f0', paddingBottom: '16px',
        flexWrap: 'wrap',
      }}>
        {(['all', 'today', 'week', 'pending'] as RangeFilter[]).map(r => (
          <button
            key={r}
            onClick={() => setRange(r)}
            style={{
              padding: '8px 16px', borderRadius: '6px', border: '1px solid',
              borderColor: range === r ? '#e2e8f0' : 'transparent',
              background: range === r ? '#f8fafc' : 'transparent',
              color: range === r ? '#0f172a' : '#64748b',
              fontWeight: 500,
              fontSize: '0.875rem', cursor: 'pointer',
              transition: 'all 0.15s',
              display: 'flex', alignItems: 'center', gap: '6px'
            }}
            onMouseOver={(e) => { if(range !== r) e.currentTarget.style.color = '#0f172a'; }}
            onMouseOut={(e) => { if(range !== r) e.currentTarget.style.color = '#64748b'; }}
          >
            {rangeLabel[r]}
            {r === 'pending' && pendingCount > 0 && (
              <span style={{ background: '#ef4444', color: '#fff', borderRadius: '4px', padding: '2px 6px', fontSize: '0.75rem', fontWeight: 600 }}>
                {pendingCount}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Reschedule alert */}
      {rescheduleCount > 0 && (
        <div style={{
          background: '#fff', border: '1px solid #fed7aa', borderLeft: '4px solid #ea580c', borderRadius: '6px',
          padding: '16px 20px', marginBottom: '24px',
          display: 'flex', alignItems: 'center', gap: '16px',
        }}>
          <div style={{ color: '#ea580c' }}><AlertCircleIcon /></div>
          <div style={{ flex: 1 }}>
            <div style={{ color: '#9a3412', fontWeight: 500, fontSize: '0.95rem' }}>
              {rescheduleCount} ứng viên yêu cầu đổi lịch phỏng vấn
            </div>
            <div style={{ margin: '4px 0 0', fontSize: '0.875rem', color: '#c2410c' }}>
              Vui lòng xem chi tiết và xác nhận để cập nhật lịch mới.
            </div>
          </div>
          <button
            onClick={() => setRange('pending')}
            style={{
              background: '#fff', color: '#ea580c', border: '1px solid #fed7aa',
              padding: '8px 16px', borderRadius: '6px', fontWeight: 500,
              fontSize: '0.875rem', cursor: 'pointer', whiteSpace: 'nowrap',
              transition: 'background 0.2s'
            }}
            onMouseOver={(e) => e.currentTarget.style.background = '#fff7ed'}
            onMouseOut={(e) => e.currentTarget.style.background = '#fff'}
          >
            Xem danh sách
          </button>
        </div>
      )}

      {/* Content */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '80px 0', color: '#64748b' }}>
          <p style={{ margin: 0, fontWeight: 500, fontSize: '1rem' }}>Đang tải dữ liệu...</p>
        </div>
      ) : error ? (
        <div style={{ background: '#fef2f2', color: '#991b1b', padding: '16px', borderRadius: '6px', border: '1px solid #fecaca' }}>
          {error}
        </div>
      ) : filteredInterviews.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '80px 20px', background: '#fff', borderRadius: '8px', border: '1px dashed #cbd5e1' }}>
          <p style={{ fontSize: '1.125rem', color: '#334155', margin: '0 0 8px', fontWeight: 500 }}>
            {range === 'today' ? 'Không có lịch phỏng vấn nào trong hôm nay.'
             : range === 'week' ? 'Không có lịch phỏng vấn nào trong tuần này.'
             : range === 'pending' ? 'Tất cả các lịch hẹn đã được xử lý.'
             : 'Hiện chưa có lịch phỏng vấn nào được thiết lập.'}
          </p>
          <p style={{ fontSize: '0.95rem', color: '#64748b', margin: 0 }}>
            Truy cập <Link to="/employer/applications" style={{ color: '#2563eb', textDecoration: 'none' }}>Quản lý ứng viên</Link> để xếp lịch.
          </p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
          {sortedDates.map(dateKey => {
            const dayInterviews = groupedByDate.get(dateKey)!;
            return (
              <div key={dateKey}>
                {/* Date group header */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '16px' }}>
                  <span style={{
                    fontWeight: 600, fontSize: '1rem',
                    color: dateKey === todayKey ? '#0f172a' : '#475569',
                  }}>
                    {formatDateHeader(dayInterviews[0].scheduledAt)}
                  </span>
                  <span style={{ fontSize: '0.875rem', color: '#64748b', fontWeight: 500, background: '#f1f5f9', padding: '2px 8px', borderRadius: '12px' }}>
                    {dayInterviews.length} buổi
                  </span>
                  <div style={{ flex: 1, height: '1px', background: '#e2e8f0' }} />
                </div>

                {/* Cards */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  {dayInterviews.map(iv => {
                    const st = getStatusCfg(iv.status);
                    const past = isPast(iv.scheduledAt) && !['COMPLETED', 'NO_SHOW', 'CANCELLED'].includes(iv.status);
                    const isOnline = !!(iv.location?.toLowerCase().includes('online') || iv.meetingLink);

                    return (
                      <div
                        key={iv.id}
                        style={{
                          background: '#fff', borderRadius: '8px',
                          border: `1px solid ${iv.status === 'RESCHEDULE_REQUESTED' ? '#fed7aa' : '#e2e8f0'}`,
                          borderLeft: `4px solid ${
                            iv.status === 'ACCEPTED' ? '#10b981'
                            : iv.status === 'RESCHEDULE_REQUESTED' ? '#f97316'
                            : iv.status === 'COMPLETED' ? '#6366f1'
                            : ['DECLINED','NO_RESPONSE','NO_SHOW'].includes(iv.status) ? '#ef4444'
                            : '#3b82f6'
                          }`,
                          opacity: ['CANCELLED', 'DECLINED'].includes(iv.status) ? 0.6 : 1,
                        }}
                      >
                        <div style={{ padding: '20px' }}>
                          <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap', alignItems: 'flex-start' }}>
                            {/* Time block */}
                            <div style={{
                              minWidth: '80px',
                            }}>
                              <div style={{ fontSize: '1.25rem', fontWeight: 600, color: past ? '#ef4444' : '#0f172a', lineHeight: 1.2 }}>
                                {new Date(iv.scheduledAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                              </div>
                              <div style={{ fontSize: '0.75rem', color: past ? '#ef4444' : '#64748b', marginTop: '4px', fontWeight: 500 }}>
                                {past ? 'Đã qua' : 'Giờ PV'}
                              </div>
                            </div>

                            {/* Info block */}
                            <div style={{ flex: '1 1 300px', minWidth: 0 }}>
                              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px', marginBottom: '8px' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                  <div style={{ fontWeight: 600, fontSize: '1.125rem', color: '#0f172a' }}>{iv.candidateName}</div>
                                  <span style={{
                                    background: st.bg, color: st.color,
                                    border: `1px solid ${st.border}`,
                                    padding: '2px 10px', borderRadius: '4px',
                                    fontSize: '0.75rem', fontWeight: 500,
                                  }}>
                                    {st.label}
                                  </span>
                                </div>
                              </div>

                              <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', marginBottom: '12px', color: '#475569', fontSize: '0.875rem' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                  <span style={{ color: '#94a3b8' }}><UserIcon /></span>
                                  {iv.candidatePhone || '---'} {iv.candidateEmail ? ` • ${iv.candidateEmail}` : ''}
                                </div>
                              </div>

                              <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginBottom: '12px' }}>
                                <div style={{ color: '#0f172a', fontSize: '0.875rem', fontWeight: 500, display: 'flex', alignItems: 'center', gap: '6px' }}>
                                  <span>Vị trí:</span> {iv.jobTitle}
                                </div>
                                <div style={{
                                  color: isOnline ? '#059669' : '#475569',
                                  fontSize: '0.875rem', fontWeight: 500,
                                  display: 'flex', alignItems: 'center', gap: '6px'
                                }}>
                                  {isOnline ? 'Trực tuyến' : 'Trực tiếp'}
                                </div>
                              </div>

                              {iv.location && (
                                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '6px', fontSize: '0.875rem', color: '#475569', marginBottom: '12px' }}>
                                  <span style={{ color: '#94a3b8', marginTop: '2px' }}><MapPinIcon /></span>
                                  <span style={{ flex: 1 }}>{iv.location}</span>
                                </div>
                              )}

                              {/* Viewed status */}
                              <div style={{ fontSize: '0.8125rem', color: iv.viewedAt ? '#64748b' : '#d97706' }}>
                                {iv.viewedAt
                                  ? `Ứng viên đã xem thông báo lúc ${new Date(iv.viewedAt).toLocaleString('vi-VN')}`
                                  : 'Ứng viên chưa xem thông báo'}
                              </div>

                              {/* Reschedule note */}
                              {iv.status === 'RESCHEDULE_REQUESTED' && iv.candidateRescheduleNote && (
                                <div style={{
                                  marginTop: '12px', padding: '12px',
                                  background: '#fff7ed', border: '1px solid #fed7aa',
                                  borderRadius: '6px', fontSize: '0.875rem', color: '#9a3412',
                                }}>
                                  <strong style={{ fontWeight: 600 }}>Lý do đổi lịch:</strong> {iv.candidateRescheduleNote}
                                </div>
                              )}
                            </div>

                            {/* Action column */}
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', minWidth: '160px', flexShrink: 0 }}>
                              {iv.meetingLink && iv.status === 'ACCEPTED' && (
                                <a
                                  href={iv.meetingLink}
                                  target="_blank"
                                  rel="noreferrer"
                                  style={{
                                    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                                    background: '#10b981', color: '#fff', border: '1px solid #059669',
                                    padding: '8px 16px', borderRadius: '6px',
                                    fontWeight: 500, fontSize: '0.875rem', textDecoration: 'none',
                                    transition: 'background 0.2s'
                                  }}
                                  onMouseOver={(e) => e.currentTarget.style.background = '#059669'}
                                  onMouseOut={(e) => e.currentTarget.style.background = '#10b981'}
                                >
                                  <LinkIcon /> Tham gia họp
                                </a>
                              )}

                              <button
                                onClick={() => navigate(`/employer/applications?appId=${iv.applicationId}`)}
                                style={{
                                  background: '#fff', color: '#0f172a',
                                  border: '1px solid #e2e8f0',
                                  padding: '8px 16px', borderRadius: '6px',
                                  fontWeight: 500, fontSize: '0.875rem',
                                  cursor: 'pointer', transition: 'background 0.2s',
                                  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px'
                                }}
                                onMouseOver={(e) => e.currentTarget.style.background = '#f8fafc'}
                                onMouseOut={(e) => e.currentTarget.style.background = '#fff'}
                              >
                                Xem hồ sơ chi tiết
                              </button>

                              {iv.meetingLink && iv.status !== 'ACCEPTED' && (
                                <a
                                  href={iv.meetingLink}
                                  target="_blank"
                                  rel="noreferrer"
                                  style={{
                                    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                                    background: '#f8fafc', color: '#475569',
                                    border: '1px solid #e2e8f0',
                                    padding: '8px 16px', borderRadius: '6px',
                                    fontWeight: 500, fontSize: '0.875rem', textDecoration: 'none',
                                    transition: 'background 0.2s'
                                  }}
                                  onMouseOver={(e) => e.currentTarget.style.background = '#f1f5f9'}
                                  onMouseOut={(e) => e.currentTarget.style.background = '#f8fafc'}
                                >
                                  <LinkIcon /> Link cuộc họp
                                </a>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Toast */}
      {toastMsg && (
        <div style={{
          position: 'fixed', bottom: '24px', right: '24px', zIndex: 9999,
          background: toastMsg.type === 'success' ? '#15803d' : '#b91c1c',
          color: '#fff', padding: '12px 24px', borderRadius: '6px',
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)', 
          fontWeight: 500, fontSize: '0.875rem',
        }}>
          {toastMsg.text}
        </div>
      )}
    </section>
  );
}
