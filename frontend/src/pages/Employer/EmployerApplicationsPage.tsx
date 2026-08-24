import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { employerService } from '../../services/employerService';
import { billingService, UserSubscription } from '../../services/billingService';
import { customAlert, customConfirm, customPrompt } from '../../utils/dialog';
import type { CandidateApplication } from '../../types/candidateDomain';
import type { Job, Company } from '../../types/job';
import VietnamAddressPicker from '../../components/location/VietnamAddressPicker';
import SearchableCombobox from '../../components/location/SearchableCombobox';
import { IconLock } from '../../components/icons/PortalNavIcons';
import { FiDownload } from '../../components/Icons';
import { buildExcelXml, downloadExcelFile, sanitizeFileName } from '../../utils/exportCsv';

const statusConfig: Record<string, { label: string; color: string; bg: string }> = {
  SUBMITTED: { label: 'Mới nộp', color: '#1d4ed8', bg: '#dbeafe' },
  UNDER_REVIEW: { label: 'Đã xem', color: '#4338ca', bg: '#e0e7ff' },
  SHORTLISTED: { label: 'Đã rút gọn', color: '#6d28d9', bg: '#ede9fe' },
  INTERVIEW_SCHEDULED: { label: 'Đang chờ xử lý', color: '#b45309', bg: '#fef3c7' },
  ACCEPTED: { label: 'Trúng tuyển', color: '#047857', bg: '#d1fae5' },
  REJECTED: { label: 'Từ chối', color: '#b91c1c', bg: '#fee2e2' },
  WITHDRAWN: { label: 'Ứng viên rút', color: '#475569', bg: '#f1f5f9' },
};

const STATUS_FILTER_TABS = [
  { key: 'SUBMITTED', label: 'Mới nộp' },
  { key: 'UNDER_REVIEW', label: 'Đã xem' },
  { key: 'SHORTLISTED', label: 'Đã rút gọn' },
  { key: 'INTERVIEW_SCHEDULED', label: 'Đang chờ xử lý' },
  { key: 'ACCEPTED', label: 'Trúng tuyển' },
  { key: 'REJECTED', label: 'Từ chối' },
] as const;

const STATUS_FILTER_KEYS = new Set<string>(STATUS_FILTER_TABS.map((tab) => tab.key));

function parseStatusQuery(raw: string): string[] {
  return raw
    .split(',')
    .map((part) => {
      const uppercase = part.trim().toUpperCase();
      return uppercase === 'APPLIED' ? 'SUBMITTED' : uppercase;
    })
    .filter((part) => STATUS_FILTER_KEYS.has(part));
}

const STATUS_SORT_ORDER = ['SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW_SCHEDULED', 'ACCEPTED', 'HIRED', 'REJECTED', 'WITHDRAWN'];

function sortApplicationsByStatus(items: CandidateApplication[]): CandidateApplication[] {
  return [...items].sort((a, b) => {
    const orderA = STATUS_SORT_ORDER.indexOf(a.status);
    const orderB = STATUS_SORT_ORDER.indexOf(b.status);
    const rankA = orderA === -1 ? 99 : orderA;
    const rankB = orderB === -1 ? 99 : orderB;
    if (rankA !== rankB) return rankA - rankB;

    const scoreA = a.aiMatchScore ?? a.matchScore ?? -999;
    const scoreB = b.aiMatchScore ?? b.matchScore ?? -999;
    if (scoreA !== scoreB) {
      return scoreB - scoreA;
    }

    return new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime();
  });
}

function RecruitmentStageStepper({ app }: { app: CandidateApplication | any }) {
  const status = app?.status || 'SUBMITTED';
  const isRejected = status === 'REJECTED';
  const isWithdrawn = status === 'WITHDRAWN';

  const interviews: any[] = app?.interviews || [];
  const hasCompletedInterview = interviews.some((iv) => iv.status === 'COMPLETED');
  const hasActiveInterview = interviews.some((iv) => ['SCHEDULED', 'PENDING_RESPONSE', 'ACCEPTED', 'RESCHEDULE_REQUESTED'].includes(iv.status));
  const hasOffer = !!app?.jobOffer || status === 'ACCEPTED' || status === 'HIRED';

  let subCaseText = '';
  let currentStageIndex = 0; // 0: Nộp

  if (hasOffer) {
    currentStageIndex = 4; // 4: Gửi Offer
    if (app?.jobOffer) {
      if (app.jobOffer.status === 'accepted') subCaseText = '🎉 Ứng viên đã chấp nhận Offer';
      else if (app.jobOffer.status === 'rejected') subCaseText = '⚠️ Ứng viên từ chối Offer (Có thương lượng)';
      else subCaseText = '✉️ Đã gửi Thư mời nhận việc (Job Offer)';
    } else {
      subCaseText = '✅ Đã trúng tuyển / Nhận việc';
    }
  } else if (hasCompletedInterview) {
    currentStageIndex = 3; // 3: Sau phỏng vấn (Đánh giá PV)
    subCaseText = '🎤 Đã phỏng vấn xong - Đang đánh giá kết quả';
  } else if (status === 'INTERVIEW_SCHEDULED' || hasActiveInterview) {
    currentStageIndex = 2; // 2: Lên lịch PV
    const activeIv = interviews.find((iv) => ['SCHEDULED', 'PENDING_RESPONSE', 'ACCEPTED', 'RESCHEDULE_REQUESTED'].includes(iv.status));
    if (activeIv) {
      if (activeIv.status === 'ACCEPTED') subCaseText = '✅ Ứng viên đã xác nhận tham gia PV';
      else if (activeIv.status === 'RESCHEDULE_REQUESTED') subCaseText = '🔄 Ứng viên gửi yêu cầu đổi lịch PV';
      else if (activeIv.status === 'PENDING_RESPONSE') subCaseText = '⏳ Đã gửi lịch PV - Chờ ứng viên phản hồi';
      else subCaseText = '📅 Đã lên lịch phỏng vấn';
    } else {
      subCaseText = '📅 Đang trong giai đoạn phỏng vấn';
    }
  } else if (status === 'UNDER_REVIEW' || status === 'SHORTLISTED') {
    currentStageIndex = 1; // 1: Xem
    subCaseText = '👁️ Nhà tuyển dụng đã xem hồ sơ';
  } else {
    currentStageIndex = 0; // 0: Nộp
    subCaseText = '📥 Hồ sơ mới nộp';
  }

  if (isRejected || isWithdrawn) {
    currentStageIndex = -1;
  }

  const stages = [
    { key: 0, label: 'Nộp' },
    { key: 1, label: 'Xem' },
    { key: 2, label: 'Lên lịch PV' },
    { key: 3, label: 'Sau phỏng vấn' },
    { key: 4, label: 'Gửi Offer' },
  ];

  return (
    <div style={{
      width: '100%',
      padding: '12px 16px 10px 16px',
      background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
      borderRadius: '8px',
      marginBottom: '8px',
      border: '1px solid #e2e8f0',
      boxSizing: 'border-box'
    }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
        {/* Background Connecting Line */}
        <div style={{
          position: 'absolute',
          top: '14px',
          left: '25px',
          right: '25px',
          height: '3px',
          background: '#cbd5e1',
          zIndex: 0
        }} />
        
        {/* Active Filled Progress Line (Red Highlight) */}
        {currentStageIndex >= 0 && (
          <div style={{
            position: 'absolute',
            top: '14px',
            left: '25px',
            width: `calc(${currentStageIndex} * ((100% - 50px) / 4))`,
            height: '3px',
            background: 'linear-gradient(90deg, #ef4444, #dc2626)',
            transition: 'width 0.4s ease',
            zIndex: 1
          }} />
        )}

        {stages.map((stage, idx) => {
          const isPassed = currentStageIndex >= idx;
          const isCurrent = currentStageIndex === idx;

          let stepBg = '#ffffff';
          let stepBorder = '#cbd5e1';
          let stepColor = '#64748b';

          if (isRejected) {
            stepBg = '#fee2e2';
            stepBorder = '#f87171';
            stepColor = '#991b1b';
          } else if (isPassed) {
            stepBg = '#ef4444'; // Red active fill
            stepBorder = '#dc2626';
            stepColor = '#ffffff';
          }

          return (
            <div key={idx} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: 2, position: 'relative' }}>
              <div style={{
                width: '28px',
                height: '28px',
                borderRadius: '50%',
                background: stepBg,
                border: `2px solid ${stepBorder}`,
                color: stepColor,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '0.75rem',
                fontWeight: 700,
                boxShadow: isCurrent ? '0 0 0 4px rgba(239, 68, 68, 0.25)' : 'none',
                transition: 'all 0.3s ease'
              }}>
                {isPassed ? (isCurrent ? '●' : '✓') : (idx + 1)}
              </div>
              <span style={{
                fontSize: '0.72rem',
                fontWeight: isCurrent ? 700 : 500,
                color: isCurrent ? '#dc2626' : isPassed ? '#1e293b' : '#94a3b8',
                marginTop: '4px',
                textAlign: 'center',
                whiteSpace: 'nowrap'
              }}>
                {stage.label}
              </span>
            </div>
          );
        })}
      </div>

      {/* Sub-cases / Status detail note */}
      <div style={{ textAlign: 'center', marginTop: '6px', fontSize: '0.75rem', fontWeight: 600, color: isRejected ? '#dc2626' : '#991b1b', background: isRejected ? '#fee2e2' : '#fff5f5', padding: '3px 10px', borderRadius: '12px', display: 'inline-block', width: '100%', boxSizing: 'border-box', border: isRejected ? '1px solid #fecaca' : '1px solid #ffe4e6' }}>
        {isRejected ? '❌ Hồ sơ đã bị từ chối' : isWithdrawn ? '↩️ Ứng viên đã rút đơn' : subCaseText}
      </div>
    </div>
  );
}

function getSelectedStatusLabels(selectedStatuses: string[]): string {
  if (selectedStatuses.length === 0) return 'Tất cả trạng thái';
  return selectedStatuses
    .map((key) => STATUS_FILTER_TABS.find((tab) => tab.key === key)?.label || key)
    .join(', ');
}

export default function EmployerApplicationsPage({ isInterviewOnly = false }: { isInterviewOnly?: boolean }) {
  const { jobId: routeJobId } = useParams<{ jobId?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryJobId = searchParams.get('jobId') || routeJobId || '';
  const queryStatuses = isInterviewOnly
    ? ['INTERVIEW_SCHEDULED']
    : parseStatusQuery(searchParams.get('status') || '');

  const [applications, setApplications] = useState<CandidateApplication[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [subscription, setSubscription] = useState<UserSubscription | null>(null);
  const [company, setCompany] = useState<Company | null>(null);
  const [aiQuota, setAiQuota] = useState<{ used: number; limit: number; remaining: number; isUnlimited: boolean } | null>(null);

  // Filters
  const [selectedJobId, setSelectedJobId] = useState<string>(queryJobId);
  const [selectedStatuses, setSelectedStatuses] = useState<string[]>(queryStatuses);
  const [searchKeyword, setSearchKeyword] = useState<string>('');
  const [appliedSearchKeyword, setAppliedSearchKeyword] = useState<string>('');
  const statusFilterParam = isInterviewOnly
    ? ['INTERVIEW_SCHEDULED']
    : selectedStatuses.length > 0
      ? selectedStatuses
      : undefined;

  // Status update modal
  const [updatingApp, setUpdatingApp] = useState<CandidateApplication | null>(null);
  const [targetStatus, setTargetStatus] = useState<string>('UNDER_REVIEW');
  const [modalError, setModalError] = useState<string | null>(null);
  const [note, setNote] = useState<string>('');
  const [updating, setUpdating] = useState(false);

  // Interview fields
  const [scheduledAt, setScheduledAt] = useState<string>('');
  const [location, setLocation] = useState<string>('');
  const [meetingLink, setMeetingLink] = useState<string>('');

  // Job Offer fields
  const [positionTitle, setPositionTitle] = useState<string>('');
  const [salary, setSalary] = useState<number | ''>('');
  const [salaryCurrency, setSalaryCurrency] = useState<string>('VND');
  const [salaryType, setSalaryType] = useState<string>('monthly');
  const [startDate, setStartDate] = useState<string>('');
  const [benefits, setBenefits] = useState<string>('');
  const [workingLocation, setWorkingLocation] = useState<string>('');
  const [offerLetterUrl, setOfferLetterUrl] = useState<string>('');

  // Occupied Interview Slots for conflict checking
  const [occupiedSlots, setOccupiedSlots] = useState<import('../../services/employerService').OccupiedInterviewSlot[]>([]);
  const [loadingOccupiedSlots, setLoadingOccupiedSlots] = useState<boolean>(false);
  const [slotConflictWarning, setSlotConflictWarning] = useState<string | null>(null);

  // Candidate detail modal
  const [selectedAppDetail, setSelectedAppDetail] = useState<CandidateApplication | null>(null);
  const [manageInterviewApp, setManageInterviewApp] = useState<CandidateApplication | null>(null);
  const [manageOfferApp, setManageOfferApp] = useState<CandidateApplication | null>(null);

  // Reschedule Action
  const [rescheduleInterviewId, setRescheduleInterviewId] = useState<string | null>(null);
  const [rescheduleDate, setRescheduleDate] = useState<string>('');
  const [rejectInterviewId, setRejectInterviewId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState<string>('');

  // Interview Evaluation Action
  const [evaluatingInterviewId, setEvaluatingInterviewId] = useState<string | null>(null);
  const [interviewResult, setInterviewResult] = useState<'COMPLETED' | 'fail'>('COMPLETED');

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;
  const [totalPages, setTotalPages] = useState(1);
  const [totalItems, setTotalItems] = useState(0);
  const [exporting, setExporting] = useState(false);

  // Custom UI Notifications
  const [toastMsg, setToastMsg] = useState<{ text: string; type: 'error' | 'success' } | null>(null);
  const [confirmDialog, setConfirmDialog] = useState<{ message: string; onConfirm: () => void } | null>(null);

  const showToast = (text: string, type: 'error' | 'success' = 'error') => {
    setToastMsg({ text, type });
    setTimeout(() => setToastMsg(null), 4000);
  };

  useEffect(() => {
    loadJobs();
  }, []);

  useEffect(() => {
    if (updatingApp && targetStatus === 'INTERVIEW_SCHEDULED') {
      const jobId = updatingApp.job?.id;
      const datePart = scheduledAt ? scheduledAt.split('T')[0] : new Date().toISOString().split('T')[0];
      if (jobId) {
        setLoadingOccupiedSlots(true);
        employerService.getOccupiedInterviewSlots(jobId, datePart)
          .then(slots => setOccupiedSlots(slots.filter(s => new Date(s.scheduledAt).getTime() > Date.now())))
          .catch(() => setOccupiedSlots([]))
          .finally(() => setLoadingOccupiedSlots(false));
      }
    } else {
      setOccupiedSlots([]);
      setSlotConflictWarning(null);
    }
  }, [updatingApp?.id, targetStatus, scheduledAt ? scheduledAt.split('T')[0] : '']);

  useEffect(() => {
    if (scheduledAt && occupiedSlots.length > 0) {
      const selectedTime = new Date(scheduledAt).getTime();
      if (!isNaN(selectedTime)) {
        const conflictSlot = occupiedSlots.find(slot => {
          if (updatingApp?.interviews?.some((iv: any) => iv.id === slot.id)) {
            return false;
          }
          const slotTime = new Date(slot.scheduledAt).getTime();
          const diffMinutes = Math.abs(selectedTime - slotTime) / (1000 * 60);
          return diffMinutes < 30;
        });
        if (conflictSlot) {
          const timeStr = new Date(conflictSlot.scheduledAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
          setSlotConflictWarning(`⚠️ Cảnh báo: Thời gian này quá gần lịch phỏng vấn của ${conflictSlot.candidateName} lúc ${timeStr} (tối thiểu phải cách nhau 30 phút).`);
        } else {
          setSlotConflictWarning(null);
        }
      }
    } else {
      setSlotConflictWarning(null);
    }
  }, [scheduledAt, occupiedSlots, updatingApp?.interviews]);

  useEffect(() => {
    setCurrentPage(1);
    loadApplications();
  }, [selectedJobId, selectedStatuses]);

  async function loadJobs() {
    try {
      const pageSize = 100;
      const [firstPage, subData, compData, quotaData] = await Promise.all([
        employerService.getJobs({ page: 1, size: pageSize }),
        billingService.getMySubscription().catch(() => null),
        employerService.getCompanyProfile().catch(() => null),
        employerService.getAiRankingQuota().catch(() => null)
      ]);
      const allJobs: Job[] = [...(firstPage.items || [])];
      const totalPages = Math.min(firstPage.totalPages || 1, 10);
      if (totalPages > 1) {
        const pagePromises = [];
        for (let page = 2; page <= totalPages; page += 1) {
          pagePromises.push(employerService.getJobs({ page, size: pageSize }));
        }
        const extraPages = await Promise.all(pagePromises);
        extraPages.forEach(p => allJobs.push(...(p.items || [])));
      }
      setJobs(allJobs);
      setSubscription(subData);
      setCompany(compData);
      setAiQuota(quotaData);
    } catch (err) {
      console.error('Failed to load jobs', err);
    }
  }

  const loadApplications = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await employerService.getApplications({
        jobId: selectedJobId || undefined,
        status: statusFilterParam,
        search: appliedSearchKeyword || undefined,
        page: currentPage,
        size: itemsPerPage,
      });
      setApplications(sortApplicationsByStatus(data.items || []));
      setTotalPages(data.totalPages || 1);
      setTotalItems(data.totalItems || 0);
    } catch (err: any) {
      console.error('Failed to load applications:', err);
      const backendMsg = err.response?.data?.message || err.message || 'Không thể tải danh sách ứng viên. Vui lòng thử lại.';
      setError(backendMsg);
    } finally {
      setLoading(false);
    }
  }, [appliedSearchKeyword, selectedJobId, statusFilterParam, currentPage]);

  useEffect(() => {
    void loadApplications();
  }, [loadApplications]);

  // Handle automatic opening of application details from notifications
  useEffect(() => {
    const appId = searchParams.get('appId');
    if (appId) {
      // First check if it's in the current page
      const targetApp = applications.find(a => a.id === appId);
      if (targetApp) {
        setSelectedAppDetail(targetApp);
        setSearchParams(prev => {
          prev.delete('appId');
          return prev;
        }, { replace: true });
      } else {
        // Not in current page, load from backend directly
        employerService.getApplicationDetail(appId).then(app => {
          setSelectedAppDetail(app);
          setSearchParams(prev => {
            prev.delete('appId');
            return prev;
          }, { replace: true });
        }).catch(err => {
          console.error('Failed to load app detail from notification:', err);
        });
      }
    }
  }, [searchParams, applications, setSearchParams]);

  // Polling for AI Ranking
  useEffect(() => {
    const hasProcessing = applications.some(app => app.aiMatchScore === -1);
    if (!hasProcessing) return;

    const interval = setInterval(() => {
      employerService.getApplications({
        jobId: selectedJobId || undefined,
        status: statusFilterParam,
        search: appliedSearchKeyword || undefined,
      }).then(data => setApplications(sortApplicationsByStatus(data.items || []))).catch(() => {});
    }, 10000);

    return () => clearInterval(interval);
  }, [applications, appliedSearchKeyword, selectedJobId, statusFilterParam]);

  const [bulkRanking, setBulkRanking] = useState(false);
  async function handleBulkAiRanking() {
    if (!selectedJobId) return;
    if (aiQuota && !aiQuota.isUnlimited && aiQuota.remaining <= 0) {
      if (await customConfirm(`Tài khoản Miễn phí của bạn đã sử dụng hết ${aiQuota.limit}/${aiQuota.limit} lượt Xếp hạng ứng viên bằng AI trong tháng này.\n\nLượt miễn phí sẽ tự động làm mới vào đầu tháng sau, hoặc bạn có thể nâng cấp gói dịch vụ để xếp hạng không giới hạn ngay bây giờ. Bạn có muốn đi đến trang Nâng cấp gói dịch vụ không?`)) {
        window.location.href = '/employer/subscription/plans';
      }
      return;
    }
    if (await customConfirm('Hệ thống sẽ Xếp hạng ứng viên bằng AI dưới nền. Bạn có muốn tiếp tục?')) {
      setBulkRanking(true);
      try {
        setApplications(apps => apps.map(app => {
          if (app.aiMatchScore == null || app.needRerank || app.aiMatchScore < 0) {
            return { ...app, aiMatchScore: -1, needRerank: false };
          }
          return app;
        }));
        const res = await employerService.triggerBulkAiRanking(selectedJobId);
        showToast(res?.message || 'Đã bắt đầu Xếp hạng ứng viên bằng AI', 'success');
        employerService.getAiRankingQuota().then(setAiQuota).catch(() => null);
      } catch (err: any) {
        const backendMsg = err.response?.data?.message || err.message || 'Có lỗi khi xếp hạng AI';
        if (err.response?.data?.errorCode === 'QUOTA_EXCEEDED' || err.response?.status === 403) {
          if (await customConfirm(backendMsg + '\n\nBạn có muốn đi đến trang Nâng cấp gói dịch vụ để xếp hạng không giới hạn?')) {
            window.location.href = '/employer/subscription/plans';
          }
        } else {
          showToast(backendMsg, 'error');
        }
        loadApplications();
      } finally {
        setBulkRanking(false);
      }
    }
  }

  async function handleOpenAppDetail(app: CandidateApplication) {
    setSelectedAppDetail(app);
    try {
      const fresh = await employerService.getApplicationDetail(app.id);
      setSelectedAppDetail(fresh);
      setApplications((prev) => prev.map((item) => (item.id === app.id ? fresh : item)));
    } catch (err) {
      console.error('Failed to update app status to viewed', err);
    }
  }

  function openBlobInNewTab(blob: Blob) {
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener,noreferrer');
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }

  async function openApplicationCv(id: string) {
    try {
      openBlobInNewTab(await employerService.downloadApplicationCv(id));
    } catch (err) {
      console.error('Failed to open CV', err);
      setError('Không thể mở CV ứng tuyển');
    }
  }

  function handleSearchSubmit(e: React.FormEvent) {
    e.preventDefault();
    setCurrentPage(1);
    loadApplications();
  }

  function getDetailedStatus(app: CandidateApplication) {
    if (app.status === 'INTERVIEW_SCHEDULED') {
      const interview = app.interviews && app.interviews.length > 0 ? app.interviews[app.interviews.length - 1] : null;
      if (interview) {
        const iStatus = (interview.status || '').toUpperCase();
        if (iStatus === 'ACCEPTED') return { label: 'Ứng viên đã xác nhận', color: '#0369a1', bg: '#e0f2fe' };
        if (iStatus === 'NO_RESPONSE' || ((iStatus === 'SCHEDULED' || iStatus === 'PENDING_RESPONSE') && interview.scheduledAt && new Date(interview.scheduledAt).getTime() <= Date.now())) return { label: 'UV không phản hồi', color: '#be123c', bg: '#ffe4e6' };
        if (iStatus === 'SCHEDULED' || iStatus === 'PENDING_RESPONSE') return { label: 'Chờ ứng viên xác nhận', color: '#92400e', bg: '#fef3c7' };
        if (iStatus === 'RESCHEDULE_REQUESTED') return { label: 'UV xin đổi lịch', color: '#be123c', bg: '#ffe4e6' };
        if (iStatus === 'DECLINED') return { label: 'UV từ chối tham gia', color: '#be123c', bg: '#ffe4e6' };
        if (iStatus === 'COMPLETED') return { label: 'Đạt (Chờ Offer)', color: '#047857', bg: '#d1fae5' };
        if (iStatus === 'NO_SHOW') return { label: 'UV không đến PV', color: '#991b1b', bg: '#fee2e2' };
      }
      return { label: 'Chờ xếp lịch', color: '#475569', bg: '#f1f5f9' };
    }
    return statusConfig[app.status] || { label: app.status, color: '#475569', bg: '#f1f5f9' };
  }

  function getCvFileName(app: CandidateApplication): string {
    return app.submittedResume?.originalFileName
      || app.cv?.originalFileName
      || (app.cvVersion ? 'CV Builder' : '');
  }

  async function handleExportExcel() {
    const EXPORT_PAGE_SIZE = 200;
    setExporting(true);
    try {
      const firstPage = await employerService.getApplications({
        jobId: selectedJobId || undefined,
        status: statusFilterParam,
        search: appliedSearchKeyword || undefined,
        page: 1,
        size: EXPORT_PAGE_SIZE,
      });

      const allApps: CandidateApplication[] = [...(firstPage.items || [])];
      const pages = Math.min(firstPage.totalPages || 1, 50);
      for (let page = 2; page <= pages; page += 1) {
        const nextPage = await employerService.getApplications({
          jobId: selectedJobId || undefined,
          status: statusFilterParam,
          search: appliedSearchKeyword || undefined,
          page,
          size: EXPORT_PAGE_SIZE,
        });
        allApps.push(...(nextPage.items || []));
      }

      if (allApps.length === 0) {
        showToast('Không có ứng viên nào phù hợp bộ lọc để xuất.');
        return;
      }

      const sortedApps = sortApplicationsByStatus(allApps);
      const statusLabels = getSelectedStatusLabels(selectedStatuses);
      const positionName = jobs.find((j) => j.id === selectedJobId)?.title || '';
      const fileBaseName = selectedJobId
        ? `${positionName || 'Vi tri tuyen dung'} ${statusLabels}`
        : `Tất cả các ứng viên ${statusLabels}`;
      const today = new Date().toLocaleDateString('vi-VN').replace(/\//g, '-');
      const headers = ['STT', 'Họ tên', 'Số điện thoại', 'Địa điểm', 'Kỹ năng', 'Vị trí', 'Ngày nộp', 'Trạng thái', 'CV', 'Điểm AI'];
      const rows = sortedApps.map((app, index) => [
        index + 1,
        app.candidate?.fullName || 'Ứng viên ẩn danh',
        app.candidate?.phone || '',
        app.candidate?.location || app.preferredLocation || '',
        (app.candidate?.skills || []).join(', '),
        app.job?.title || positionName,
        app.submittedAt ? new Date(app.submittedAt).toLocaleDateString('vi-VN') : '',
        getDetailedStatus(app).label,
        getCvFileName(app),
        app.aiMatchScore != null ? app.aiMatchScore : '',
      ]);

      const xml = buildExcelXml({
        sheetName: selectedJobId ? (positionName || 'Ung vien') : 'Tất cả ứng viên',
        title: `${fileBaseName} (${sortedApps.length} hồ sơ, xuất ngày ${today})`,
        headers,
        rows,
      });
      downloadExcelFile(xml, `${sanitizeFileName(fileBaseName)}.xls`);
      showToast(`Đã xuất ${sortedApps.length} ứng viên. Mở file bằng Microsoft Excel.`, 'success');
    } catch (err: any) {
      const backendMsg = err.response?.data?.message || err.message || 'Không thể xuất danh sách ứng viên. Vui lòng thử lại.';
      showToast(backendMsg);
    } finally {
      setExporting(false);
    }
  }

  function applyStatusFilters(next: string[]) {
    setSelectedStatuses(next);
    setSearchParams((prev) => {
      if (next.length === 0) {
        prev.delete('status');
      } else {
        prev.set('status', next.join(','));
      }
      return prev;
    }, { replace: true });
  }

  function toggleStatusFilter(key: string) {
    if (!key) {
      applyStatusFilters([]);
      return;
    }
    const exists = selectedStatuses.includes(key);
    const next = exists
      ? selectedStatuses.filter((status) => status !== key)
      : [...selectedStatuses, key];
    applyStatusFilters(next);
  }

  function openUpdateModal(app: CandidateApplication, defaultStatus?: string) {
    setUpdatingApp(app);
    setTargetStatus(defaultStatus || app.status || 'UNDER_REVIEW');
    setModalError(null);
    setNote('');
    setScheduledAt('');
    setLocation('');
    setMeetingLink('');
    setPositionTitle(app.job.title || '');
    setSalary('');
    setSalaryCurrency('VND');
    setSalaryType('monthly');
    setStartDate('');
    setBenefits('');
    setWorkingLocation('');
    setOfferLetterUrl('');
  }

  async function handleConfirmUpdate() {
    if (!updatingApp) return;
    setUpdating(true);
    setModalError(null);
    try {
      if (targetStatus === 'INTERVIEW_SCHEDULED') {
        if (!scheduledAt) throw new Error('Vui lòng chọn ngày giờ phỏng vấn');
        if (new Date(scheduledAt).getTime() < Date.now()) throw new Error('Ngày giờ phỏng vấn không được ở trong quá khứ');
        if (slotConflictWarning) throw new Error(slotConflictWarning);
        if (!location) throw new Error('Vui lòng chọn hình thức hoặc địa điểm phỏng vấn');
        if (location === 'Trực tuyến' && !meetingLink) throw new Error('Vui lòng nhập link họp cho phỏng vấn trực tuyến');
        if (meetingLink && !/^https?:\/\/.+/.test(meetingLink)) throw new Error('Link họp trực tuyến phải bắt đầu bằng http:// hoặc https://');
        await employerService.scheduleInterview(updatingApp.id, {
          scheduledAt,
          location,
          meetingLink,
          note
        });
      } else if (targetStatus === 'ACCEPTED' || targetStatus === 'UPDATE_OFFER') {
        if (!positionTitle || !positionTitle.trim()) throw new Error('Vui lòng nhập chức danh');
        if (!salary || isNaN(Number(salary)) || Number(salary) <= 0) throw new Error('Vui lòng nhập mức lương hợp lệ (phải lớn hơn 0)');
        if (!startDate) throw new Error('Vui lòng chọn ngày bắt đầu làm việc');
        if (new Date(startDate).getTime() < new Date().setHours(0,0,0,0)) throw new Error('Ngày bắt đầu làm việc không được ở trong quá khứ');
        if (offerLetterUrl && !/^https?:\/\/.+/.test(offerLetterUrl)) throw new Error('Link Offer Letter phải bắt đầu bằng http:// hoặc https://');

        await employerService.createJobOffer(updatingApp.id, {
          positionTitle,
          salary: salary ? Number(salary) : undefined,
          salaryCurrency,
          salaryType,
          startDate: startDate || undefined,
          benefits,
          workingLocation,
          offerLetterUrl,
          employerNote: note
        });
      } else if (targetStatus === 'REJECTED') {
        await employerService.rejectApplication(updatingApp.id, note);
      } else if (targetStatus === 'EVALUATE_INTERVIEW') {
        if (!evaluatingInterviewId) throw new Error('Thiếu Interview ID');
        await employerService.employerUpdateInterviewResult(evaluatingInterviewId, interviewResult, note);
        
        if (interviewResult === 'COMPLETED') {
           showToast('Phỏng vấn đạt! Vui lòng tạo Lời mời làm việc.', 'success');
           await loadApplications(); // Ensure background state is updated immediately!
           setTargetStatus('ACCEPTED');
           setPositionTitle(updatingApp.job.title || '');
           setSalary('');
           setSalaryCurrency('VND');
           setSalaryType('monthly');
           setStartDate('');
           setBenefits('');
           setWorkingLocation('');
           setOfferLetterUrl('');
           setNote('');
           setUpdating(false);
           return; // Giữ form mở, chuyển sang bước Offer
        }
      } else {
        await employerService.updateApplicationStatus(
          updatingApp.id,
          targetStatus,
          note
        );
      }

      // Reload applications to get latest data
      await loadApplications();
      setUpdatingApp(null);
      if (selectedAppDetail) {
        setSelectedAppDetail(null); // Just close detail modal to avoid stale data
      }
      showToast(targetStatus === 'ACCEPTED' ? 'Đã gửi Thư mời nhận việc (Job Offer) thành công cho ứng viên!' : 'Cập nhật trạng thái thành công!', 'success');
    } catch (err: any) {
      const message =
        err?.response?.data?.message || err?.message || 'Có lỗi xảy ra khi cập nhật trạng thái';
      setModalError(message);
    } finally {
      setUpdating(false);
    }
  }

  const currentJob = jobs.find((j) => j.id === selectedJobId);
  const jobOptions = useMemo(
    () => [
      { value: '', label: '-- Tất cả việc làm --', keywords: 'tat ca viec lam all' },
      ...jobs.map((job) => ({
        value: job.id,
        label: `${job.title} (${job.status})`,
        keywords: `${job.title} ${job.status}`,
      })),
    ],
    [jobs],
  );

  if (company && company.verificationStatus !== 'verified') {
    return (
      <section className="employer-verify-gate">
        <div className="employer-verify-card">
          <div className="employer-verify-icon" aria-hidden="true">
            <IconLock size={28} />
          </div>
          <h2>Công ty chưa được xác thực</h2>
          <p className="employer-verify-copy">
            Để đăng tin tuyển dụng, bạn cần xác thực doanh nghiệp trước. Vui lòng hoàn tất hồ sơ pháp lý để Admin phê duyệt.
          </p>
          <div className="employer-verify-actions">
            <Link to="/employer/verification" className="employer-verify-btn primary">
              Đi tới trang Xác thực
            </Link>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="content-card" style={{ padding: '24px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px', marginBottom: '24px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <Link to="/employer/jobs" style={{ textDecoration: 'none', color: '#2563eb', fontWeight: 500, fontSize: '0.9rem' }}>
              ← Quản lý việc làm
            </Link>
          </div>
          <h1 style={{ color: '#0f172a', margin: '8px 0 4px 0', fontSize: '1.6rem' }}>
            {isInterviewOnly
              ? 'Lịch phỏng vấn'
              : currentJob ? `👥 Ứng viên: ${currentJob.title}` : '👥 Tất cả đơn ứng tuyển'}
          </h1>
          <p style={{ color: '#64748b', margin: 0, fontSize: '0.95rem' }}>
            {isInterviewOnly 
              ? 'Quản lý lịch hẹn phỏng vấn của các ứng viên'
              : 'Quản lý hồ sơ ứng viên, xem CV và chuyển đổi trạng thái vòng tuyển dụng'}
          </p>
        </div>

        {selectedJobId && currentJob?.rankingConfig?.enabled && (
          <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              <button
                onClick={handleBulkAiRanking}
                disabled={bulkRanking}
                style={{
                  background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
                  color: '#fff',
                  border: 'none',
                  padding: '8px 16px',
                  borderRadius: '6px',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  cursor: bulkRanking ? 'not-allowed' : 'pointer',
                  opacity: bulkRanking ? 0.7 : 1,
                  boxShadow: '0 2px 4px rgba(99,102,241,0.2)',
                }}
              >
                {bulkRanking ? '⏳ Đang khởi tạo...' : '✨ Xếp hạng ứng viên bằng AI'}
              </button>
              {aiQuota && !aiQuota.isUnlimited && (
                <div style={{ fontSize: '0.75rem', marginTop: '4px', fontWeight: 500, color: '#64748b' }}>
                  (Còn lại {Math.max(0, aiQuota.remaining)}/{aiQuota.limit} lượt xếp hạng miễn phí tháng này)
                </div>
              )}
            </div>
            <button
              onClick={() => {
                setSelectedJobId('');
                setSearchParams({});
              }}
              style={{
                background: '#f1f5f9',
                color: '#334155',
                border: '1px solid #cbd5e1',
                padding: '8px 16px',
                borderRadius: '6px',
                fontWeight: 600,
                fontSize: '0.85rem',
                cursor: 'pointer',
              }}
            >
              Hiển thị tất cả JD
            </button>
          </div>
        )}
      </div>

      {/* Filter and Search Bar */}
      <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '20px' }}>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ flex: '1 1 260px', minWidth: '220px' }}>
            <SearchableCombobox
              value={selectedJobId}
              options={jobOptions}
              placeholder="Tìm vị trí tuyển dụng..."
              ariaLabel="Chọn vị trí tuyển dụng"
              emptyText="Không tìm thấy vị trí tuyển dụng."
              clearValueOnType={false}
              inputClassName="employer-job-filter-input"
              onChange={(jobId) => {
                setSelectedJobId(jobId);
                setSearchParams((prev) => {
                  if (jobId) {
                    prev.set('jobId', jobId);
                  } else {
                    prev.delete('jobId');
                  }
                  return prev;
                }, { replace: true });
              }}
            />
          </div>

          <div style={{ flex: '1 1 200px' }}>
            <input
              type="text"
              placeholder="Tìm tên ứng viên, email, SĐT..."
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              style={{ width: '100%', padding: '10px 14px', borderRadius: '6px', border: '1px solid #cbd5e1' }}
            />
          </div>

          <button
            type="submit"
            style={{ background: '#2563eb', color: '#fff', border: 'none', padding: '10px 20px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer' }}
          >
            Tìm kiếm
          </button>
          {!isInterviewOnly && (
            <button
              type="button"
              onClick={handleExportExcel}
              disabled={exporting}
              title="Xuất danh sách ứng viên theo trạng thái đang chọn"
              style={{
                background: '#fff',
                color: '#047857',
                border: '1px solid #047857',
                padding: '10px 16px',
                borderRadius: '6px',
                fontWeight: 600,
                cursor: exporting ? 'not-allowed' : 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                opacity: exporting ? 0.7 : 1,
              }}
            >
              <FiDownload width={16} height={16} />
              {exporting ? 'Đang xuất...' : 'Xuất Excel'}
            </button>
          )}
        </form>

        {/* Status Tabs */}
        {!isInterviewOnly && (
          <div style={{ display: 'flex', gap: '8px', marginTop: '16px', overflowX: 'auto', paddingBottom: '4px' }}>
          {[{ key: '', label: 'Tất cả trạng thái' }, ...STATUS_FILTER_TABS].map((tab) => {
            const isAllTab = tab.key === '';
            const isActive = isAllTab ? selectedStatuses.length === 0 : selectedStatuses.includes(tab.key);
            return (
            <button
              key={tab.key || 'ALL'}
              type="button"
              onClick={() => toggleStatusFilter(tab.key)}
              style={{
                background: isActive ? '#2563eb' : '#fff',
                color: isActive ? '#fff' : '#475569',
                border: isActive ? '1px solid #2563eb' : '1px solid #cbd5e1',
                padding: '6px 14px',
                borderRadius: '20px',
                fontSize: '0.85rem',
                fontWeight: isActive ? 600 : 500,
                cursor: 'pointer',
                whiteSpace: 'nowrap',
              }}
            >
              {tab.label}
            </button>
            );
          })}
          </div>
        )}
      </div>

      {/* Applications Table / Cards */}
      {loading ? (
        <p className="loading" style={{ textAlign: 'center', padding: '40px 0' }}>Đang tải hồ sơ ứng viên...</p>
      ) : error ? (
        <div style={{ background: '#fef2f2', color: '#991b1b', padding: '16px', borderRadius: '8px', border: '1px solid #fecaca' }}>{error}</div>
      ) : applications.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '48px 20px', background: '#f8fafc', borderRadius: '8px', border: '1px dashed #cbd5e1' }}>
          <p style={{ fontSize: '1.1rem', color: '#64748b', margin: '0 0 8px 0' }}>Không tìm thấy đơn ứng tuyển nào phù hợp với bộ lọc.</p>
          <p style={{ fontSize: '0.9rem', color: '#94a3b8', margin: 0 }}>Hãy thử thay đổi tiêu chí tìm kiếm hoặc chọn lại việc làm.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {(() => {
            return (
              <>
          {(isInterviewOnly ? applications.filter(app => {
            if (!app.interviews || app.interviews.length === 0) return false;
            const ivStatus = app.interviews[app.interviews.length - 1].status;
            return ivStatus !== 'ACCEPTED' && ivStatus !== 'COMPLETED';
          }) : applications)
            .map((app) => {
            const st = getDetailedStatus(app);
            const candidateName = app.candidate?.fullName || 'Ứng viên ẩn danh';
            const candidateEmail = app.candidate?.phone ? `${app.candidate.phone}` : 'Chưa có SĐT';
            const aiMatchScore = app.aiMatchScore;

            return (
              <div
                key={app.id}
                style={{
                  border: '1px solid #e2e8f0',
                  borderRadius: '10px',
                  padding: '20px',
                  background: '#fff',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '16px',
                }}
              >
                <RecruitmentStageStepper app={app} />
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '20px' }}>
                {/* Candidate and Job Info */}
                <div
                  onClick={() => setSelectedAppDetail(app)}
                  style={{ flex: '1 1 400px', cursor: 'pointer', transition: 'opacity 0.2s' }}
                  title="Click để xem chi tiết hồ sơ & CV ứng viên"
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
                    <div
                      style={{
                        width: '44px',
                        height: '44px',
                        borderRadius: '50%',
                        background: '#e2e8f0',
                        color: '#334155',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontWeight: 700,
                        fontSize: '1.1rem',
                      }}
                    >
                      {candidateName.charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <h3 style={{ margin: 0, color: '#0f172a', fontSize: '1.1rem' }}>{candidateName}</h3>
                      <div style={{ fontSize: '0.85rem', color: '#64748b', marginTop: '2px' }}>
                        📞 {candidateEmail} {app.candidate?.location ? `| 📍 ${app.candidate.location}` : ''}
                      </div>
                    </div>
                    <span
                      style={{
                        background: st.bg,
                        color: st.color,
                        padding: '4px 10px',
                        borderRadius: '12px',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        marginLeft: 'auto',
                      }}
                    >
                      {st.label}
                    </span>
                  </div>

                  {/* Applied Job & Date */}
                  <div style={{ background: '#f8fafc', padding: '10px 12px', borderRadius: '6px', marginBottom: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: '0.85rem', color: '#334155', fontWeight: 500 }}>
                      💼 Vị trí: <strong>{app.job.title}</strong>
                    </span>
                    <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                      🕒 Nộp ngày: {new Date(app.submittedAt).toLocaleDateString('vi-VN')}
                    </span>
                  </div>

                  {/* Skills */}
                  {app.candidate?.skills && app.candidate.skills.length > 0 && (
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '12px' }}>
                      {app.candidate.skills.map((skill, idx) => (
                        <span
                          key={idx}
                          style={{
                            background: '#f1f5f9',
                            color: '#475569',
                            padding: '3px 8px',
                            borderRadius: '4px',
                            fontSize: '0.75rem',
                          }}
                        >
                          {skill}
                        </span>
                      ))}
                    </div>
                  )}

                  {/* CV Document Link */}
                  <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                    {app.cv ? (
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                          background: '#eff6ff',
                          color: '#2563eb',
                          padding: '6px 12px',
                          borderRadius: '6px',
                          fontSize: '0.85rem',
                          fontWeight: 500,
                        }}
                      >
                        📄 CV đính kèm: {app.cv.originalFileName}
                      </span>
                    ) : app.cvVersion ? (
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                          background: '#f5f3ff',
                          color: '#7c3aed',
                          padding: '6px 12px',
                          borderRadius: '6px',
                          fontSize: '0.85rem',
                          fontWeight: 500,
                        }}
                      >
                        📝 CV online: {app.cvVersion.title}
                      </span>
                    ) : (
                      <span style={{ fontSize: '0.85rem', color: '#94a3b8' }}>Chưa có file CV đính kèm</span>
                    )}
                  </div>

                  {/* AI Ranking Score */}
                  {((aiMatchScore !== undefined && aiMatchScore !== null) || app.aiMatchAnalysis === 'PROCESSING' || app.aiMatchAnalysis === 'ERROR') && (
                    <div style={{ marginTop: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      {aiMatchScore === -1 || app.aiMatchAnalysis === 'PROCESSING' ? (
                        <span style={{
                          background: '#e0f2fe', color: '#0369a1', padding: '4px 10px', borderRadius: '12px',
                          fontSize: '0.85rem', fontWeight: 600, border: '1px solid #bae6fd'
                        }}>
                          ⏳ Đang phân tích AI...
                        </span>
                      ) : (aiMatchScore === -2 || app.aiMatchAnalysis === 'ERROR') ? (
                        <span style={{
                          background: '#fee2e2', color: '#991b1b', padding: '4px 10px', borderRadius: '12px',
                          fontSize: '0.85rem', fontWeight: 600, border: '1px solid #fecaca'
                        }} title={app.aiMatchAnalysis === 'ERROR' ? 'Lỗi phân tích AI: Hệ thống quá tải hoặc lỗi kết nối.' : app.aiMatchAnalysis}>
                          ❌ Lỗi phân tích
                        </span>
                      ) : (
                        <span style={{
                          background: aiMatchScore !== undefined && aiMatchScore >= 80 ? '#dcfce7' : aiMatchScore !== undefined && aiMatchScore >= 50 ? '#fef9c3' : '#fee2e2',
                          color: aiMatchScore !== undefined && aiMatchScore >= 80 ? '#166534' : aiMatchScore !== undefined && aiMatchScore >= 50 ? '#854d0e' : '#991b1b',
                          padding: '4px 10px',
                          borderRadius: '12px',
                          fontSize: '0.85rem',
                          fontWeight: 600,
                          border: `1px solid ${aiMatchScore !== undefined && aiMatchScore >= 80 ? '#bbf7d0' : aiMatchScore !== undefined && aiMatchScore >= 50 ? '#fef08a' : '#fecaca'}`
                        }}>
                          ✨ AI Match: {aiMatchScore}%
                        </span>
                      )}
                      {app.needRerank && aiMatchScore !== undefined && aiMatchScore !== null && aiMatchScore > -1 && (
                        <span style={{ fontSize: '0.75rem', color: '#f59e0b', fontStyle: 'italic', background: '#fffbeb', padding: '2px 6px', borderRadius: '4px', border: '1px solid #fde68a' }}>
                          ⚠️ JD thay đổi, cần chấm lại
                        </span>
                      )}
                    </div>
                  )}
                </div>

                {/* Actions */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', minWidth: '180px', alignSelf: 'center' }}>
                  <button
                    onClick={() => void handleOpenAppDetail(app)}
                    style={{
                      background: '#f8fafc',
                      color: '#2563eb',
                      border: '1px solid #93c5fd',
                      padding: '8px 14px',
                      borderRadius: '6px',
                      fontWeight: 600,
                      fontSize: '0.85rem',
                      cursor: 'pointer',
                      textAlign: 'center',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '6px',
                    }}
                  >
                    👁️ Xem chi tiết hồ sơ
                  </button>

                  <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', justifyContent: 'center' }}>
                    {(app.status === 'SUBMITTED' || app.status === 'UNDER_REVIEW' || app.status === 'INTERVIEW_SCHEDULED') && !app.interviews?.some((iv: any) => iv.status === 'SCHEDULED' || iv.status === 'PENDING_RESPONSE' || iv.status === 'ACCEPTED' || iv.status === 'RESCHEDULE_REQUESTED' || iv.status === 'COMPLETED') && !isInterviewOnly && (
                      <button
                        onClick={() => openUpdateModal(app, 'INTERVIEW_SCHEDULED')}
                        style={{ flex: 1, background: '#fef3c7', color: '#b45309', border: '1px solid #fde68a', padding: '6px 10px', borderRadius: '6px', fontWeight: 600, fontSize: '0.75rem', cursor: 'pointer' }}
                      >
                        📅 Lịch PV
                      </button>
                    )}
                    {app.interviews && app.interviews.length > 0 && !app.interviews?.some((iv: any) => iv.status === 'COMPLETED') && (
                      <button
                        onClick={() => setManageInterviewApp(app)}
                        style={{ flex: 1, background: '#e0e7ff', color: '#4338ca', border: '1px solid #c7d2fe', padding: '6px 10px', borderRadius: '6px', fontWeight: 600, fontSize: '0.75rem', cursor: 'pointer' }}
                      >
                        🎤 Quản lý PV
                      </button>
                    )}

                    {app.status === 'INTERVIEW_SCHEDULED' && app.interviews?.some((iv: any) => iv.status === 'COMPLETED') && !isInterviewOnly && (
                      <button
                        onClick={() => openUpdateModal(app, 'ACCEPTED')}
                        style={{ flex: 1, background: '#d1fae5', color: '#047857', border: '1px solid #a7f3d0', padding: '6px 10px', borderRadius: '6px', fontWeight: 600, fontSize: '0.75rem', cursor: 'pointer' }}
                      >
                        Gửi Offer
                      </button>
                    )}

                    {app.status === 'ACCEPTED' && app.jobOffer && !isInterviewOnly && (
                      <button
                        onClick={() => setManageOfferApp(app)}
                        style={{ flex: 1, background: '#10b981', color: '#fff', border: '1px solid #059669', padding: '6px 10px', borderRadius: '6px', fontWeight: 600, fontSize: '0.75rem', cursor: 'pointer' }}
                      >
                        💼 Quản lý Offer
                      </button>
                    )}
                    {app.status !== 'REJECTED' && app.status !== 'ACCEPTED' && !isInterviewOnly && (
                      <button
                        onClick={() => openUpdateModal(app, 'REJECTED')}
                        style={{ flex: 1, background: '#fee2e2', color: '#b91c1c', border: '1px solid #fecaca', padding: '6px 10px', borderRadius: '6px', fontWeight: 600, fontSize: '0.75rem', cursor: 'pointer' }}
                      >
                        Từ chối
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </div>
          );
          })}
          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '12px', marginTop: '24px' }}>
              <button
                onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                disabled={currentPage === 1}
                style={{
                  padding: '8px 16px',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  background: currentPage === 1 ? '#f8fafc' : '#fff',
                  color: currentPage === 1 ? '#94a3b8' : '#334155',
                  cursor: currentPage === 1 ? 'not-allowed' : 'pointer',
                  fontWeight: 500,
                  transition: 'all 0.2s'
                }}
              >
                Trước
              </button>

              <div style={{ display: 'flex', gap: '8px' }}>
                {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
                  <button
                    key={page}
                    onClick={() => setCurrentPage(page)}
                    style={{
                      width: '36px',
                      height: '36px',
                      borderRadius: '6px',
                      border: currentPage === page ? 'none' : '1px solid #cbd5e1',
                      background: currentPage === page ? '#2563eb' : '#fff',
                      color: currentPage === page ? '#fff' : '#334155',
                      fontWeight: currentPage === page ? 600 : 500,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      transition: 'all 0.2s'
                    }}
                  >
                    {page}
                  </button>
                ))}
              </div>

              <button
                onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                disabled={currentPage === totalPages}
                style={{
                  padding: '8px 16px',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  background: currentPage === totalPages ? '#f8fafc' : '#fff',
                  color: currentPage === totalPages ? '#94a3b8' : '#334155',
                  cursor: currentPage === totalPages ? 'not-allowed' : 'pointer',
                  fontWeight: 500,
                  transition: 'all 0.2s'
                }}
              >
                Sau
              </button>
            </div>
          )}
              </>
            );
          })()}
        </div>
      )}

      {/* Status Update Modal */}
      {updatingApp && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(15, 23, 42, 0.6)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '20px',
          }}
        >
          <div style={{ background: '#fff', borderRadius: '12px', padding: '24px', width: '100%', maxWidth: '500px', boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)', maxHeight: '90vh', overflowY: 'auto' }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#0f172a', fontSize: '1.25rem' }}>
              {targetStatus === 'UNDER_REVIEW' && 'Duyệt hồ sơ (Đưa vào vòng xem xét)'}
              {targetStatus === 'INTERVIEW_SCHEDULED' && 'Lên lịch phỏng vấn'}
              {targetStatus === 'ACCEPTED' && 'Gửi Lời mời làm việc (Job Offer)'}
              {targetStatus === 'REJECTED' && 'Từ chối ứng viên'}
              {targetStatus === 'EVALUATE_INTERVIEW' && 'Đánh giá kết quả phỏng vấn'}
              {targetStatus !== 'UNDER_REVIEW' && targetStatus !== 'INTERVIEW_SCHEDULED' && targetStatus !== 'ACCEPTED' && targetStatus !== 'REJECTED' && targetStatus !== 'EVALUATE_INTERVIEW' && 'Thao tác hồ sơ'}
            </h3>

            <div style={{ fontSize: '0.9rem', color: '#475569', marginBottom: '16px' }}>
              Ứng viên: <strong>{updatingApp.candidate?.fullName}</strong>
            </div>

            {targetStatus === 'EVALUATE_INTERVIEW' && (
              <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '16px' }}>
                <h4 style={{ margin: '0 0 12px 0', fontSize: '1rem', color: '#0f172a' }}>Đánh giá kết quả</h4>
                <div style={{ display: 'flex', gap: '12px', marginBottom: '12px' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer' }}>
                    <input type="radio" name="interviewResult" value="COMPLETED" checked={interviewResult === 'COMPLETED'} onChange={() => setInterviewResult('COMPLETED')} />
                    <span style={{ fontWeight: 600, color: '#166534' }}>Đạt (Pass)</span>
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer' }}>
                    <input type="radio" name="interviewResult" value="fail" checked={interviewResult === 'fail'} onChange={() => setInterviewResult('fail')} />
                    <span style={{ fontWeight: 600, color: '#991b1b' }}>Không đạt (Fail)</span>
                  </label>
                </div>
              </div>
            )}

            {targetStatus === 'INTERVIEW_SCHEDULED' && (
              (() => {
                const hasActiveInterview = updatingApp?.interviews?.some((iv: any) => {
                  if (iv.status === 'ACCEPTED' || iv.status === 'RESCHEDULE_REQUESTED') return true;
                  if ((iv.status === 'SCHEDULED' || iv.status === 'PENDING_RESPONSE') && iv.scheduledAt) {
                    return new Date(iv.scheduledAt).getTime() > Date.now();
                  }
                  return false;
                });

                if (hasActiveInterview) {
                  return (
                    <div style={{ background: '#fffbeb', padding: '16px', borderRadius: '8px', border: '1px solid #fde68a', marginBottom: '16px' }}>
                      <h4 style={{ margin: '0 0 8px 0', fontSize: '0.95rem', color: '#92400e' }}>Đã có lịch phỏng vấn</h4>
                      <p style={{ margin: 0, fontSize: '0.85rem', color: '#b45309' }}>Hồ sơ này đang có lịch phỏng vấn chưa hoàn tất. Bạn không cần tạo thêm lịch mới lúc này. Vui lòng sử dụng tính năng <strong>Quản lý phỏng vấn</strong> ở ngoài danh sách.</p>
                    </div>
                  );
                }

                return (
                  <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '16px' }}>
                    <h4 style={{ margin: '0 0 12px 0', fontSize: '1rem', color: '#0f172a' }}>Thông tin Phỏng vấn</h4>

                    <div style={{ marginBottom: '12px' }}>
                      <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Thời gian (*)</label>
                      <input type="datetime-local" min={new Date(new Date().getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16)} value={scheduledAt} onChange={e => setScheduledAt(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: slotConflictWarning ? '1px solid #ef4444' : '1px solid #cbd5e1' }} />
                      
                      {loadingOccupiedSlots && (
                        <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '4px' }}>⏳ Đang kiểm tra lịch đã bận...</div>
                      )}

                      {!loadingOccupiedSlots && occupiedSlots.length > 0 && (
                        <div style={{ marginTop: '8px', padding: '8px 12px', background: '#fff1f2', borderRadius: '6px', border: '1px solid #fecdd3' }}>
                          <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#9f1239', marginBottom: '6px' }}>
                            📌 Khung giờ đã có lịch phỏng vấn trong ngày ({scheduledAt ? scheduledAt.split('T')[0] : new Date().toISOString().split('T')[0]}):
                          </div>
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                            {occupiedSlots.map((slot) => {
                              const slotDate = new Date(slot.scheduledAt);
                              const timeStr = slotDate.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
                              return (
                                <span key={slot.id} style={{ background: '#fecdd3', color: '#881337', border: '1px solid #fda4af', padding: '2px 8px', borderRadius: '12px', fontSize: '0.75rem', fontWeight: 600 }}>
                                  🔴 {timeStr} ({slot.candidateName})
                                </span>
                              );
                            })}
                          </div>
                        </div>
                      )}

                      {!loadingOccupiedSlots && occupiedSlots.length === 0 && (
                        <div style={{ fontSize: '0.8rem', color: '#166534', marginTop: '4px' }}>✅ Chưa có lịch phỏng vấn nào khác trong ngày này.</div>
                      )}

                      {slotConflictWarning && (
                        <div style={{ marginTop: '8px', background: '#fef2f2', color: '#991b1b', border: '1px solid #fecaca', padding: '8px 12px', borderRadius: '6px', fontSize: '0.85rem', fontWeight: 600 }}>
                          {slotConflictWarning}
                        </div>
                      )}
                    </div>
                    <div style={{ marginBottom: '12px' }}>
                      <VietnamAddressPicker
                        label="Hình thức / Địa điểm phỏng vấn"
                        required
                        allowOnline
                        value={location}
                        onChange={setLocation}
                        detailPlaceholder="Tầng, phòng, tòa nhà, số nhà, tên đường..."
                      />
                    </div>
                    <div style={{ marginBottom: '12px' }}>
                      <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Link họp trực tuyến (Nếu có)</label>
                      <input type="url" placeholder="VD: https://meet.google.com/..." value={meetingLink} onChange={e => setMeetingLink(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }} />
                    </div>
                  </div>
                );
              })()
            )}

            {targetStatus === 'ACCEPTED' && (
              <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '16px', maxHeight: '300px', overflowY: 'auto' }}>
                <h4 style={{ margin: '0 0 12px 0', fontSize: '1rem', color: '#0f172a' }}>Thông tin Job Offer</h4>

                <div style={{ marginBottom: '12px' }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Chức danh (*)</label>
                  <input type="text" value={positionTitle} disabled style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1', background: '#f1f5f9', color: '#64748b' }} />
                </div>
                <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
                  <div style={{ flex: 2 }}>
                    <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Mức lương</label>
                    <input type="number" placeholder="VD: 15000000" value={salary} onChange={e => setSalary(e.target.value ? Number(e.target.value) : '')} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Tiền tệ</label>
                    <select value={salaryCurrency} onChange={e => setSalaryCurrency(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }}>
                      <option value="VND">VND</option>
                      <option value="USD">USD</option>
                    </select>
                  </div>
                  <div style={{ flex: 1 }}>
                    <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Kỳ lương</label>
                    <select value={salaryType} onChange={e => setSalaryType(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }}>
                      <option value="monthly">Tháng</option>
                      <option value="yearly">Năm</option>
                      <option value="negotiable">Thỏa thuận</option>
                    </select>
                  </div>
                </div>
                <div style={{ marginBottom: '12px' }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Ngày bắt đầu</label>
                  <input type="date" min={new Date(new Date().getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)} value={startDate} onChange={e => setStartDate(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }} />
                </div>
                <div style={{ marginBottom: '12px' }}>
                  <VietnamAddressPicker
                    label="Nơi làm việc"
                    allowRemote
                    value={workingLocation}
                    onChange={setWorkingLocation}
                  />
                </div>
                <div style={{ marginBottom: '12px' }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Phúc lợi</label>
                  <textarea rows={2} value={benefits} onChange={e => setBenefits(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }} />
                </div>
                <div style={{ marginBottom: '12px' }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Link Offer Letter (Google Drive / PDF)</label>
                  <input type="url" value={offerLetterUrl} onChange={e => setOfferLetterUrl(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }} />
                </div>
              </div>
            )}

            {targetStatus !== 'UNDER_REVIEW' && (
              <div style={{ marginBottom: '20px' }}>
                <label style={{ display: 'block', fontSize: '0.9rem', fontWeight: 600, color: '#334155', marginBottom: '6px' }}>
                  Ghi chú / Lời nhắn cho ứng viên (Hệ thống sẽ gửi email tự động)
                </label>
                <textarea
                  rows={3}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Lời nhắn kèm theo thông báo..."
                  style={{ width: '100%', padding: '10px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '0.9rem', fontFamily: 'inherit' }}
                />
              </div>
            )}

            {modalError && (
              <div style={{ background: '#fef2f2', color: '#991b1b', padding: '12px 16px', borderRadius: '8px', border: '1px solid #fecaca', marginBottom: '16px', fontSize: '0.95rem', display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 500 }}>
                <span>❌</span> {modalError}
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
              <button
                type="button"
                onClick={() => setUpdatingApp(null)}
                style={{ background: '#f1f5f9', color: '#475569', border: 'none', padding: '10px 18px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer' }}
              >
                Hủy
              </button>
              {targetStatus === 'ACCEPTED' && (
                <button
                  type="button"
                  onClick={() => {
                    setUpdatingApp(null);
                    showToast('Đã lưu nháp. Bạn có thể gửi Offer sau.', 'info');
                  }}
                  style={{ background: '#fef3c7', color: '#92400e', border: 'none', padding: '10px 18px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer' }}
                >
                  Để sau (Lưu nháp)
                </button>
              )}
              <button
                type="button"
                disabled={updating}
                onClick={handleConfirmUpdate}
                style={{ background: '#2563eb', color: '#fff', border: 'none', padding: '10px 20px', borderRadius: '6px', fontWeight: 600, cursor: updating ? 'wait' : 'pointer' }}
              >
                {updating ? 'Đang lưu...' : (targetStatus === 'ACCEPTED' ? 'Gửi Job Offer' : 'Xác nhận & Gửi thông báo')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Candidate Profile Detail Modal */}
      {selectedAppDetail && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(15, 23, 42, 0.65)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 999,
            padding: '20px',
            backdropFilter: 'blur(4px)',
          }}
        >
          <div
            style={{
              background: '#fff',
              borderRadius: '16px',
              width: '100%',
              maxWidth: '850px',
              maxHeight: '90vh',
              display: 'flex',
              flexDirection: 'column',
              boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)',
              overflow: 'hidden',
            }}
          >
            {/* Modal Header */}
            <div
              style={{
                padding: '20px 24px',
                background: '#f8fafc',
                borderBottom: '1px solid #e2e8f0',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '12px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                <div
                  style={{
                    width: '52px',
                    height: '52px',
                    borderRadius: '50%',
                    background: '#2563eb',
                    color: '#fff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 700,
                    fontSize: '1.4rem',
                    boxShadow: '0 4px 6px -1px rgba(37,99,235,0.2)',
                  }}
                >
                  {(selectedAppDetail.candidate?.fullName || 'U').charAt(0).toUpperCase()}
                </div>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                    <h2 style={{ margin: 0, color: '#0f172a', fontSize: '1.35rem' }}>
                      {selectedAppDetail.candidate?.fullName || 'Ứng viên ẩn danh'}
                    </h2>
                    {(() => {
                      const st = getDetailedStatus(selectedAppDetail);
                      return (
                        <span
                          style={{
                            background: st.bg,
                            color: st.color,
                            padding: '4px 12px',
                            borderRadius: '12px',
                            fontSize: '0.8rem',
                            fontWeight: 600,
                          }}
                        >
                          {st.label}
                        </span>
                      );
                    })()}
                  </div>
                  <div style={{ fontSize: '0.9rem', color: '#64748b', marginTop: '4px' }}>
                    💼 Ứng tuyển: <strong style={{ color: '#334155' }}>{selectedAppDetail.job.title}</strong>
                  </div>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setSelectedAppDetail(null)}
                style={{
                  background: '#f1f5f9',
                  color: '#64748b',
                  border: 'none',
                  width: '36px',
                  height: '36px',
                  borderRadius: '50%',
                  fontSize: '1.2rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
                title="Đóng modal"
              >
                ✕
              </button>
            </div>

            {/* Modal Body */}
            <div style={{ padding: '24px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '24px' }}>
              <RecruitmentStageStepper app={selectedAppDetail} />
              {/* Overview Info Cards */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px' }}>
                <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 600 }}>📞 ĐIỆN THOẠI / CONTACT</div>
                  <div style={{ fontSize: '0.95rem', color: '#0f172a', fontWeight: 600, marginTop: '4px' }}>
                    {selectedAppDetail.candidate?.phone || 'Chưa cập nhật'}
                  </div>
                </div>
                <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 600 }}>📍 KHU VỰC / ĐỊA ĐIỂM</div>
                  <div style={{ fontSize: '0.95rem', color: '#0f172a', fontWeight: 600, marginTop: '4px' }}>
                    {selectedAppDetail.candidate?.location || 'Chưa cập nhật'}
                  </div>
                </div>
                <div style={{ background: '#f8fafc', padding: '14px', borderRadius: '10px', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 600 }}>🕒 NGÀY NỘP HỒ SƠ</div>
                  <div style={{ fontSize: '0.95rem', color: '#0f172a', fontWeight: 600, marginTop: '4px' }}>
                    {new Date(selectedAppDetail.submittedAt).toLocaleDateString('vi-VN')}
                  </div>
                </div>
              </div>

              {(selectedAppDetail.preferredLocation || selectedAppDetail.coverLetter) && (
                <div>
                  <h4 style={{ margin: '0 0 10px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                    Thông tin ứng tuyển
                  </h4>
                  <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '10px', border: '1px solid #e2e8f0', color: '#334155', lineHeight: '1.6', fontSize: '0.95rem' }}>
                    {selectedAppDetail.preferredLocation && (
                      <p style={{ margin: '0 0 10px' }}>
                        <strong>Địa điểm làm việc mong muốn:</strong> {selectedAppDetail.preferredLocation}
                      </p>
                    )}
                    {selectedAppDetail.coverLetter && (
                      <div>
                        <strong>Thư giới thiệu:</strong>
                        <p style={{ margin: '6px 0 0', whiteSpace: 'pre-line' }}>{selectedAppDetail.coverLetter}</p>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* AI Match Analysis Section */}
              {(selectedAppDetail.aiMatchScore !== undefined && selectedAppDetail.aiMatchScore !== null) && (
                <div>
                  <h4 style={{ margin: '0 0 10px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                    🤖 Phân tích độ phù hợp bằng AI
                  </h4>
                  <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '10px', border: '1px solid #e2e8f0', color: '#334155', lineHeight: '1.6', fontSize: '0.95rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
                      <strong style={{ color: '#0f172a' }}>Điểm phù hợp tổng thể:</strong>
                      <span style={{
                        background: selectedAppDetail.aiMatchScore >= 80 ? '#dcfce7' : selectedAppDetail.aiMatchScore >= 50 ? '#fef9c3' : '#fee2e2',
                        color: selectedAppDetail.aiMatchScore >= 80 ? '#166534' : selectedAppDetail.aiMatchScore >= 50 ? '#854d0e' : '#991b1b',
                        padding: '4px 10px',
                        borderRadius: '12px',
                        fontSize: '0.95rem',
                        fontWeight: 700,
                      }}>
                        {selectedAppDetail.aiMatchScore}%
                      </span>
                      {selectedAppDetail.needRerank && (
                        <span style={{ fontSize: '0.8rem', color: '#f59e0b', fontStyle: 'italic' }}>
                          (Hồ sơ hoặc yêu cầu đã thay đổi, hệ thống sẽ tự động chấm lại sau ít phút)
                        </span>
                      )}
                    </div>

                    {selectedAppDetail.aiMatchAnalysis && (
                      <div style={{ marginBottom: '16px', whiteSpace: 'pre-line' }}>
                        <strong>Đánh giá chung:</strong><br />
                        {selectedAppDetail.aiMatchAnalysis}
                      </div>
                    )}

                    {selectedAppDetail.scoreBreakdown && Object.keys(selectedAppDetail.scoreBreakdown).length > 0 && (
                      <div style={{ marginBottom: '16px' }}>
                        <strong style={{ display: 'block', marginBottom: '8px' }}>Chi tiết điểm theo tiêu chí:</strong>
                        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                          {Object.entries(selectedAppDetail.scoreBreakdown).map(([key, score]) => (
                            <span key={key} style={{ background: '#e0e7ff', color: '#4338ca', padding: '4px 12px', borderRadius: '6px', fontSize: '0.85rem', fontWeight: 600 }}>
                              {key}: {score}/100
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {selectedAppDetail.missingRequirements && selectedAppDetail.missingRequirements.length > 0 && (
                      <div>
                        <strong style={{ display: 'block', marginBottom: '8px', color: '#b91c1c' }}>⚠️ Các yêu cầu còn thiếu:</strong>
                        <ul style={{ margin: 0, paddingLeft: '20px', color: '#991b1b' }}>
                          {selectedAppDetail.missingRequirements.map((req, idx) => (
                            <li key={idx} style={{ marginBottom: '4px' }}>{req}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Bio Section */}
              <div>
                <h4 style={{ margin: '0 0 10px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                  📖 Giới thiệu bản thân
                </h4>
                <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '10px', border: '1px solid #e2e8f0', color: '#334155', lineHeight: '1.6', fontSize: '0.95rem', whiteSpace: 'pre-line' }}>
                  {selectedAppDetail.candidate?.bio || (selectedAppDetail.cvVersion?.snapshot?.summary as string) || 'Ứng viên chưa cập nhật lời giới thiệu.'}
                </div>
              </div>

              {/* Skills Section */}
              <div>
                <h4 style={{ margin: '0 0 10px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                  🛠️ Kỹ năng chuyên môn
                </h4>
                {(() => {
                  const skillsList = selectedAppDetail.candidate?.skills?.length
                    ? selectedAppDetail.candidate.skills
                    : Array.isArray(selectedAppDetail.cvVersion?.snapshot?.skills)
                    ? (selectedAppDetail.cvVersion.snapshot.skills as string[])
                    : [];
                  return skillsList.length > 0 ? (
                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                      {skillsList.map((sk, idx) => (
                        <span
                          key={idx}
                          style={{
                            background: '#eff6ff',
                            color: '#1d4ed8',
                            border: '1px solid #bfdbfe',
                            padding: '6px 14px',
                            borderRadius: '20px',
                            fontSize: '0.85rem',
                            fontWeight: 600,
                          }}
                        >
                          {sk}
                        </span>
                      ))}
                    </div>
                  ) : (
                    <p style={{ color: '#94a3b8', fontSize: '0.9rem', margin: 0 }}>Chưa ghi nhận kỹ năng nào.</p>
                  );
                })()}
              </div>

              {/* CV Attachment Section */}
              <div>
                <h4 style={{ margin: '0 0 10px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                  📄 Hồ sơ CV đính kèm
                </h4>
                <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '10px', border: '1px solid #e2e8f0', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
                  {selectedAppDetail.cv ? (
                    <>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{ fontSize: '2rem' }}>📎</div>
                        <div>
                          <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>
                            {selectedAppDetail.cv.originalFileName}
                          </div>
                          <div style={{ fontSize: '0.8rem', color: '#64748b' }}>
                            Kích thước: {Math.round(selectedAppDetail.cv.fileSize / 1024)} KB | Tải lên ngày {new Date(selectedAppDetail.cv.createdAt).toLocaleDateString('vi-VN')}
                          </div>
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          openApplicationCv(selectedAppDetail.id);
                        }}
                        style={{
                          background: '#fff',
                          color: '#2563eb',
                          border: '1px solid #bfdbfe',
                          padding: '6px 12px',
                          borderRadius: '6px',
                          fontWeight: 600,
                          fontSize: '0.85rem',
                          cursor: 'pointer',
                        }}
                      >
                        Xem CV
                      </button>
                    </>
                  ) : selectedAppDetail.cvVersion ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <div style={{ fontSize: '2rem' }}>📝</div>
                      <div>
                        <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>
                          {selectedAppDetail.cvVersion.title} (CV Online)
                        </div>
                        <div style={{ fontSize: '0.8rem', color: '#64748b' }}>
                          Mẫu CV: {selectedAppDetail.cvVersion.templateKey} | Cập nhật ngày {new Date(selectedAppDetail.cvVersion.updatedAt).toLocaleDateString('vi-VN')}
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div style={{ color: '#94a3b8', fontSize: '0.9rem' }}>Ứng viên không đính kèm file gốc.</div>
                  )}
                </div>
              </div>

              {/* Education Section */}
              <div>
                <h4 style={{ margin: '0 0 12px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                  🎓 Học vấn & Bằng cấp
                </h4>
                {(() => {
                  const eduList = (selectedAppDetail.candidate?.education && selectedAppDetail.candidate.education.length > 0)
                    ? selectedAppDetail.candidate.education
                    : Array.isArray(selectedAppDetail.cvVersion?.snapshot?.education)
                    ? (selectedAppDetail.cvVersion.snapshot.education as Record<string, unknown>[])
                    : [];
                  return eduList.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                      {eduList.map((item, idx) => {
                        const inst = item.institution || item.school || item.schoolName || item.organization || item.title || item.name || 'Trường / Cơ sở đào tạo';
                        const deg = item.degree || item.major || item.field || (item.title && item.title !== inst ? item.title : '') || (item.organization && item.organization !== inst ? item.organization : '') || '';
                        const field = item.field && item.degree && item.field !== item.degree ? ` - ${item.field}` : '';
                        const start = item.startDate || item.startYear || item.time || '';
                        const end = item.endDate || item.endYear || '';
                        const timeRange = (start || end) ? `🕒 ${[start, end].filter(Boolean).join(' - ')}` : '';
                        const desc = item.description || item.summary || '';
                        return (
                          <div key={idx} style={{ borderLeft: '3px solid #2563eb', paddingLeft: '14px', background: '#f8fafc', padding: '12px 14px', borderRadius: '0 8px 8px 0' }}>
                            <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>{String(inst)}</div>
                            {deg && <div style={{ color: '#334155', fontSize: '0.9rem', marginTop: '2px' }}>{String(deg)}{field}</div>}
                            {timeRange && <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '4px' }}>{timeRange}</div>}
                            {desc && <div style={{ color: '#475569', fontSize: '0.88rem', marginTop: '6px', whiteSpace: 'pre-line' }}>{String(desc)}</div>}
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p style={{ color: '#94a3b8', fontSize: '0.9rem', margin: 0 }}>Chưa có thông tin học vấn.</p>
                  );
                })()}
              </div>

              {/* Work Experience Section */}
              <div>
                <h4 style={{ margin: '0 0 12px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                  💼 Kinh nghiệm làm việc
                </h4>
                {(() => {
                  const experienceSnapshot = selectedAppDetail.cvVersion?.snapshot;
                  const snapshotExperiences = experienceSnapshot?.workExperience || experienceSnapshot?.experience;
                  const expList = (selectedAppDetail.candidate?.workExperience && selectedAppDetail.candidate.workExperience.length > 0)
                    ? selectedAppDetail.candidate.workExperience
                    : Array.isArray(snapshotExperiences)
                    ? (snapshotExperiences as Record<string, unknown>[])
                    : [];
                  return expList.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                      {expList.map((item, idx) => {
                        const comp = item.company || item.companyName || item.organization || item.title || item.name || 'Công ty / Tổ chức';
                        const pos = item.position || item.role || item.jobTitle || (item.title && item.title !== comp ? item.title : '') || (item.organization && item.organization !== comp ? item.organization : '') || '';
                        const start = item.startDate || item.startYear || item.time || '';
                        const end = item.endDate || item.endYear || '';
                        const timeRange = (start || end) ? `🕒 ${[start, end].filter(Boolean).join(' - ')}` : '';
                        const desc = item.description || item.summary || '';
                        return (
                          <div key={idx} style={{ borderLeft: '3px solid #10b981', paddingLeft: '14px', background: '#f8fafc', padding: '12px 14px', borderRadius: '0 8px 8px 0' }}>
                            <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>{String(comp)}</div>
                            {pos && <div style={{ color: '#10b981', fontWeight: 600, fontSize: '0.9rem', marginTop: '2px' }}>{String(pos)}</div>}
                            {timeRange && <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '4px' }}>{timeRange}</div>}
                            {desc && <div style={{ color: '#334155', fontSize: '0.88rem', marginTop: '6px', whiteSpace: 'pre-line' }}>{String(desc)}</div>}
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p style={{ color: '#94a3b8', fontSize: '0.9rem', margin: 0 }}>Chưa có thông tin kinh nghiệm làm việc.</p>
                  );
                })()}
              </div>

              {/* Projects Section */}
              <div>
                <h4 style={{ margin: '0 0 12px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                  🚀 Dự án đã thực hiện
                </h4>
                {(() => {
                  const projList = (selectedAppDetail.candidate?.projects && selectedAppDetail.candidate.projects.length > 0)
                    ? selectedAppDetail.candidate.projects
                    : Array.isArray(selectedAppDetail.cvVersion?.snapshot?.projects)
                    ? (selectedAppDetail.cvVersion.snapshot.projects as Record<string, unknown>[])
                    : [];
                  return projList.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                      {projList.map((item, idx) => {
                        const name = item.name || item.projectName || item.title || 'Tên dự án';
                        const role = item.role || item.position || item.organization || '';
                        const time = item.time || item.startDate || (item.startYear ? [item.startYear, item.endYear].filter(Boolean).join(' - ') : '');
                        const desc = item.description || item.summary || '';
                        const url = item.url || item.link || item.credentialUrl || '';
                        return (
                          <div key={idx} style={{ borderLeft: '3px solid #8b5cf6', paddingLeft: '14px', background: '#f8fafc', padding: '12px 14px', borderRadius: '0 8px 8px 0' }}>
                            <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>{String(name)} {role ? `(${role})` : ''}</div>
                            {time && <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '4px' }}>🕒 {String(time)}</div>}
                            {desc && <div style={{ color: '#334155', fontSize: '0.88rem', marginTop: '6px', whiteSpace: 'pre-line' }}>{String(desc)}</div>}
                            {url && (
                              <div style={{ marginTop: '6px' }}>
                                <a href={String(url)} target="_blank" rel="noopener noreferrer" style={{ color: '#2563eb', fontSize: '0.85rem', textDecoration: 'underline' }}>
                                  Xem liên kết dự án ↗
                                </a>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p style={{ color: '#94a3b8', fontSize: '0.9rem', margin: 0 }}>Chưa bổ sung thông tin dự án.</p>
                  );
                })()}
              </div>

              {/* Certificates Section */}
              <div>
                <h4 style={{ margin: '0 0 12px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                  🏆 Chứng chỉ & Bằng cấp chuyên môn
                </h4>
                {(() => {
                  const certSnapshot = selectedAppDetail.cvVersion?.snapshot;
                  const snapshotCerts = certSnapshot?.certifications || certSnapshot?.certificates;
                  const certList = (selectedAppDetail.candidate?.certifications && selectedAppDetail.candidate.certifications.length > 0)
                    ? selectedAppDetail.candidate.certifications
                    : ((selectedAppDetail.candidate as any)?.certificates && (selectedAppDetail.candidate as any).certificates.length > 0)
                    ? (selectedAppDetail.candidate as any).certificates
                    : Array.isArray(snapshotCerts)
                    ? (snapshotCerts as Record<string, unknown>[])
                    : [];
                  return certList.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                      {certList.map((item: any, idx: number) => {
                        const name = item.title || item.name || item.certificateName || 'Tên chứng chỉ';
                        const org = item.organization || item.issuer || item.issuedBy || '';
                        const time = item.time || item.issueDate || item.year || '';
                        const desc = item.description || item.summary || '';
                        const url = item.credentialUrl || item.url || '';
                        return (
                          <div key={idx} style={{ borderLeft: '3px solid #eab308', paddingLeft: '14px', background: '#f8fafc', padding: '12px 14px', borderRadius: '0 8px 8px 0' }}>
                            <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>{String(name)}</div>
                            {org && <div style={{ color: '#ca8a04', fontWeight: 600, fontSize: '0.9rem', marginTop: '2px' }}>Tổ chức cấp: {String(org)}</div>}
                            {time && <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '4px' }}>🕒 {String(time)}</div>}
                            {desc && <div style={{ color: '#334155', fontSize: '0.88rem', marginTop: '6px', whiteSpace: 'pre-line' }}>{String(desc)}</div>}
                            {url && (
                              <div style={{ marginTop: '6px' }}>
                                <a href={String(url)} target="_blank" rel="noopener noreferrer" style={{ color: '#2563eb', fontSize: '0.85rem', textDecoration: 'underline' }}>
                                  Xem chứng chỉ ↗
                                </a>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p style={{ color: '#94a3b8', fontSize: '0.9rem', margin: 0 }}>Chưa bổ sung thông tin chứng chỉ.</p>
                  );
                })()}
              </div>

              {selectedAppDetail.interviews && selectedAppDetail.interviews.length > 0 && (
                <div>
                  <h4 style={{ margin: '0 0 12px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                    🗓️ Lịch phỏng vấn
                  </h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {selectedAppDetail.interviews.map((iv: any, idx: number) => {
                      let statusBg = '#f1f5f9';
                      let statusColor = '#475569';
                      let statusText = iv.status;
                      
                      if (iv.status === 'SCHEDULED' || iv.status === 'PENDING_RESPONSE') { statusBg = '#fef3c7'; statusColor = '#92400e'; statusText = 'Chờ ứng viên xác nhận'; }
                      else if (iv.status === 'ACCEPTED') { statusBg = '#dcfce7'; statusColor = '#166534'; statusText = 'Ứng viên đã xác nhận tham gia'; }
                      else if (iv.status === 'DECLINED') { statusBg = '#fee2e2'; statusColor = '#991b1b'; statusText = 'UV Từ chối'; }
                      else if (iv.status === 'RESCHEDULE_REQUESTED') { statusBg = '#ffedd5'; statusColor = '#c2410c'; statusText = 'UV Xin đổi lịch'; }
                      else if (iv.status === 'NO_RESPONSE') { statusBg = '#fee2e2'; statusColor = '#991b1b'; statusText = 'UV Không phản hồi'; }
                      else if (iv.status === 'COMPLETED') { statusBg = '#e0e7ff'; statusColor = '#3730a3'; statusText = 'Đã phỏng vấn xong'; }
                      else if (iv.status === 'NO_SHOW') { statusBg = '#f3f4f6'; statusColor = '#374151'; statusText = 'UV Không đến'; }

                      return (
                        <div key={idx} style={{ background: '#f8fafc', border: '1px solid #e2e8f0', padding: '16px', borderRadius: '10px' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                            <span style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>Lịch phỏng vấn</span>
                            <span style={{ fontSize: '0.8rem', background: statusBg, color: statusColor, padding: '2px 8px', borderRadius: '12px', fontWeight: 600 }}>
                              {statusText}
                            </span>
                          </div>
                          <div style={{ fontSize: '0.9rem', color: '#334155', marginBottom: '4px' }}><strong>Thời gian:</strong> {new Date(iv.scheduledAt).toLocaleString('vi-VN')}</div>
                          <div style={{ fontSize: '0.9rem', color: '#334155', marginBottom: '4px' }}><strong>Hình thức / Địa điểm:</strong> {iv.location}</div>
                          {iv.meetingLink && (
                            <div style={{ fontSize: '0.9rem', color: '#334155', marginBottom: '4px' }}>
                              <strong>Link:</strong> <a href={iv.meetingLink} target="_blank" rel="noreferrer" style={{ color: '#2563eb' }}>{iv.meetingLink}</a>
                            </div>
                          )}
                          <div style={{ fontSize: '0.85rem', color: '#64748b', marginTop: '8px', fontStyle: 'italic' }}>
                            {iv.viewedAt ? `UV đã xem lúc: ${new Date(iv.viewedAt).toLocaleString('vi-VN')}` : 'Ứng viên chưa xem lời mời'}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {selectedAppDetail.jobOffer && (
                <div>
                  <h4 style={{ margin: '0 0 12px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                    🎉 Job Offer
                  </h4>
                  <div style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', padding: '16px', borderRadius: '10px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                      <span style={{ fontWeight: 600, color: '#166534', fontSize: '0.95rem' }}>{selectedAppDetail.jobOffer.positionTitle}</span>
                      <span style={{ fontSize: '0.8rem', background: '#86efac', color: '#14532d', padding: '2px 8px', borderRadius: '12px', fontWeight: 600 }}>
                        {selectedAppDetail.jobOffer.status === 'accepted' ? 'Ứng viên đã nhận' : selectedAppDetail.jobOffer.status === 'rejected' ? 'Ứng viên từ chối' : 'Đã gửi Offer'}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.9rem', color: '#14532d', marginBottom: '4px' }}><strong>Mức lương:</strong> {selectedAppDetail.jobOffer.salary != null ? selectedAppDetail.jobOffer.salary.toLocaleString() : 'Thỏa thuận'} {selectedAppDetail.jobOffer.salaryCurrency}</div>
                    <div style={{ fontSize: '0.9rem', color: '#14532d', marginBottom: '4px' }}><strong>Ngày bắt đầu:</strong> {selectedAppDetail.jobOffer.startDate}</div>
                    {selectedAppDetail.jobOffer.candidateNote && (
                      <div style={{ marginTop: '12px', padding: '10px', background: '#fff', borderRadius: '8px', border: '1px solid #dcfce3', fontSize: '0.85rem', color: '#166534' }}>
                        <strong>Phản hồi của ứng viên:</strong> {selectedAppDetail.jobOffer.candidateNote}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Timeline Section */}
              {selectedAppDetail.timeline && selectedAppDetail.timeline.length > 0 && (
                <div>
                  <h4 style={{ margin: '0 0 12px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                    📈 Lịch sử xử lý hồ sơ (Timeline)
                  </h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {selectedAppDetail.timeline.map((t, idx) => {
                      const toSt = statusConfig[t.toStatus] || { label: t.toStatus, color: '#475569', bg: '#f1f5f9' };
                      const fromSt = t.fromStatus ? (statusConfig[t.fromStatus] || { label: t.fromStatus }) : null;
                      const isSameStatus = idx > 0 && selectedAppDetail.timeline[idx - 1].toStatus === t.toStatus;

                      let displayNote = t.publicNote;
                      if (displayNote) {
                        const rescheduleMatch = displayNote.match(/(?:Nhà tuyển dụng phản hồi đổi lịch:\s*)?Chấp nhận đổi lịch phỏng vấn mới\s*\(Lịch cũ:\s*([^\-]+?)\s*->\s*Lịch mới:\s*([^)]+)\)/i);
                        if (rescheduleMatch) {
                          displayNote = `Đã đổi lịch phỏng vấn từ ${rescheduleMatch[1].trim()} thành ${rescheduleMatch[2].trim()}`;
                        } else if (displayNote.toLowerCase().includes('yêu cầu đổi lịch phỏng vấn')) {
                          if (!displayNote.includes('Lý do:') && selectedAppDetail.interviews && selectedAppDetail.interviews.length > 0) {
                            const latestIv = selectedAppDetail.interviews[selectedAppDetail.interviews.length - 1];
                            if (latestIv && latestIv.candidateRescheduleNote) {
                              displayNote = `Ứng viên đã yêu cầu đổi lịch phỏng vấn (Lý do: ${latestIv.candidateRescheduleNote})`;
                            } else {
                              displayNote = 'Ứng viên đã yêu cầu đổi lịch phỏng vấn';
                            }
                          }
                        } else if (displayNote.toLowerCase().includes('từ chối đổi lịch phỏng vấn')) {
                          if (!displayNote.includes('Lý do:') && selectedAppDetail.interviews && selectedAppDetail.interviews.length > 0) {
                            const latestIv = selectedAppDetail.interviews[selectedAppDetail.interviews.length - 1];
                            if (latestIv && latestIv.employerRescheduleNote) {
                              displayNote = `Nhà tuyển dụng phản hồi đổi lịch: Từ chối đổi lịch phỏng vấn (Lý do: ${latestIv.employerRescheduleNote})`;
                            }
                          }
                        } else if (
                          displayNote === 'Đã lên lịch phỏng vấn' &&
                          selectedAppDetail.interviews && selectedAppDetail.interviews.length > 0
                        ) {
                          const latestIv = selectedAppDetail.interviews[selectedAppDetail.interviews.length - 1];
                          if (latestIv && latestIv.scheduledAt) {
                            const ivTimeStr = new Date(latestIv.scheduledAt).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' });
                            displayNote += ` (Thời gian: ${ivTimeStr})`;
                          }
                        }
                      }

                      return (
                        <div key={idx} style={{ display: 'flex', alignItems: 'flex-start', gap: '12px', background: '#f8fafc', padding: '10px 14px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                          <div style={{ width: '130px', flexShrink: 0, display: 'flex', justifyContent: 'flex-end', paddingTop: '2px' }}>
                            {!isSameStatus ? (
                              <span style={{ background: toSt.bg, color: toSt.color, padding: '4px 10px', borderRadius: '12px', fontSize: '0.75rem', fontWeight: 600, whiteSpace: 'nowrap' }}>
                                {fromSt && fromSt.label !== toSt.label ? `${fromSt.label} ➔ ${toSt.label}` : toSt.label}
                              </span>
                            ) : (
                              <span style={{ color: toSt.color, fontSize: '0.8rem', opacity: 0.7, fontWeight: 600 }}>
                                ↳
                              </span>
                            )}
                          </div>
                          <div style={{ flex: 1 }}>
                            {displayNote && <div style={{ fontSize: '0.88rem', color: '#334155', marginBottom: '4px' }}>💬 {displayNote}</div>}
                            <div style={{ fontSize: '0.78rem', color: '#94a3b8', display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <span>🕒 Cập nhật lúc: {new Date(t.createdAt).toLocaleString('vi-VN')}</span>
                              {t.publicNote && t.publicNote.toLowerCase().includes('job offer') && (
                                <button
                                  type="button"
                                  className="button outline"
                                  style={{ padding: '2px 8px', fontSize: '0.75rem', borderRadius: '4px', height: 'auto', minHeight: 'auto', borderColor: '#2563eb', color: '#2563eb' }}
                                  onClick={() => setManageOfferApp(selectedAppDetail)}
                                >
                                  📄 Xem Offer
                                </button>
                              )}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>

            {/* Modal Footer / Action Bar */}
            <div
              style={{
                padding: '16px 24px',
                background: '#f8fafc',
                borderTop: '1px solid #e2e8f0',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '12px',
              }}
            >
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                {(selectedAppDetail.status === 'SUBMITTED' || selectedAppDetail.status === 'UNDER_REVIEW' || selectedAppDetail.status === 'INTERVIEW_SCHEDULED') && !selectedAppDetail.interviews?.some((iv: any) => iv.status === 'SCHEDULED' || iv.status === 'PENDING_RESPONSE' || iv.status === 'ACCEPTED' || iv.status === 'RESCHEDULE_REQUESTED' || iv.status === 'COMPLETED') && !isInterviewOnly && (
                  <button
                    type="button"
                    onClick={() => openUpdateModal(selectedAppDetail, 'INTERVIEW_SCHEDULED')}
                    style={{ background: '#fef3c7', color: '#b45309', border: '1px solid #fde68a', padding: '10px 14px', borderRadius: '6px', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
                  >
                    Lên lịch phỏng vấn
                  </button>
                )}

                {selectedAppDetail.interviews && selectedAppDetail.interviews.length > 0 && !selectedAppDetail.interviews?.some((iv: any) => iv.status === 'COMPLETED') && (
                  <button
                    type="button"
                    onClick={() => { setManageInterviewApp(selectedAppDetail); setSelectedAppDetail(null); }}
                    style={{ background: '#e0e7ff', color: '#4338ca', border: '1px solid #c7d2fe', padding: '10px 14px', borderRadius: '6px', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
                  >
                    🎤 Quản lý Phỏng vấn
                  </button>
                )}

                {selectedAppDetail.status === 'INTERVIEW_SCHEDULED' && selectedAppDetail.interviews?.some((iv: any) => iv.status === 'COMPLETED') && (
                  <button
                    type="button"
                    onClick={() => openUpdateModal(selectedAppDetail, 'ACCEPTED')}
                    style={{ background: '#d1fae5', color: '#047857', border: '1px solid #a7f3d0', padding: '10px 14px', borderRadius: '6px', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
                  >
                    Gửi Lời mời làm việc (Offer)
                  </button>
                )}

                {selectedAppDetail.status === 'ACCEPTED' && selectedAppDetail.jobOffer && (
                  <button
                    type="button"
                    onClick={() => { setManageOfferApp(selectedAppDetail); setSelectedAppDetail(null); }}
                    style={{ background: '#10b981', color: '#fff', border: '1px solid #059669', padding: '10px 14px', borderRadius: '6px', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
                  >
                    💼 Quản lý Offer
                  </button>
                )}

                {selectedAppDetail.status !== 'REJECTED' && selectedAppDetail.status !== 'ACCEPTED' && (
                  <button
                    type="button"
                    onClick={() => openUpdateModal(selectedAppDetail, 'REJECTED')}
                    style={{ background: '#fee2e2', color: '#b91c1c', border: '1px solid #fecaca', padding: '10px 14px', borderRadius: '6px', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
                  >
                    Từ chối ứng viên
                  </button>
                )}
              </div>

              <button
                type="button"
                onClick={() => setSelectedAppDetail(null)}
                style={{ background: '#e2e8f0', color: '#334155', border: 'none', padding: '10px 20px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer' }}
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Job Offer Management Modal */}
      {manageOfferApp && manageOfferApp.jobOffer && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(15, 23, 42, 0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '20px' }}>
          <div style={{ background: '#fff', borderRadius: '12px', padding: '0', width: '100%', maxWidth: '600px', maxHeight: '90vh', display: 'flex', flexDirection: 'column', boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)' }}>

            <div style={{ padding: '20px 24px', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f8fafc', borderRadius: '12px 12px 0 0' }}>
              <div>
                <h2 style={{ margin: 0, color: '#0f172a', fontSize: '1.25rem' }}>💼 Quản lý Lời mời làm việc</h2>
                <div style={{ fontSize: '0.9rem', color: '#64748b', marginTop: '4px' }}>Ứng viên: <strong>{manageOfferApp.candidate?.fullName}</strong></div>
              </div>
              <button onClick={() => setManageOfferApp(null)} style={{ background: 'transparent', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: '#64748b' }}>×</button>
            </div>

            <div style={{ padding: '24px', overflowY: 'auto', flex: 1 }}>
              <div style={{ background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '8px', padding: '16px', marginBottom: '20px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                  <h4 style={{ margin: 0, color: '#b45309' }}>Chi tiết Offer</h4>
                  <span style={{ fontSize: '0.8rem', background: manageOfferApp.jobOffer.status === 'accepted' ? '#dcfce7' : manageOfferApp.jobOffer.status === 'rejected' ? '#fee2e2' : '#fef3c7', color: manageOfferApp.jobOffer.status === 'accepted' ? '#166534' : manageOfferApp.jobOffer.status === 'rejected' ? '#b91c1c' : '#b45309', padding: '4px 10px', borderRadius: '12px', fontWeight: 600 }}>
                    {manageOfferApp.jobOffer.status === 'accepted' ? 'Hoàn tất (Đã gửi Offer)' : manageOfferApp.jobOffer.status === 'rejected' ? 'Bị từ chối' : 'Chờ phản hồi'}
                  </span>
                </div>

                <div className="responsive-two-col" style={{ fontSize: '0.9rem', color: '#334155' }}>
                  <div><strong>Vị trí:</strong> {manageOfferApp.jobOffer.positionTitle}</div>
                  <div><strong>Mức lương:</strong> {manageOfferApp.jobOffer.salary ? `${manageOfferApp.jobOffer.salary.toLocaleString()} ${manageOfferApp.jobOffer.salaryCurrency}` : 'Thỏa thuận'}</div>
                  <div><strong>Ngày bắt đầu:</strong> {manageOfferApp.jobOffer.startDate || 'Chưa rõ'}</div>
                  <div><strong>Nơi làm việc:</strong> {manageOfferApp.jobOffer.workingLocation || 'Theo công ty'}</div>
                </div>
                {manageOfferApp.jobOffer.offerLetterUrl && (
                  <div style={{ marginTop: '12px', fontSize: '0.9rem' }}>
                    <strong>Link thư mời:</strong> <a href={manageOfferApp.jobOffer.offerLetterUrl} target="_blank" rel="noreferrer" style={{ color: '#2563eb' }}>Xem thư mời</a>
                  </div>
                )}
              </div>

              {manageOfferApp.jobOffer.candidateNote && (
                <div style={{ background: manageOfferApp.jobOffer.status === 'accepted' ? '#f0fdf4' : '#fef2f2', padding: '16px', borderRadius: '8px', border: `1px solid ${manageOfferApp.jobOffer.status === 'accepted' ? '#bbf7d0' : '#fecaca'}` }}>
                  <h4 style={{ margin: '0 0 8px 0', color: manageOfferApp.jobOffer.status === 'accepted' ? '#166534' : '#991b1b', fontSize: '0.95rem' }}>
                    Phản hồi từ ứng viên
                  </h4>
                  <div style={{ fontSize: '0.9rem', color: '#475569' }}>
                    {manageOfferApp.jobOffer.candidateNote}
                  </div>
                </div>
              )}
            </div>

            <div style={{ padding: '16px 24px', borderTop: '1px solid #e2e8f0', background: '#f8fafc', borderRadius: '0 0 12px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                {manageOfferApp.jobOffer.status === 'rejected' && (
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button onClick={() => { openUpdateModal(manageOfferApp, 'UPDATE_OFFER'); setManageOfferApp(null); }} style={{ background: '#3b82f6', color: '#fff', border: 'none', padding: '8px 16px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer' }}>Sửa Offer</button>
                    <button onClick={() => { openUpdateModal(manageOfferApp, 'DECLINE_OFFER_NEGOTIATION'); setManageOfferApp(null); }} style={{ background: '#fee2e2', color: '#b91c1c', border: '1px solid #fecaca', padding: '8px 16px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer' }}>Từ chối (Giữ nguyên)</button>
                  </div>
                )}
              </div>
              <button onClick={() => setManageOfferApp(null)} style={{ background: '#e2e8f0', color: '#334155', border: 'none', padding: '10px 20px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer' }}>Đóng</button>
            </div>
          </div>
        </div>
      )}

      {/* Interview Management Modal */}
      {manageInterviewApp && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(15, 23, 42, 0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '20px' }}>
          <div style={{ background: '#fff', borderRadius: '12px', padding: '0', width: '100%', maxWidth: '700px', maxHeight: '90vh', display: 'flex', flexDirection: 'column', boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)' }}>

            {/* Header */}
            <div style={{ padding: '20px 24px', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f8fafc', borderRadius: '12px 12px 0 0' }}>
              <div>
                <h2 style={{ margin: 0, color: '#0f172a', fontSize: '1.25rem' }}>🎤 Quản lý Phỏng vấn</h2>
                <div style={{ fontSize: '0.9rem', color: '#64748b', marginTop: '4px' }}>Ứng viên: <strong>{manageInterviewApp.candidate?.fullName}</strong></div>
              </div>
              <button onClick={() => setManageInterviewApp(null)} style={{ background: 'transparent', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: '#64748b' }}>×</button>
            </div>

            {/* Body */}
            <div style={{ padding: '24px', overflowY: 'auto', flex: 1 }}>
              {manageInterviewApp.interviews && manageInterviewApp.interviews.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  {manageInterviewApp.interviews.map((iv, idx) => {
                    let statusBg = '#f1f5f9';
                    let statusColor = '#475569';
                    let statusText = iv.status || '';
                    let isUnrespondedPast = (iv.status === 'SCHEDULED' || iv.status === 'PENDING_RESPONSE') && iv.scheduledAt && new Date(iv.scheduledAt).getTime() <= Date.now();
                    
                    if (iv.status === 'NO_RESPONSE' || isUnrespondedPast) { statusBg = '#fee2e2'; statusColor = '#991b1b'; statusText = 'UV Không phản hồi'; }
                    else if (iv.status === 'SCHEDULED' || iv.status === 'PENDING_RESPONSE') { statusBg = '#fef3c7'; statusColor = '#92400e'; statusText = 'Chờ ứng viên xác nhận'; }
                    else if (iv.status === 'ACCEPTED') { statusBg = '#dcfce7'; statusColor = '#166534'; statusText = 'Ứng viên đã xác nhận tham gia'; }
                    else if (iv.status === 'DECLINED') { statusBg = '#fee2e2'; statusColor = '#991b1b'; statusText = 'UV Từ chối'; }
                    else if (iv.status === 'RESCHEDULE_REQUESTED') { statusBg = '#ffedd5'; statusColor = '#c2410c'; statusText = 'UV Xin đổi lịch'; }
                    else if (iv.status === 'COMPLETED') { statusBg = '#e0e7ff'; statusColor = '#3730a3'; statusText = 'Đã phỏng vấn xong'; }
                    else if (iv.status === 'NO_SHOW') { statusBg = '#f3f4f6'; statusColor = '#374151'; statusText = 'UV Không đến'; }

                    return (
                      <div key={idx} style={{ background: '#fff7ed', border: '1px solid #fed7aa', padding: '16px', borderRadius: '10px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                          <span style={{ fontWeight: 600, color: '#9a3412', fontSize: '1rem' }}>Lịch phỏng vấn</span>
                          <span style={{ fontSize: '0.8rem', background: statusBg, color: statusColor, padding: '4px 10px', borderRadius: '12px', fontWeight: 600 }}>
                            {statusText}
                          </span>
                        </div>

                        <div className="responsive-two-col" style={{ marginBottom: '16px', background: '#fff', padding: '12px', borderRadius: '8px', border: '1px solid #ffedd5' }}>
                          <div style={{ fontSize: '0.9rem', color: '#431407' }}><strong>🕒 Thời gian:</strong> {new Date(iv.scheduledAt).toLocaleString('vi-VN')}</div>
                          {iv.location && <div style={{ fontSize: '0.9rem', color: '#431407' }}><strong>📍 Địa điểm:</strong> {iv.location}</div>}
                          {iv.meetingLink && <div style={{ fontSize: '0.9rem', color: '#431407', gridColumn: '1 / -1' }}><strong>🔗 Link họp:</strong> <a href={iv.meetingLink} target="_blank" rel="noreferrer" style={{ color: '#2563eb' }}>Tham gia ngay</a></div>}
                        </div>
                        <div style={{ fontSize: '0.85rem', color: '#b45309', marginBottom: '16px', fontStyle: 'italic' }}>
                          {iv.viewedAt ? `UV đã xem lúc: ${new Date(iv.viewedAt).toLocaleString('vi-VN')}` : 'Ứng viên chưa xem lời mời'}
                        </div>

                        {iv.status === 'RESCHEDULE_REQUESTED' && (
                          <div style={{ marginTop: '12px', padding: '12px', background: '#fee2e2', borderRadius: '8px', border: '1px solid #fca5a5' }}>
                            <div style={{ fontWeight: 600, color: '#991b1b', marginBottom: '4px', fontSize: '0.9rem' }}>⚠️ Ứng viên xin đổi lịch</div>
                            <div style={{ color: '#7f1d1d', fontSize: '0.85rem', marginBottom: '12px' }}><strong>Lý do/Đề xuất:</strong> {iv.candidateRescheduleNote}</div>
                            {rescheduleInterviewId === iv.id ? (
                              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', background: '#fff', padding: '12px', borderRadius: '6px', border: '1px solid #e5e7eb' }}>
                                <label style={{ fontSize: '0.85rem', fontWeight: 600, color: '#374151' }}>Chọn Ngày/Giờ mới:</label>
                                <input
                                  type="datetime-local"
                                  value={rescheduleDate}
                                  onChange={(e) => setRescheduleDate(e.target.value)}
                                  style={{ padding: '8px', border: '1px solid #d1d5db', borderRadius: '4px' }}
                                />
                                <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                                  <button
                                    onClick={async () => {
                                      if (!rescheduleDate) {
                                        showToast('Vui lòng chọn ngày/giờ mới', 'error');
                                        return;
                                      }
                                      const scheduledAtIso = new Date(rescheduleDate).toISOString();
                                      employerService.employerRespondToReschedule(iv.id, 'accept_reschedule', 'Đồng ý đổi lịch', scheduledAtIso).then(async () => {
                                        showToast('Đã chốt lịch mới thành công!', 'success');
                                        setRescheduleInterviewId(null);
                                        await loadApplications();
                                        try {
                                          const freshApp = await employerService.getApplicationDetail(manageInterviewApp.id);
                                          setManageInterviewApp(freshApp);
                                        } catch(e) {}
                                      }).catch(console.error);
                                    }}
                                    style={{ background: '#10b981', color: '#fff', border: 'none', padding: '6px 12px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.8rem' }}
                                  >
                                    Xác nhận
                                  </button>
                                  <button
                                    onClick={() => {
                                      setRescheduleInterviewId(null);
                                      setRescheduleDate('');
                                    }}
                                    style={{ background: '#e5e7eb', color: '#4b5563', border: 'none', padding: '6px 12px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.8rem' }}
                                  >
                                    Hủy
                                  </button>
                                </div>
                              </div>
                            ) : rejectInterviewId === iv.id ? (
                              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', background: '#fff', padding: '12px', borderRadius: '6px', border: '1px solid #e5e7eb' }}>
                                <label style={{ fontSize: '0.85rem', fontWeight: 600, color: '#374151' }}>Lý do từ chối đổi lịch:</label>
                                <textarea
                                  value={rejectReason}
                                  onChange={(e) => setRejectReason(e.target.value)}
                                  placeholder="Nhập lý do từ chối..."
                                  style={{ padding: '8px', border: '1px solid #d1d5db', borderRadius: '4px', minHeight: '60px', fontFamily: 'inherit', fontSize: '0.9rem' }}
                                />
                                <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                                  <button
                                    onClick={async () => {
                                      if (!rejectReason.trim()) {
                                        showToast('Vui lòng nhập lý do từ chối', 'error');
                                        return;
                                      }
                                      employerService.employerRespondToReschedule(iv.id, 'reject_reschedule', rejectReason).then(async () => {
                                        showToast('Đã từ chối yêu cầu đổi lịch!', 'success');
                                        setRejectInterviewId(null);
                                        setRejectReason('');
                                        await loadApplications();
                                        try {
                                          const freshApp = await employerService.getApplicationDetail(manageInterviewApp.id);
                                          setManageInterviewApp(freshApp);
                                        } catch(e) {}
                                      }).catch(console.error);
                                    }}
                                    style={{ background: '#ef4444', color: '#fff', border: 'none', padding: '6px 12px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.8rem' }}
                                  >
                                    Xác nhận từ chối
                                  </button>
                                  <button
                                    onClick={() => {
                                      setRejectInterviewId(null);
                                      setRejectReason('');
                                    }}
                                    style={{ background: '#e5e7eb', color: '#4b5563', border: 'none', padding: '6px 12px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.8rem' }}
                                  >
                                    Hủy
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <div style={{ display: 'flex', gap: '8px' }}>
                                <button
                                  onClick={() => setRescheduleInterviewId(iv.id)}
                                  style={{ background: '#10b981', color: '#fff', border: 'none', padding: '6px 12px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.8rem' }}
                                >
                                  Đồng ý đổi lịch
                                </button>
                                <button
                                  onClick={() => setRejectInterviewId(iv.id)}
                                  style={{ background: '#ef4444', color: '#fff', border: 'none', padding: '6px 12px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.8rem' }}
                                >
                                  Từ chối
                                </button>
                              </div>
                            )}
                          </div>
                        )}

                        {/* Check if we should allow evaluation */}
                        {(iv.status === 'SCHEDULED' || iv.status === 'ACCEPTED' || iv.status === 'PENDING_RESPONSE' || iv.status === 'NO_RESPONSE') && (
                          <div style={{ marginTop: '16px', paddingTop: '16px', borderTop: '1px dashed #fdba74' }}>
                            {iv.status === 'NO_RESPONSE' || isUnrespondedPast ? (
                              <div style={{ color: '#991b1b', fontSize: '0.85rem', fontStyle: 'italic', textAlign: 'center', background: '#fef2f2', padding: '12px', borderRadius: '8px', border: '1px solid #fecaca', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
                                <span>⚠️ Ứng viên không phản hồi lịch phỏng vấn đúng hạn (Đã qua giờ phỏng vấn).</span>
                                <button
                                  type="button"
                                  onClick={() => {
                                    openUpdateModal(manageInterviewApp, 'INTERVIEW_SCHEDULED');
                                    setManageInterviewApp(null);
                                  }}
                                  style={{ background: '#2563eb', color: '#fff', border: 'none', padding: '6px 14px', borderRadius: '6px', fontWeight: 600, fontSize: '0.8rem', cursor: 'pointer', fontStyle: 'normal' }}
                                >
                                  📅 Lên lịch phỏng vấn mới
                                </button>
                              </div>
                            ) : iv.status !== 'ACCEPTED' ? (
                              <div style={{ color: '#b45309', fontSize: '0.85rem', fontStyle: 'italic', textAlign: 'center' }}>
                                ⏳ Ứng viên chưa xác nhận lịch phỏng vấn...
                              </div>
                            ) : new Date(iv.scheduledAt).getTime() > Date.now() ? (
                              <div style={{ background: '#fffbeb', color: '#b45309', padding: '12px 14px', borderRadius: '8px', border: '1px solid #fde68a', fontSize: '0.85rem', textAlign: 'center', lineHeight: 1.5 }}>
                                ⏳ <strong>Chưa đến giờ phỏng vấn</strong> (Lịch hẹn: <strong>{new Date(iv.scheduledAt).toLocaleString('vi-VN')}</strong>).
                                <br />
                                Bạn chỉ có thể đánh giá kết quả phỏng vấn sau khi thời gian hẹn phỏng vấn bắt đầu.
                              </div>
                            ) : !isInterviewOnly ? (
                              <button
                                onClick={() => { setEvaluatingInterviewId(iv.id); setManageInterviewApp(null); openUpdateModal(manageInterviewApp, 'EVALUATE_INTERVIEW'); }}
                                style={{ background: '#3b82f6', color: '#fff', border: 'none', padding: '10px 16px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.9rem', width: '100%' }}
                              >
                                📋 Đánh giá kết quả phỏng vấn
                              </button>
                            ) : null}
                          </div>
                        )}

                        {(iv.status === 'COMPLETED' || iv.status === 'NO_SHOW') && (
                          <div style={{ marginTop: '16px', padding: '16px', background: iv.status === 'COMPLETED' ? '#f0fdf4' : '#fef2f2', borderRadius: '8px', border: `1px solid ${iv.status === 'COMPLETED' ? '#bbf7d0' : '#fecaca'}` }}>
                            <div style={{ fontWeight: 600, color: iv.status === 'COMPLETED' ? '#166534' : '#991b1b', fontSize: '1rem', marginBottom: '8px' }}>
                              Kết quả: {iv.status === 'COMPLETED' ? '🎉 Đạt (Pass)' : '❌ Không đạt (Fail / No Show)'}
                            </div>
                            {iv.note && <div style={{ fontSize: '0.9rem', color: iv.status === 'COMPLETED' ? '#14532d' : '#7f1d1d' }}>Nhận xét: {iv.note}</div>}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div style={{ textAlign: 'center', color: '#64748b', padding: '40px' }}>Chưa có lịch phỏng vấn nào</div>
              )}
            </div>

            {/* Footer */}
            <div style={{ padding: '16px 24px', borderTop: '1px solid #e2e8f0', background: '#f8fafc', borderRadius: '0 0 12px 12px', textAlign: 'right' }}>
              <button onClick={() => setManageInterviewApp(null)} style={{ background: '#e2e8f0', color: '#334155', border: 'none', padding: '10px 20px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer' }}>Đóng</button>
            </div>
          </div>
        </div>
      )}

      {toastMsg && (
        <div
          role="status"
          style={{
            position: 'fixed',
            bottom: '24px',
            right: '24px',
            zIndex: 9999,
            background: toastMsg.type === 'success' ? '#047857' : '#b91c1c',
            color: '#fff',
            padding: '12px 16px',
            borderRadius: '8px',
            boxShadow: '0 8px 24px rgba(15, 23, 42, 0.18)',
            maxWidth: '360px',
            fontWeight: 600,
            fontSize: '0.9rem',
          }}
        >
          {toastMsg.text}
        </div>
      )}
    </section>
  );
}
