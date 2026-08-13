import React, { useCallback, useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { employerService } from '../../services/employerService';
import { billingService, UserSubscription } from '../../services/billingService';
import { customAlert, customConfirm, customPrompt } from '../../utils/dialog';
import type { CandidateApplication } from '../../types/candidateDomain';
import type { Job } from '../../types/job';

const statusConfig: Record<string, { label: string; color: string; bg: string }> = {
  SUBMITTED: { label: 'Mới nộp', color: '#1d4ed8', bg: '#dbeafe' },
  UNDER_REVIEW: { label: 'Đang xem xét', color: '#4338ca', bg: '#e0e7ff' },
  SHORTLISTED: { label: 'Đã rút gọn', color: '#6d28d9', bg: '#ede9fe' },
  INTERVIEW_SCHEDULED: { label: 'Hẹn phỏng vấn', color: '#b45309', bg: '#fef3c7' },
  ACCEPTED: { label: 'Trúng tuyển', color: '#047857', bg: '#d1fae5' },
  REJECTED: { label: 'Từ chối', color: '#b91c1c', bg: '#fee2e2' },
  WITHDRAWN: { label: 'Ứng viên rút', color: '#475569', bg: '#f1f5f9' },
};

export default function EmployerApplicationsPage() {
  const { jobId: routeJobId } = useParams<{ jobId?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryJobId = searchParams.get('jobId') || routeJobId || '';

  const [applications, setApplications] = useState<CandidateApplication[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [subscription, setSubscription] = useState<UserSubscription | null>(null);

  // Filters
  const [selectedJobId, setSelectedJobId] = useState<string>(queryJobId);
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [searchKeyword, setSearchKeyword] = useState<string>('');
  const [appliedSearchKeyword, setAppliedSearchKeyword] = useState<string>('');

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


  // Candidate detail modal
  const [selectedAppDetail, setSelectedAppDetail] = useState<CandidateApplication | null>(null);
  const [manageInterviewApp, setManageInterviewApp] = useState<CandidateApplication | null>(null);
  const [manageOfferApp, setManageOfferApp] = useState<CandidateApplication | null>(null);

  // Reschedule Action
  const [rescheduleInterviewId, setRescheduleInterviewId] = useState<string | null>(null);
  const [rescheduleDate, setRescheduleDate] = useState<string>('');

  // Interview Evaluation Action
  const [evaluatingInterviewId, setEvaluatingInterviewId] = useState<string | null>(null);
  const [interviewResult, setInterviewResult] = useState<'COMPLETED' | 'fail'>('COMPLETED');

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 10;

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
    setCurrentPage(1);
    loadApplications();
  }, [selectedJobId, selectedStatus]);

  async function loadJobs() {
    try {
      const [jobList, subData] = await Promise.all([
        employerService.getJobs(),
        billingService.getMySubscription().catch(() => null)
      ]);
      setJobs(jobList);
      setSubscription(subData);
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
        status: selectedStatus || undefined,
        search: appliedSearchKeyword || undefined,
      });
      setApplications(data);
    } catch (err: any) {
      console.error('Failed to load applications:', err);
      const backendMsg = err.response?.data?.message || err.message || 'Không thể tải danh sách ứng viên. Vui lòng thử lại.';
      setError(backendMsg);
    } finally {
      setLoading(false);
    }
  }, [appliedSearchKeyword, selectedJobId, selectedStatus]);

  useEffect(() => {
    void loadApplications();
  }, [loadApplications]);

  // Handle automatic opening of application details from notifications
  useEffect(() => {
    const appId = searchParams.get('appId');
    if (appId && applications.length > 0) {
      const targetApp = applications.find(a => a.id === appId);
      if (targetApp) {
        setSelectedAppDetail(targetApp);
        // Clear the query parameter so it doesn't reopen if the user closes it and the component re-renders
        setSearchParams(prev => {
          prev.delete('appId');
          return prev;
        }, { replace: true });
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
        status: selectedStatus || undefined,
        search: appliedSearchKeyword || undefined,
      }).then(data => setApplications(data)).catch(() => {});
    }, 10000);

    return () => clearInterval(interval);
  }, [applications, appliedSearchKeyword, selectedJobId, selectedStatus]);

  const [bulkRanking, setBulkRanking] = useState(false);
  function handleBulkAiRanking() {
    if (!selectedJobId) return;
    if (!subscription || !subscription.planId) {
      setConfirmDialog({
        message: 'Tính năng Phân tích AI hàng loạt yêu cầu gói dịch vụ nâng cao. Bạn có muốn đi đến trang Nâng cấp gói dịch vụ?',
        onConfirm: () => {
          setConfirmDialog(null);
          window.location.href = '/employer/subscription/plans';
        }
      });
      return;
    }
    setConfirmDialog({
      message: 'Hệ thống sẽ phân tích AI dưới nền. Bạn có muốn tiếp tục?',
      onConfirm: async () => {
        setConfirmDialog(null);
        setBulkRanking(true);
        try {
          setApplications(apps => apps.map(app => {
            if (app.aiMatchScore == null || app.needRerank || app.aiMatchScore < 0) {
              return { ...app, aiMatchScore: -1, needRerank: false };
            }
            return app;
          }));
          await employerService.triggerBulkAiRanking(selectedJobId);
          showToast('Đã bắt đầu phân tích AI', 'success');
        } catch (err: any) {
          showToast(err.response?.data?.message || 'Có lỗi khi phân tích AI', 'error');
          loadApplications();
        } finally {
          setBulkRanking(false);
        }
      }
    });
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
      setError('Khong the mo CV ung tuyen');
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
        if (iStatus === 'ACCEPTED') return { label: 'Sắp phỏng vấn', color: '#0369a1', bg: '#e0f2fe' };
        if (iStatus === 'PENDING_RESPONSE') return { label: 'Chờ UV phản hồi', color: '#b45309', bg: '#fef3c7' };
        if (iStatus === 'NO_RESPONSE') return { label: 'UV không phản hồi', color: '#be123c', bg: '#ffe4e6' };
        if (iStatus === 'RESCHEDULE_REQUESTED' || iStatus === 'DECLINED') return { label: 'UV xin đổi lịch / Từ chối', color: '#be123c', bg: '#ffe4e6' };
        if (iStatus === 'COMPLETED') return { label: 'Đạt (Chờ Offer)', color: '#047857', bg: '#d1fae5' };
        if (iStatus === 'NO_SHOW') return { label: 'UV không đến PV', color: '#991b1b', bg: '#fee2e2' };
      }
      return { label: 'Chờ xếp lịch', color: '#475569', bg: '#f1f5f9' };
    }
    return statusConfig[app.status] || { label: app.status, color: '#475569', bg: '#f1f5f9' };
  }

  function openUpdateModal(app: CandidateApplication, defaultStatus?: string) {
    setUpdatingApp(app);
    setTargetStatus(defaultStatus || app.status || 'UNDER_REVIEW');
    setModalError(null);
    setNote('');
    setScheduledAt('');
    setLocation('');
    setMeetingLink('');
    setPositionTitle(defaultStatus === 'UPDATE_OFFER' && app.jobOffer ? app.jobOffer.positionTitle : app.job.title || '');
    setSalary(defaultStatus === 'UPDATE_OFFER' && app.jobOffer?.salary ? app.jobOffer.salary : '');
    setSalaryCurrency(defaultStatus === 'UPDATE_OFFER' && app.jobOffer?.salaryCurrency ? app.jobOffer.salaryCurrency : 'VND');
    setSalaryType(defaultStatus === 'UPDATE_OFFER' && app.jobOffer?.salaryType ? app.jobOffer.salaryType : 'monthly');
    setStartDate(defaultStatus === 'UPDATE_OFFER' && app.jobOffer?.startDate ? app.jobOffer.startDate : '');
    setBenefits(defaultStatus === 'UPDATE_OFFER' && app.jobOffer?.benefits ? app.jobOffer.benefits : '');
    setWorkingLocation(defaultStatus === 'UPDATE_OFFER' && app.jobOffer?.workingLocation ? app.jobOffer.workingLocation : '');
    setOfferLetterUrl(defaultStatus === 'UPDATE_OFFER' && app.jobOffer?.offerLetterUrl ? app.jobOffer.offerLetterUrl : '');
  }

  async function handleConfirmUpdate() {
    if (!updatingApp) return;
    setUpdating(true);
    setModalError(null);
    try {
      if (targetStatus === 'INTERVIEW_SCHEDULED') {
        if (!scheduledAt) throw new Error('Vui lòng chọn ngày giờ phỏng vấn');
        if (new Date(scheduledAt).getTime() < Date.now()) throw new Error('Ngày giờ phỏng vấn không được ở trong quá khứ');
        if (meetingLink && !/^https?:\/\/.+/.test(meetingLink)) throw new Error('Link họp trực tuyến phải bắt đầu bằng http:// hoặc https://');
        await employerService.scheduleInterview(updatingApp.id, {
          scheduledAt,
          location,
          meetingLink,
          note
        });
      } else if (targetStatus === 'ACCEPTED' || targetStatus === 'UPDATE_OFFER') {
        if (!positionTitle) throw new Error('Vui lòng nhập chức danh');
        if (salary && Number(salary) <= 0) throw new Error('Mức lương phải lớn hơn 0');
        if (startDate && new Date(startDate).getTime() < new Date().setHours(0,0,0,0)) throw new Error('Ngày bắt đầu không được ở trong quá khứ');
        if (offerLetterUrl && !/^https?:\/\/.+/.test(offerLetterUrl)) throw new Error('Link Offer Letter phải bắt đầu bằng http:// hoặc https://');

        const offerData = {
          positionTitle,
          salary: salary ? Number(salary) : undefined,
          salaryCurrency,
          salaryType,
          startDate: startDate || undefined,
          benefits,
          workingLocation,
          offerLetterUrl,
          employerNote: note
        };

        if (targetStatus === 'UPDATE_OFFER') {
           if (!updatingApp.jobOffer) throw new Error('Không tìm thấy Job Offer để sửa');
           await employerService.employerRespondToOfferRejection(updatingApp.jobOffer.id, true, offerData);
        } else {
           await employerService.createJobOffer(updatingApp.id, offerData);
        }
      } else if (targetStatus === 'DECLINE_OFFER_NEGOTIATION') {
         if (!updatingApp.jobOffer) throw new Error('Không tìm thấy Job Offer để thao tác');
         await employerService.employerRespondToOfferRejection(updatingApp.jobOffer.id, false, { employerNote: note } as any);
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
      showToast('Cập nhật trạng thái thành công!', 'success');
    } catch (err: any) {
      const message =
        err?.response?.data?.message || err?.message || 'Có lỗi xảy ra khi cập nhật trạng thái';
      setModalError(message);
    } finally {
      setUpdating(false);
    }
  }

  const currentJob = jobs.find((j) => j.id === selectedJobId);

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
            {currentJob ? `👥 Ứng viên: ${currentJob.title}` : '👥 Tất cả đơn ứng tuyển'}
          </h1>
          <p style={{ color: '#64748b', margin: 0, fontSize: '0.95rem' }}>
            Quản lý hồ sơ ứng viên, xem CV và chuyển đổi trạng thái vòng tuyển dụng
          </p>
        </div>

        {selectedJobId && currentJob?.rankingConfig?.enabled && (
          <div style={{ display: 'flex', gap: '12px' }}>
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
              {bulkRanking ? '⏳ Đang khởi tạo...' : '✨ Phân tích AI tất cả'}
            </button>
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
          <div style={{ flex: '1 1 220px' }}>
            <select
              value={selectedJobId}
              onChange={(e) => {
                setSelectedJobId(e.target.value);
                if (e.target.value) {
                  setSearchParams({ jobId: e.target.value });
                } else {
                  setSearchParams({});
                }
              }}
              style={{ width: '100%', padding: '10px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', background: '#fff' }}
            >
              <option value="">-- Tất cả việc làm --</option>
              {jobs.map((job) => (
                <option key={job.id} value={job.id}>
                  {job.title} ({job.status})
                </option>
              ))}
            </select>
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
        </form>

        {/* Status Tabs */}
        <div style={{ display: 'flex', gap: '8px', marginTop: '16px', overflowX: 'auto', paddingBottom: '4px' }}>
          {[
            { key: '', label: 'Tất cả trạng thái' },
            { key: 'SUBMITTED', label: 'Mới nộp' },
            { key: 'INTERVIEW_SCHEDULED', label: 'Hẹn phỏng vấn' },
            { key: 'ACCEPTED', label: 'Trúng tuyển' },
            { key: 'REJECTED', label: 'Từ chối' },
          ].map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setSelectedStatus(tab.key)}
              style={{
                background: selectedStatus === tab.key ? '#2563eb' : '#fff',
                color: selectedStatus === tab.key ? '#fff' : '#475569',
                border: '1px solid #cbd5e1',
                padding: '6px 14px',
                borderRadius: '20px',
                fontSize: '0.85rem',
                fontWeight: selectedStatus === tab.key ? 600 : 500,
                cursor: 'pointer',
                whiteSpace: 'nowrap',
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>
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
            const filteredApps = applications.filter(app => !selectedStatus || app.status === selectedStatus);
            const totalPages = Math.ceil(filteredApps.length / itemsPerPage);
            return (
              <>
          {[...filteredApps]
            .sort((a, b) => (b.aiMatchScore || 0) - (a.aiMatchScore || 0))
            .slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
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
                  justifyContent: 'space-between',
                  alignItems: 'flex-start',
                  flexWrap: 'wrap',
                  gap: '20px',
                }}
              >
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
                    onClick={() => setSelectedAppDetail(app)}
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
                    {(app.status === 'SUBMITTED' || app.status === 'UNDER_REVIEW' || app.status === 'INTERVIEW_SCHEDULED') && !app.interviews?.some((iv: any) => iv.status === 'PENDING_RESPONSE' || iv.status === 'ACCEPTED' || iv.status === 'RESCHEDULE_REQUESTED' || iv.status === 'COMPLETED') && (
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

                    {app.status === 'INTERVIEW_SCHEDULED' && app.interviews?.some((iv: any) => iv.status === 'COMPLETED') && (
                      <button
                        onClick={() => openUpdateModal(app, 'ACCEPTED')}
                        style={{ flex: 1, background: '#d1fae5', color: '#047857', border: '1px solid #a7f3d0', padding: '6px 10px', borderRadius: '6px', fontWeight: 600, fontSize: '0.75rem', cursor: 'pointer' }}
                      >
                        Gửi Offer
                      </button>
                    )}

                    {app.status === 'ACCEPTED' && app.jobOffer && (
                      <button
                        onClick={() => setManageOfferApp(app)}
                        style={{ flex: 1, background: '#10b981', color: '#fff', border: '1px solid #059669', padding: '6px 10px', borderRadius: '6px', fontWeight: 600, fontSize: '0.75rem', cursor: 'pointer' }}
                      >
                        💼 Quản lý Offer
                      </button>
                    )}
                    {app.status !== 'REJECTED' && app.status !== 'ACCEPTED' && (
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
          <div style={{ background: '#fff', borderRadius: '12px', padding: '24px', width: '100%', maxWidth: '500px', boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)' }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#0f172a', fontSize: '1.25rem' }}>
              {targetStatus === 'UNDER_REVIEW' && 'Duyệt hồ sơ (Đưa vào vòng xem xét)'}
              {targetStatus === 'INTERVIEW_SCHEDULED' && 'Lên lịch phỏng vấn'}
              {targetStatus === 'ACCEPTED' && 'Gửi Lời mời làm việc (Job Offer)'}
              {targetStatus === 'UPDATE_OFFER' && 'Cập nhật Lời mời làm việc (Sửa Offer)'}
              {targetStatus === 'DECLINE_OFFER_NEGOTIATION' && 'Từ chối thay đổi Offer'}
              {targetStatus === 'REJECTED' && 'Từ chối ứng viên'}
              {targetStatus === 'EVALUATE_INTERVIEW' && 'Đánh giá kết quả phỏng vấn'}
              {targetStatus !== 'UNDER_REVIEW' && targetStatus !== 'INTERVIEW_SCHEDULED' && targetStatus !== 'ACCEPTED' && targetStatus !== 'UPDATE_OFFER' && targetStatus !== 'DECLINE_OFFER_NEGOTIATION' && targetStatus !== 'REJECTED' && targetStatus !== 'EVALUATE_INTERVIEW' && 'Thao tác hồ sơ'}
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
              <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '16px' }}>
                <h4 style={{ margin: '0 0 12px 0', fontSize: '1rem', color: '#0f172a' }}>Thông tin Phỏng vấn</h4>

                <div style={{ marginBottom: '12px' }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Thời gian (*)</label>
                  <input type="datetime-local" min={new Date(new Date().getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16)} value={scheduledAt} onChange={e => setScheduledAt(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }} />
                </div>
                <div style={{ marginBottom: '12px' }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Địa điểm</label>
                  <input type="text" placeholder="VD: Tầng 3, Tòa nhà ABC" value={location} onChange={e => setLocation(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }} />
                </div>
                <div style={{ marginBottom: '12px' }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Link họp trực tuyến (Nếu có)</label>
                  <input type="url" placeholder="VD: https://meet.google.com/..." value={meetingLink} onChange={e => setMeetingLink(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }} />
                </div>
              </div>
            )}

            {(targetStatus === 'ACCEPTED' || targetStatus === 'UPDATE_OFFER') && (
              <div style={{ background: '#f8fafc', padding: '16px', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '16px', maxHeight: '300px', overflowY: 'auto' }}>
                <h4 style={{ margin: '0 0 12px 0', fontSize: '1rem', color: '#0f172a' }}>{targetStatus === 'UPDATE_OFFER' ? 'Cập nhật Job Offer' : 'Thông tin Job Offer'}</h4>

                <div style={{ marginBottom: '12px' }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Chức danh (*)</label>
                  <input type="text" value={positionTitle} onChange={e => setPositionTitle(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }} />
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
                  <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>Nơi làm việc</label>
                  <input type="text" value={workingLocation} onChange={e => setWorkingLocation(e.target.value)} style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #cbd5e1' }} />
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
              {(targetStatus === 'ACCEPTED' || targetStatus === 'UPDATE_OFFER') && (
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
                {updating ? 'Đang lưu...' : (targetStatus === 'ACCEPTED' || targetStatus === 'UPDATE_OFFER' ? 'Gửi Job Offer' : 'Xác nhận & Gửi thông báo')}
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
                        const inst = item.institution || item.school || item.schoolName || 'Trường / Cơ sở đào tạo';
                        const deg = item.degree || item.major || item.field || '';
                        const field = item.field && item.degree ? ` - ${item.field}` : '';
                        const start = item.startDate || item.startYear || '';
                        const end = item.endDate || item.endYear || 'Hiện tại';
                        return (
                          <div key={idx} style={{ borderLeft: '3px solid #2563eb', paddingLeft: '14px', background: '#f8fafc', padding: '12px 14px', borderRadius: '0 8px 8px 0' }}>
                            <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>{String(inst)}</div>
                            {deg && <div style={{ color: '#334155', fontSize: '0.9rem', marginTop: '2px' }}>{String(deg)}{field}</div>}
                            {(start || end) && <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '4px' }}>🕒 {String(start)} - {String(end)}</div>}
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
                        const comp = item.company || item.companyName || 'Công ty / Tổ chức';
                        const pos = item.position || item.title || item.role || '';
                        const start = item.startDate || item.startYear || '';
                        const end = item.endDate || item.endYear || 'Hiện tại';
                        const desc = item.description || item.summary || '';
                        return (
                          <div key={idx} style={{ borderLeft: '3px solid #10b981', paddingLeft: '14px', background: '#f8fafc', padding: '12px 14px', borderRadius: '0 8px 8px 0' }}>
                            <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>{String(comp)}</div>
                            {pos && <div style={{ color: '#10b981', fontWeight: 600, fontSize: '0.9rem', marginTop: '2px' }}>{String(pos)}</div>}
                            {(start || end) && <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '4px' }}>🕒 {String(start)} - {String(end)}</div>}
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
              {(() => {
                const projList = (selectedAppDetail.candidate?.projects && selectedAppDetail.candidate.projects.length > 0)
                  ? selectedAppDetail.candidate.projects
                  : Array.isArray(selectedAppDetail.cvVersion?.snapshot?.projects)
                  ? (selectedAppDetail.cvVersion.snapshot.projects as Record<string, unknown>[])
                  : [];
                return projList.length > 0 ? (
                  <div>
                    <h4 style={{ margin: '0 0 12px 0', color: '#0f172a', fontSize: '1.05rem', borderBottom: '2px solid #2563eb', paddingBottom: '6px', display: 'inline-block' }}>
                      🚀 Dự án đã thực hiện
                    </h4>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                      {projList.map((item, idx) => {
                        const name = item.name || item.projectName || item.title || 'Tên dự án';
                        const role = item.role || item.position || '';
                        const desc = item.description || item.summary || '';
                        return (
                          <div key={idx} style={{ borderLeft: '3px solid #8b5cf6', paddingLeft: '14px', background: '#f8fafc', padding: '12px 14px', borderRadius: '0 8px 8px 0' }}>
                            <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>{String(name)} {role ? `(${role})` : ''}</div>
                            {desc && <div style={{ color: '#334155', fontSize: '0.88rem', marginTop: '6px', whiteSpace: 'pre-line' }}>{String(desc)}</div>}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ) : null;
              })()}

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
                      
                      if (iv.status === 'PENDING_RESPONSE') { statusBg = '#fef3c7'; statusColor = '#b45309'; statusText = 'Chờ UV phản hồi'; }
                      else if (iv.status === 'ACCEPTED') { statusBg = '#dcfce7'; statusColor = '#166534'; statusText = 'UV Đã chấp nhận'; }
                      else if (iv.status === 'DECLINED') { statusBg = '#fee2e2'; statusColor = '#991b1b'; statusText = 'UV Từ chối'; }
                      else if (iv.status === 'RESCHEDULE_REQUESTED') { statusBg = '#ffedd5'; statusColor = '#c2410c'; statusText = 'UV Xin đổi lịch'; }
                      else if (iv.status === 'NO_RESPONSE') { statusBg = '#fee2e2'; statusColor = '#991b1b'; statusText = 'UV Không phản hồi'; }
                      else if (iv.status === 'COMPLETED') { statusBg = '#e0e7ff'; statusColor = '#3730a3'; statusText = 'Đã phỏng vấn xong'; }
                      else if (iv.status === 'NO_SHOW') { statusBg = '#f3f4f6'; statusColor = '#374151'; statusText = 'UV Không đến'; }

                      return (
                        <div key={idx} style={{ background: '#f8fafc', border: '1px solid #e2e8f0', padding: '16px', borderRadius: '10px' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                            <span style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>Vòng {iv.roundNumber}</span>
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
                            {iv.viewedAt ? `UV đã xem lúc: ${new Date(iv.viewedAt).toLocaleString('vi-VN')}` : 'Not viewed (UV chưa xem lời mời)'}
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
                            {t.publicNote && <div style={{ fontSize: '0.88rem', color: '#334155', marginBottom: '4px' }}>💬 {t.publicNote}</div>}
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
                              {t.publicNote && t.publicNote.toLowerCase().includes('phỏng vấn') && (
                                <button
                                  type="button"
                                  className="button outline"
                                  style={{ padding: '2px 8px', fontSize: '0.75rem', borderRadius: '4px', height: 'auto', minHeight: 'auto', borderColor: '#c2410c', color: '#c2410c' }}
                                  onClick={() => setManageInterviewApp(selectedAppDetail)}
                                >
                                  🗓️ Xem Lịch
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
                {(selectedAppDetail.status === 'SUBMITTED' || selectedAppDetail.status === 'UNDER_REVIEW' || selectedAppDetail.status === 'INTERVIEW_SCHEDULED') && !selectedAppDetail.interviews?.some((iv: any) => iv.status === 'PENDING_RESPONSE' || iv.status === 'ACCEPTED' || iv.status === 'RESCHEDULE_REQUESTED' || iv.status === 'COMPLETED') && (
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
                  <span style={{ fontSize: '0.8rem', background: manageOfferApp.jobOffer.status === 'accepted' ? '#d1fae5' : manageOfferApp.jobOffer.status === 'rejected' ? '#fee2e2' : '#fef3c7', color: manageOfferApp.jobOffer.status === 'accepted' ? '#047857' : manageOfferApp.jobOffer.status === 'rejected' ? '#b91c1c' : '#b45309', padding: '4px 10px', borderRadius: '12px', fontWeight: 600 }}>
                    {manageOfferApp.jobOffer.status === 'accepted' ? 'Đã đồng ý' : manageOfferApp.jobOffer.status === 'rejected' ? 'Bị từ chối' : 'Chờ phản hồi'}
                  </span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '0.9rem', color: '#334155' }}>
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
                    let statusText = iv.status;
                    
                    if (iv.status === 'PENDING_RESPONSE') { statusBg = '#fef3c7'; statusColor = '#b45309'; statusText = 'Chờ UV phản hồi'; }
                    else if (iv.status === 'ACCEPTED') { statusBg = '#dcfce7'; statusColor = '#166534'; statusText = 'UV Đã chấp nhận'; }
                    else if (iv.status === 'DECLINED') { statusBg = '#fee2e2'; statusColor = '#991b1b'; statusText = 'UV Từ chối'; }
                    else if (iv.status === 'RESCHEDULE_REQUESTED') { statusBg = '#ffedd5'; statusColor = '#c2410c'; statusText = 'UV Xin đổi lịch'; }
                    else if (iv.status === 'NO_RESPONSE') { statusBg = '#fee2e2'; statusColor = '#991b1b'; statusText = 'UV Không phản hồi'; }
                    else if (iv.status === 'COMPLETED') { statusBg = '#e0e7ff'; statusColor = '#3730a3'; statusText = 'Đã phỏng vấn xong'; }
                    else if (iv.status === 'NO_SHOW') { statusBg = '#f3f4f6'; statusColor = '#374151'; statusText = 'UV Không đến'; }

                    return (
                      <div key={idx} style={{ background: '#fff7ed', border: '1px solid #fed7aa', padding: '16px', borderRadius: '10px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                          <span style={{ fontWeight: 600, color: '#9a3412', fontSize: '1rem' }}>Phỏng vấn Vòng {iv.roundNumber}</span>
                          <span style={{ fontSize: '0.8rem', background: statusBg, color: statusColor, padding: '4px 10px', borderRadius: '12px', fontWeight: 600 }}>
                            {statusText}
                          </span>
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '16px', background: '#fff', padding: '12px', borderRadius: '8px', border: '1px solid #ffedd5' }}>
                          <div style={{ fontSize: '0.9rem', color: '#431407' }}><strong>🕒 Thời gian:</strong> {new Date(iv.scheduledAt).toLocaleString('vi-VN')}</div>
                          {iv.location && <div style={{ fontSize: '0.9rem', color: '#431407' }}><strong>📍 Địa điểm:</strong> {iv.location}</div>}
                          {iv.meetingLink && <div style={{ fontSize: '0.9rem', color: '#431407', gridColumn: '1 / -1' }}><strong>🔗 Link họp:</strong> <a href={iv.meetingLink} target="_blank" rel="noreferrer" style={{ color: '#2563eb' }}>Tham gia ngay</a></div>}
                        </div>
                        <div style={{ fontSize: '0.85rem', color: '#b45309', marginBottom: '16px', fontStyle: 'italic' }}>
                          {iv.viewedAt ? `UV đã xem lúc: ${new Date(iv.viewedAt).toLocaleString('vi-VN')}` : 'Not viewed (UV chưa xem lời mời)'}
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
                                        await customAlert('Vui lòng chọn ngày/giờ mới');
                                        return;
                                      }
                                      const scheduledAtIso = new Date(rescheduleDate).toISOString();
                                      employerService.employerRespondToReschedule(iv.id, 'accept_reschedule', 'Đồng ý đổi lịch', scheduledAtIso).then(async () => {
                                        await customAlert('Đã chốt lịch mới thành công!');
                                        setRescheduleInterviewId(null);
                                        const freshApps = await employerService.getApplications({ jobId: selectedJobId || undefined, status: selectedStatus || undefined, search: appliedSearchKeyword || undefined });
                                        setApplications(freshApps);
                                        const freshApp = freshApps.find(a => a.id === manageInterviewApp.id);
                                        if (freshApp) setManageInterviewApp(freshApp);
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
                            ) : (
                              <div style={{ display: 'flex', gap: '8px' }}>
                                <button
                                  onClick={() => setRescheduleInterviewId(iv.id)}
                                  style={{ background: '#10b981', color: '#fff', border: 'none', padding: '6px 12px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.8rem' }}
                                >
                                  Đồng ý đổi lịch
                                </button>
                                <button
                                  onClick={async () => {
                                    const note = await customPrompt('Lý do từ chối đổi lịch:');
                                    if (note) {
                                      employerService.employerRespondToReschedule(iv.id, 'reject_reschedule', note).then(async () => {
                                        await customAlert('Đã từ chối yêu cầu đổi lịch!');
                                        const freshApps = await employerService.getApplications({ jobId: selectedJobId || undefined, status: selectedStatus || undefined, search: appliedSearchKeyword || undefined });
                                        setApplications(freshApps);
                                        const freshApp = freshApps.find(a => a.id === manageInterviewApp.id);
                                        if (freshApp) setManageInterviewApp(freshApp);
                                      }).catch(console.error);
                                    }
                                  }}
                                  style={{ background: '#ef4444', color: '#fff', border: 'none', padding: '6px 12px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.8rem' }}
                                >
                                  Từ chối
                                </button>
                              </div>
                            )}
                          </div>
                        )}

                        {/* Check if we should allow evaluation */}
                        {(iv.status === 'ACCEPTED' || iv.status === 'PENDING_RESPONSE' || iv.status === 'NO_RESPONSE') && (
                          <div style={{ marginTop: '16px', paddingTop: '16px', borderTop: '1px dashed #fdba74' }}>
                            {iv.status !== 'ACCEPTED' ? (
                              <div style={{ color: '#b45309', fontSize: '0.85rem', fontStyle: 'italic', textAlign: 'center' }}>
                                ⏳ Ứng viên chưa xác nhận lịch phỏng vấn...
                              </div>
                            ) : (
                              <button
                                onClick={() => { setEvaluatingInterviewId(iv.id); setManageInterviewApp(null); openUpdateModal(manageInterviewApp, 'EVALUATE_INTERVIEW'); }}
                                style={{ background: '#3b82f6', color: '#fff', border: 'none', padding: '10px 16px', borderRadius: '6px', fontWeight: 600, cursor: 'pointer', fontSize: '0.9rem', width: '100%' }}
                              >
                                📋 Đánh giá kết quả phỏng vấn
                              </button>
                            )}
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
    </section>
  );
}
