import { FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, NavLink, Outlet, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { authService } from './services/authService';
import { candidateService } from './services/candidateService';
import { jobService } from './services/jobService';
import type {
  CandidateApplication,
  CandidateProfile,
  CvFile,
  CvVersion,
  NotificationItem,
  SubscriptionView,
} from './types/candidateDomain';
import type { Job, JobFilters, Recommendation } from './types/job';

const statusLabels: Record<string, string> = {
  SUBMITTED: 'Da nop',
  UNDER_REVIEW: 'Dang xem xet',
  INTERVIEW_SCHEDULED: 'Hen phong van',
  INTERVIEWED: 'Da phong van',
  EVALUATED: 'Da danh gia',
  ACCEPTED: 'Chap nhan',
  REJECTED: 'Tu choi',
  HIRED: 'Da tuyen',
};

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/jobs" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
      <Route path="/select-role" element={<SelectRolePage />} />
      <Route path="/jobs" element={<JobsPage />} />
      <Route path="/jobs/:id" element={<JobDetailPage />} />
      <Route path="/candidate" element={<Protected><CandidateLayout /></Protected>}>
        <Route index element={<CandidateHome />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route path="cvs" element={<CvPage />} />
        <Route path="saved-jobs" element={<SavedJobsPage />} />
        <Route path="applications" element={<ApplicationsPage />} />
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="subscription" element={<SubscriptionPage />} />
      </Route>
    </Routes>
  );
}

function getToken() {
  return localStorage.getItem('token');
}

function formatMoney(value?: number) {
  if (!value) return 'Thoa thuan';
  return new Intl.NumberFormat('vi-VN').format(value) + ' VND';
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Co loi xay ra';
  }
  return 'Co loi xay ra';
}

function Protected({ children }: { children: JSX.Element }) {
  if (!getToken()) return <Navigate to="/login" replace />;
  return children;
}

function Shell({ children }: { children: React.ReactNode }) {
  const token = getToken();
  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" to="/jobs">Smart Recruitment</Link>
        <nav>
          <NavLink to="/jobs">Viec lam</NavLink>
          {token && <NavLink to="/candidate">Candidate</NavLink>}
          {!token && <NavLink to="/login">Dang nhap</NavLink>}
        </nav>
      </header>
      <main>{children}</main>
    </div>
  );
}

function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('candidate.demo@sjp.local');
  const [password, setPassword] = useState('Password123!');
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError('');
    try {
      const response = await authService.login({ email, password });
      if (response.token) localStorage.setItem('token', response.token);
      navigate(response.user.role === 'CANDIDATE' ? '/candidate' : '/jobs');
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <Shell>
      <section className="auth-panel">
        <h1>Dang nhap</h1>
        <form onSubmit={submit} className="form-grid">
          <label>Email<input value={email} onChange={(e) => setEmail(e.target.value)} /></label>
          <label>Mat khau<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
          {error && <p className="error">{error}</p>}
          <button type="submit">Dang nhap</button>
        </form>
        <a className="secondary-action" href="/api/oauth2/authorization/google">Dang nhap voi Google</a>
        <p className="muted">Demo: candidate.demo@sjp.local / Password123!</p>
      </section>
    </Shell>
  );
}

function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'CANDIDATE' | 'EMPLOYER'>('CANDIDATE');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setMessage('');
    setError('');
    try {
      await authService.register({ email, password, role });
      setMessage('Dang ky thanh cong. Vui long kiem tra email de xac minh tai khoan.');
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <Shell>
      <section className="auth-panel">
        <h1>Dang ky</h1>
        <form onSubmit={submit} className="form-grid">
          <label>Email<input value={email} onChange={(e) => setEmail(e.target.value)} /></label>
          <label>Mat khau<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
          <label>Vai tro
            <select value={role} onChange={(e) => setRole(e.target.value as 'CANDIDATE' | 'EMPLOYER')}>
              <option value="CANDIDATE">Candidate</option>
              <option value="EMPLOYER">Employer</option>
            </select>
          </label>
          {message && <p className="success">{message}</p>}
          {error && <p className="error">{error}</p>}
          <button type="submit">Tao tai khoan</button>
        </form>
      </section>
    </Shell>
  );
}

function VerifyEmailPage() {
  const [params] = useSearchParams();
  const [message, setMessage] = useState('Dang xac minh...');
  useEffect(() => {
    const token = params.get('token');
    if (!token) {
      setMessage('Thieu token xac minh.');
      return;
    }
    authService.verifyEmail(token)
      .then(() => setMessage('Email da duoc xac minh. Ban co the dang nhap.'))
      .catch((err) => setMessage(readError(err)));
  }, [params]);
  return <Shell><section className="auth-panel"><h1>Xac minh email</h1><p>{message}</p><Link to="/login">Ve trang dang nhap</Link></section></Shell>;
}

function OAuthCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  useEffect(() => {
    const token = params.get('token');
    if (token) {
      localStorage.setItem('token', token);
      navigate('/candidate');
    } else {
      navigate('/login');
    }
  }, [navigate, params]);
  return <Shell><section className="auth-panel"><p>Dang hoan tat dang nhap Google...</p></section></Shell>;
}

function SelectRolePage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  async function select(role: 'CANDIDATE' | 'EMPLOYER') {
    const token = params.get('token');
    if (!token) return setError('Thieu token chon vai tro.');
    try {
      const response = await authService.completeOauthRole(token, role);
      if (response.token) localStorage.setItem('token', response.token);
      navigate(role === 'CANDIDATE' ? '/candidate' : '/jobs');
    } catch (err) {
      setError(readError(err));
    }
  }
  return (
    <Shell>
      <section className="auth-panel">
        <h1>Chon vai tro</h1>
        <div className="button-row">
          <button onClick={() => select('CANDIDATE')}>Candidate</button>
          <button className="outline" onClick={() => select('EMPLOYER')}>Employer</button>
        </div>
        {error && <p className="error">{error}</p>}
      </section>
    </Shell>
  );
}

function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [filters, setFilters] = useState<JobFilters>({ sort: 'newest' });
  const [error, setError] = useState('');

  async function load() {
    try {
      const response = await jobService.getAll(filters, 0, 12);
      setJobs(response.content);
    } catch (err) {
      setError(readError(err));
    }
  }

  useEffect(() => { load(); }, []);

  return (
    <Shell>
      <section className="page-grid">
        <aside className="filter-panel">
          <h2>Tim viec</h2>
          <input placeholder="Tu khoa" value={filters.search || ''} onChange={(e) => setFilters({ ...filters, search: e.target.value })} />
          <input placeholder="Dia diem" value={filters.location || ''} onChange={(e) => setFilters({ ...filters, location: e.target.value })} />
          <input placeholder="Ky nang: Java, React" value={filters.skills || ''} onChange={(e) => setFilters({ ...filters, skills: e.target.value })} />
          <select value={filters.experienceLevel || ''} onChange={(e) => setFilters({ ...filters, experienceLevel: e.target.value })}>
            <option value="">Kinh nghiem</option>
            <option value="INTERN">Intern</option>
            <option value="FRESHER">Fresher</option>
            <option value="JUNIOR">Junior</option>
          </select>
          <select value={filters.sort || 'newest'} onChange={(e) => setFilters({ ...filters, sort: e.target.value })}>
            <option value="newest">Moi nhat</option>
            <option value="relevance">Phu hop</option>
            <option value="salary">Luong cao</option>
            <option value="deadline">Gan deadline</option>
          </select>
          <button onClick={load}>Ap dung</button>
          {error && <p className="error">{error}</p>}
        </aside>
        <section className="list-panel">
          <h1>Viec lam dang tuyen</h1>
          <div className="job-list">{jobs.map((job) => <JobCard key={job.id} job={job} />)}</div>
        </section>
      </section>
    </Shell>
  );
}

function JobCard({ job }: { job: Job }) {
  return (
    <article className="job-card">
      <div>
        <Link to={`/jobs/${job.id}`}><h3>{job.title}</h3></Link>
        <p>{job.company.name} · {job.location} · {job.experienceLevel}</p>
        <p>{formatMoney(job.salaryMin)} - {formatMoney(job.salaryMax)}</p>
      </div>
      <div className="chip-row">
        {job.matchScore !== undefined && <span className="chip strong">{job.matchScore}% match</span>}
        {job.saved && <span className="chip">Da luu</span>}
        {job.applied && <span className="chip">Da ung tuyen</span>}
      </div>
    </article>
  );
}

function JobDetailPage() {
  const { id } = useParams();
  const [job, setJob] = useState<Job | null>(null);
  const [cvs, setCvs] = useState<CvFile[]>([]);
  const [cvId, setCvId] = useState<string | undefined>();
  const [message, setMessage] = useState('');

  async function load() {
    if (!id) return;
    setJob(await jobService.getById(id));
    if (getToken()) {
      candidateService.getCvs().then((items) => {
        setCvs(items);
        setCvId(items.find((item) => item.defaultCv)?.id || items[0]?.id);
      }).catch(() => setCvs([]));
    }
  }

  useEffect(() => { load(); }, [id]);

  async function toggleSave() {
    if (!job) return;
    if (job.saved) await candidateService.unsaveJob(job.id);
    else await candidateService.saveJob(job.id);
    await load();
  }

  async function apply() {
    if (!job) return;
    try {
      await candidateService.apply(job.id, cvId);
      setMessage('Da nop ho so ung tuyen.');
      await load();
    } catch (err) {
      setMessage(readError(err));
    }
  }

  if (!job) return <Shell><p className="loading">Dang tai...</p></Shell>;
  return (
    <Shell>
      <section className="detail-page">
        <div className="detail-main">
          <p className="eyebrow">{job.company.name}</p>
          <h1>{job.title}</h1>
          <p>{job.location} · {job.experienceLevel} · {formatMoney(job.salaryMin)} - {formatMoney(job.salaryMax)}</p>
          <h2>Mo ta</h2>
          <p>{job.description}</p>
          <h2>Yeu cau</h2>
          <div className="chip-row">{job.requirements.map((item) => <span className="chip" key={item}>{item}</span>)}</div>
        </div>
        <aside className="apply-panel">
          {job.matchScore !== undefined && <strong>{job.matchScore}% phu hop</strong>}
          {getToken() ? (
            <>
              <button onClick={toggleSave}>{job.saved ? 'Bo luu' : 'Luu viec'}</button>
              <select value={cvId || ''} onChange={(e) => setCvId(e.target.value || undefined)}>
                <option value="">Chon CV</option>
                {cvs.map((cv) => <option key={cv.id} value={cv.id}>{cv.originalFileName}</option>)}
              </select>
              <button onClick={apply} disabled={job.applied}>{job.applied ? 'Da ung tuyen' : 'Ung tuyen'}</button>
            </>
          ) : <Link className="button-link" to="/login">Dang nhap de ung tuyen</Link>}
          {message && <p className={message.includes('Da nop') ? 'success' : 'error'}>{message}</p>}
        </aside>
      </section>
    </Shell>
  );
}

function CandidateLayout() {
  const navigate = useNavigate();
  function logout() {
    localStorage.removeItem('token');
    navigate('/login');
  }
  return (
    <div className="candidate-shell">
      <aside className="candidate-nav">
        <Link className="brand" to="/candidate">Candidate</Link>
        <NavLink to="/candidate/profile">Ho so</NavLink>
        <NavLink to="/candidate/cvs">CV</NavLink>
        <NavLink to="/candidate/saved-jobs">Viec da luu</NavLink>
        <NavLink to="/candidate/applications">Ung tuyen</NavLink>
        <NavLink to="/candidate/notifications">Thong bao</NavLink>
        <NavLink to="/candidate/subscription">Goi dich vu</NavLink>
        <NavLink to="/jobs">Tim viec</NavLink>
        <button onClick={logout}>Dang xuat</button>
      </aside>
      <main className="candidate-main"><Outlet /></main>
    </div>
  );
}

function CandidateHome() {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  useEffect(() => { jobService.recommendations().then(setRecommendations).catch(() => setRecommendations([])); }, []);
  return <section><h1>Dashboard ung vien</h1><div className="job-list">{recommendations.slice(0, 4).map((item) => <JobCard key={item.job.id} job={item.job} />)}</div></section>;
}

function ProfilePage() {
  const [profile, setProfile] = useState<CandidateProfile | null>(null);
  const [skills, setSkills] = useState('');
  const [message, setMessage] = useState('');
  useEffect(() => { candidateService.getProfile().then((data) => { setProfile(data); setSkills(data.skills.join(', ')); }); }, []);
  async function save() {
    if (!profile) return;
    const saved = await candidateService.updateProfile({ ...profile, skills: skills.split(',').map((s) => s.trim()).filter(Boolean) });
    setProfile(saved);
    setMessage('Da luu ho so.');
  }
  if (!profile) return <p className="loading">Dang tai...</p>;
  return (
    <section className="content-card">
      <h1>Ho so Candidate</h1>
      <div className="form-grid two">
        <label>Ho ten<input value={profile.fullName || ''} onChange={(e) => setProfile({ ...profile, fullName: e.target.value })} /></label>
        <label>Dien thoai<input value={profile.phone || ''} onChange={(e) => setProfile({ ...profile, phone: e.target.value })} /></label>
        <label>Dia diem<input value={profile.location || ''} onChange={(e) => setProfile({ ...profile, location: e.target.value })} /></label>
        <label>Ky nang<input value={skills} onChange={(e) => setSkills(e.target.value)} /></label>
        <label className="wide">Gioi thieu<textarea value={profile.bio || ''} onChange={(e) => setProfile({ ...profile, bio: e.target.value })} /></label>
      </div>
      <button onClick={save}>Luu ho so</button>
      <span className={profile.applyReady ? 'success' : 'muted'}>{profile.applyReady ? 'San sang ung tuyen' : 'Can hoan thien ho so va CV'}</span>
      {message && <p className="success">{message}</p>}
    </section>
  );
}

function CvPage() {
  const [cvs, setCvs] = useState<CvFile[]>([]);
  const [versions, setVersions] = useState<CvVersion[]>([]);
  const [message, setMessage] = useState('');
  async function load() {
    setCvs(await candidateService.getCvs());
    setVersions(await candidateService.getCvVersions());
  }
  useEffect(() => { load(); }, []);
  async function upload(file?: File) {
    if (!file) return;
    try {
      await candidateService.uploadCv(file);
      setMessage('Upload CV thanh cong.');
      await load();
    } catch (err) {
      setMessage(readError(err));
    }
  }
  async function createVersion() {
    await candidateService.createCvVersion(`CV Builder ${versions.length + 1}`, {});
    await load();
  }
  return (
    <section className="content-card">
      <h1>CV cua toi</h1>
      <input type="file" accept="application/pdf" onChange={(e) => upload(e.target.files?.[0])} />
      {message && <p className={message.includes('thanh cong') ? 'success' : 'error'}>{message}</p>}
      <div className="table-list">
        {cvs.map((cv) => (
          <div className="table-row" key={cv.id}>
            <strong>{cv.originalFileName}</strong>
            <span>{Math.round(cv.fileSize / 1024)} KB</span>
            <span>{cv.defaultCv ? 'Mac dinh' : 'PDF'}</span>
            <button onClick={() => candidateService.setDefaultCv(cv.id).then(load)}>Dat mac dinh</button>
            <button className="danger" onClick={() => candidateService.deleteCv(cv.id).then(load)}>Xoa</button>
          </div>
        ))}
      </div>
      <h2>CV Builder</h2>
      <button onClick={createVersion}>Tao ban CV tu ho so</button>
      <div className="table-list">{versions.map((version) => <div className="table-row" key={version.id}><strong>{version.title}</strong><span>{version.templateKey}</span><span>{new Date(version.updatedAt).toLocaleDateString('vi-VN')}</span></div>)}</div>
    </section>
  );
}

function SavedJobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  useEffect(() => { candidateService.getSavedJobs().then(setJobs); }, []);
  return <section><h1>Viec da luu</h1><div className="job-list">{jobs.map((job) => <JobCard key={job.id} job={job} />)}</div></section>;
}

function ApplicationsPage() {
  const [applications, setApplications] = useState<CandidateApplication[]>([]);
  useEffect(() => { candidateService.getApplications().then(setApplications); }, []);
  return (
    <section className="content-card">
      <h1>Ho so ung tuyen</h1>
      <div className="table-list">
        {applications.map((application) => (
          <Link className="table-row clickable" to={`/candidate/applications/${application.id}`} key={application.id}>
            <strong>{application.job.title}</strong>
            <span>{application.job.company.name}</span>
            <span className="status-pill">{statusLabels[application.status] || application.status}</span>
            <span>{new Date(application.submittedAt).toLocaleDateString('vi-VN')}</span>
          </Link>
        ))}
      </div>
    </section>
  );
}

function ApplicationDetailPage() {
  const { id } = useParams();
  const [application, setApplication] = useState<CandidateApplication | null>(null);
  useEffect(() => { if (id) candidateService.getApplication(id).then(setApplication); }, [id]);
  if (!application) return <p className="loading">Dang tai...</p>;
  return (
    <section className="content-card">
      <h1>{application.job.title}</h1>
      <p>{application.job.company.name} · {statusLabels[application.status] || application.status}</p>
      <p>CV da nop: {application.cv?.originalFileName || application.cvVersion?.title || 'Khong co'}</p>
      <div className="timeline">
        {application.timeline.map((item) => (
          <div className="timeline-item" key={item.id}>
            <strong>{statusLabels[item.toStatus] || item.toStatus}</strong>
            <span>{new Date(item.createdAt).toLocaleString('vi-VN')}</span>
            <p>{item.publicNote}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function NotificationsPage() {
  const [items, setItems] = useState<NotificationItem[]>([]);
  async function load() { setItems(await candidateService.getNotifications()); }
  useEffect(() => { load(); }, []);
  return (
    <section className="content-card">
      <h1>Thong bao</h1>
      <div className="table-list">
        {items.map((item) => <div className="table-row" key={item.id}><strong>{item.title}</strong><span>{item.message}</span><button onClick={() => candidateService.markNotificationRead(item.id).then(load)}>{item.read ? 'Da doc' : 'Danh dau doc'}</button></div>)}
      </div>
    </section>
  );
}

function SubscriptionPage() {
  const [subscription, setSubscription] = useState<SubscriptionView | null>(null);
  useEffect(() => { candidateService.getSubscription().then(setSubscription); }, []);
  if (!subscription) return <p className="loading">Dang tai...</p>;
  return (
    <section className="content-card">
      <h1>Goi dich vu</h1>
      <div className="metric-grid">
        <div><span>Goi hien tai</span><strong>{subscription.planName}</strong></div>
        <div><span>CV</span><strong>{subscription.cvCount}</strong></div>
        <div><span>Viec da luu</span><strong>{subscription.savedJobsCount}</strong></div>
        <div><span>Thong bao chua doc</span><strong>{subscription.unreadNotificationsCount}</strong></div>
      </div>
      <h2>Quyen loi</h2>
      <div className="chip-row">{subscription.benefits.map((benefit) => <span className="chip" key={benefit}>{benefit}</span>)}</div>
    </section>
  );
}

export default App;
