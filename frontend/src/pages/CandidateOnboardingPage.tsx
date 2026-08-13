import { FormEvent, KeyboardEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { candidateService } from '../services/candidateService';
import MultiProvinceCombobox from '../components/location/MultiProvinceCombobox';
import { parseApiError } from '../utils/planLimits';
import '../styles/candidate-onboarding.css';

const EXPERIENCE_LEVELS = [
  ['intern', 'Thực tập'], ['fresher', 'Fresher'], ['junior', 'Junior'],
  ['middle', 'Middle'], ['senior', 'Senior'], ['lead', 'Lead'],
] as const;

const readError = (error: unknown) => parseApiError(error).message;

function safeReturnTo(value: string | null) {
  return value?.startsWith('/') && !value.startsWith('//') && value !== '/candidate/onboarding'
    ? value
    : '/candidate';
}

export default function CandidateOnboardingPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [titles, setTitles] = useState<string[]>([]);
  const [titleDraft, setTitleDraft] = useState('');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [salaryText, setSalaryText] = useState('');
  const [experienceLevel, setExperienceLevel] = useState('');
  const [locations, setLocations] = useState<string[]>([]);
  const [willingToRelocate, setWillingToRelocate] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const returnTo = safeReturnTo(params.get('returnTo'));

  useEffect(() => {
    candidateService.getOnboarding().then((data) => {
      setTitles(data.desiredJobTitles || []);
      setSalaryText(data.expectedSalary ? String(Math.round(data.expectedSalary)) : '');
      setExperienceLevel(data.experienceLevel || '');
      setLocations(data.preferredLocations || []);
      setWillingToRelocate(Boolean(data.willingToRelocate));
    }).catch((reason) => setError(readError(reason))).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      candidateService.getJobTitleSuggestions(titleDraft, 10)
        .then(setSuggestions)
        .catch(() => setSuggestions([]));
    }, 220);
    return () => window.clearTimeout(timer);
  }, [titleDraft]);

  const salary = Number(salaryText.replace(/\D/g, ''));
  const salaryDisplay = useMemo(() => salaryText ? salary.toLocaleString('vi-VN') : '', [salary, salaryText]);
  const canComplete = titles.length > 0 && salaryText !== '' && Number.isFinite(salary)
    && salary >= 0 && Boolean(experienceLevel) && locations.length > 0 && !saving;

  function addTitle(rawValue: string) {
    const value = rawValue.trim().replace(/\s+/g, ' ');
    if (!value || titles.length >= 5) return;
    if (!titles.some((item) => item.toLocaleLowerCase('vi') === value.toLocaleLowerCase('vi'))) {
      setTitles([...titles, value]);
    }
    setTitleDraft('');
  }

  function onTitleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      addTitle(titleDraft);
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!canComplete) return;
    setSaving(true);
    setError('');
    try {
      await candidateService.completeOnboarding({
        desiredJobTitles: titles,
        expectedSalary: salary,
        experienceLevel,
        preferredLocations: locations,
        willingToRelocate,
      });
      navigate(returnTo, { replace: true });
    } catch (reason) {
      setError(readError(reason));
    } finally {
      setSaving(false);
    }
  }

  async function skip() {
    setSaving(true);
    setError('');
    try {
      await candidateService.skipOnboarding();
      navigate(returnTo, { replace: true });
    } catch (reason) {
      setError(readError(reason));
      setSaving(false);
    }
  }

  if (loading) return <div className="onboarding-shell"><div className="onboarding-card onboarding-loading" role="status">Đang chuẩn bị hồ sơ của bạn...</div></div>;

  return (
    <main className="onboarding-shell">
      <div className="onboarding-orb onboarding-orb--one" />
      <div className="onboarding-orb onboarding-orb--two" />
      <section className="onboarding-wrap" aria-labelledby="onboarding-title">
        <header className="onboarding-header">
          <Link to="/" className="onboarding-brand" aria-label="Smart Recruitment Portal">SR</Link>
          <p className="onboarding-kicker">Bước khởi đầu dành cho ứng viên</p>
          <h1 id="onboarding-title">Công việc phù hợp bắt đầu từ điều bạn mong muốn</h1>
          <p>Chia sẻ vài ưu tiên để hệ thống sắp xếp cơ hội sát với mục tiêu của bạn hơn.</p>
        </header>

        <form className="onboarding-card" onSubmit={submit}>
          <div className="onboarding-field onboarding-field--wide">
            <label>Vị trí mong muốn <span>*</span><small>{titles.length}/5</small></label>
            <div className="onboarding-tags">
              {titles.map((title) => <span className="onboarding-tag" key={title}>{title}<button type="button" aria-label={`Xóa ${title}`} onClick={() => setTitles(titles.filter((item) => item !== title))}>×</button></span>)}
            </div>
            {titles.length < 5 && <div className="title-combobox">
              <input value={titleDraft} onChange={(event) => setTitleDraft(event.target.value)} onKeyDown={onTitleKeyDown} placeholder="Nhập chức danh và nhấn Enter" maxLength={120} />
              {titleDraft.trim() && <div className="title-combobox__menu">
                {suggestions.filter((item) => !titles.includes(item)).map((item) => <button type="button" key={item} onClick={() => addTitle(item)}>{item}</button>)}
                <button type="button" className="title-combobox__custom" onClick={() => addTitle(titleDraft)}>+ Thêm “{titleDraft.trim()}”</button>
              </div>}
            </div>}
            <p className="onboarding-hint">Có thể chọn từ gợi ý hoặc thêm chức danh riêng, tối đa 5 vị trí.</p>
          </div>

          <div className="onboarding-grid">
            <label className="onboarding-field">Mức lương mong muốn <span>*</span><div className="salary-input"><input inputMode="numeric" value={salaryDisplay} onChange={(event) => setSalaryText(event.target.value.replace(/\D/g, ''))} placeholder="0" /><b>VND / tháng</b></div></label>
            <label className="onboarding-field">Cấp độ kinh nghiệm <span>*</span><select value={experienceLevel} onChange={(event) => setExperienceLevel(event.target.value)}><option value="">Chọn cấp độ</option>{EXPERIENCE_LEVELS.map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
          </div>

          <MultiProvinceCombobox values={locations} onChange={setLocations} required />

          <label className="onboarding-check"><input type="checkbox" checked={willingToRelocate} onChange={(event) => setWillingToRelocate(event.target.checked)} /><span><strong>Sẵn sàng thay đổi địa điểm</strong><small>Tôi cân nhắc chuyển nơi làm việc nếu có cơ hội phù hợp.</small></span></label>

          {error && <div className="onboarding-error" role="alert">{error}</div>}

          <div className="onboarding-actions">
            <button type="button" className="outline" disabled={saving} onClick={() => void skip()}>Hoàn thiện sau</button>
            <button type="submit" disabled={!canComplete}>{saving ? 'Đang lưu...' : 'Hoàn thành hồ sơ'}</button>
          </div>
          <p className="onboarding-required">* Thông tin bắt buộc khi hoàn thành</p>
        </form>
      </section>
    </main>
  );
}
