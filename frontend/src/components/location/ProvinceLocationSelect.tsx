import { useEffect, useId, useMemo, useState } from 'react';
import { provinceService } from '../../services/provinceService';
import type { AdministrativeDivision } from '../../types/location';
import SearchableCombobox from './SearchableCombobox';
import './location-picker.css';

interface ProvinceLocationSelectProps {
  value: string;
  onChange: (value: string) => void;
  label?: string;
  ariaLabel?: string;
  allowRemote?: boolean;
  required?: boolean;
  disabled?: boolean;
  className?: string;
  selectClassName?: string;
  placeholder?: string;
  helperText?: string;
}

export default function ProvinceLocationSelect({
  value,
  onChange,
  label,
  ariaLabel,
  allowRemote = false,
  required = false,
  disabled = false,
  className = '',
  selectClassName = '',
  placeholder = 'Chọn tỉnh / thành phố',
  helperText,
}: ProvinceLocationSelectProps) {
  const generatedId = useId();
  const selectId = `province-${generatedId.replace(/:/g, '')}`;
  const statusId = `${selectId}-status`;
  const [provinces, setProvinces] = useState<AdministrativeDivision[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retryVersion, setRetryVersion] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    provinceService.getProvinces(retryVersion > 0).then((items) => {
      if (active) setProvinces(items);
    }).catch(() => {
      if (active) setError('Không thể tải danh sách tỉnh/thành phố.');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [retryVersion]);

  const knownValue = useMemo(() => (
    value === ''
    || (allowRemote && value === 'Remote')
    || provinces.some((province) => province.name === value)
  ), [allowRemote, provinces, value]);

  return (
    <div className={`location-picker ${className}`.trim()} aria-busy={loading}>
      {label && <label className="location-picker__label" htmlFor={selectId}>{label}{required && <span className="location-picker__required"> *</span>}</label>}
      <SearchableCombobox
        value={knownValue ? value : ''}
        options={[
          ...(allowRemote ? [{ value: 'Remote', label: 'Remote / Làm việc từ xa', keywords: 'tu xa' }] : []),
          ...provinces.map((province) => ({ value: province.name, label: province.name })),
        ]}
        onChange={onChange}
        placeholder={placeholder}
        ariaLabel={label ? undefined : ariaLabel}
        inputClassName={selectClassName}
        inputId={selectId}
        required={required}
        disabled={disabled || Boolean(error)}
        loading={loading}
      />
      {!knownValue && value && <p className="location-picker__legacy">Giá trị hiện tại: {value}. Vui lòng chọn lại.</p>}
      <div id={statusId} aria-live="polite">
        {error ? (
          <div className="location-picker__error" role="alert">
            <span>{error}</span>
            <button type="button" className="location-picker__retry" onClick={() => setRetryVersion((version) => version + 1)}>Thử lại</button>
          </div>
        ) : helperText ? <p className="location-picker__hint">{helperText}</p> : null}
      </div>
    </div>
  );
}
