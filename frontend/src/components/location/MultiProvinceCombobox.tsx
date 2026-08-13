import { useEffect, useMemo, useState } from 'react';
import { provinceService } from '../../services/provinceService';
import SearchableCombobox from './SearchableCombobox';
import type { AdministrativeDivision } from '../../types/location';
import './location-picker.css';

interface MultiProvinceComboboxProps {
  values: string[];
  onChange: (values: string[]) => void;
  max?: number;
  label?: string;
  required?: boolean;
}

export default function MultiProvinceCombobox({
  values,
  onChange,
  max = 5,
  label = 'Địa điểm làm việc mong muốn',
  required = false,
}: MultiProvinceComboboxProps) {
  const [provinces, setProvinces] = useState<AdministrativeDivision[]>([]);
  const [selection, setSelection] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    provinceService.getProvinces(retry > 0)
      .then((items) => { if (active) setProvinces(items); })
      .catch(() => { if (active) setError('Không thể tải danh sách tỉnh/thành phố.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [retry]);

  const options = useMemo(() => provinces
    .filter((province) => !values.includes(province.name))
    .map((province) => ({ value: province.name, label: province.name })), [provinces, values]);

  return (
    <div className="location-picker onboarding-field">
      <label className="location-picker__label">
        {label}{required && <span className="location-picker__required"> *</span>}
        <span className="onboarding-field__count">{values.length}/{max}</span>
      </label>
      <div className="onboarding-tags" aria-label="Địa điểm đã chọn">
        {values.map((value) => (
          <span className="onboarding-tag" key={value}>
            {value}
            <button type="button" aria-label={`Xóa ${value}`} onClick={() => onChange(values.filter((item) => item !== value))}>×</button>
          </span>
        ))}
      </div>
      {values.length < max && (
        <SearchableCombobox
          value={selection}
          options={options}
          onChange={(value) => {
            if (value && !values.includes(value)) onChange([...values, value]);
            setSelection('');
          }}
          placeholder="Nhập để tìm tỉnh / thành phố"
          ariaLabel={label}
          loading={loading}
          disabled={Boolean(error)}
          clearAfterSelect
        />
      )}
      {error && <div className="location-picker__error" role="alert"><span>{error}</span><button type="button" className="location-picker__retry" onClick={() => setRetry((version) => version + 1)}>Thử lại</button></div>}
      <p className="location-picker__hint">Chỉ các địa điểm được chọn từ danh sách mới được lưu.</p>
    </div>
  );
}
