import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { provinceService } from '../../services/provinceService';
import {
  EMPTY_VIETNAM_ADDRESS,
  formatVietnamAddress,
  type AdministrativeDivision,
  type VietnamAddressValue,
} from '../../types/location';
import './location-picker.css';

interface VietnamAddressFieldsProps {
  value: VietnamAddressValue;
  onChange: (value: VietnamAddressValue) => void;
  label?: string;
  required?: boolean;
  disabled?: boolean;
  allowOnline?: boolean;
  allowRemote?: boolean;
  detailPlaceholder?: string;
  className?: string;
}

export function VietnamAddressFields({
  value,
  onChange,
  label,
  required = false,
  disabled = false,
  allowOnline = false,
  allowRemote = false,
  detailPlaceholder = 'Số nhà, tên đường, tòa nhà...',
  className = '',
}: VietnamAddressFieldsProps) {
  const generatedId = useId().replace(/:/g, '');
  const [provinces, setProvinces] = useState<AdministrativeDivision[]>([]);
  const [wards, setWards] = useState<AdministrativeDivision[]>([]);
  const [loadingProvinces, setLoadingProvinces] = useState(true);
  const [loadingWards, setLoadingWards] = useState(false);
  const [provinceError, setProvinceError] = useState('');
  const [wardError, setWardError] = useState('');
  const [provinceRetry, setProvinceRetry] = useState(0);
  const [wardRetry, setWardRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setLoadingProvinces(true);
    setProvinceError('');
    provinceService.getProvinces(provinceRetry > 0).then((items) => {
      if (active) setProvinces(items);
    }).catch(() => {
      if (active) setProvinceError('Không thể tải danh sách tỉnh/thành phố.');
    }).finally(() => {
      if (active) setLoadingProvinces(false);
    });
    return () => { active = false; };
  }, [provinceRetry]);

  const resolvedProvinceCode = useMemo(() => (
    value.provinceCode
    || provinces.find((province) => province.name === value.provinceName)?.code
    || null
  ), [provinces, value.provinceCode, value.provinceName]);

  useEffect(() => {
    if (value.mode !== 'physical' || !resolvedProvinceCode) {
      setWards([]);
      setWardError('');
      return;
    }
    let active = true;
    setLoadingWards(true);
    setWardError('');
    provinceService.getWards(resolvedProvinceCode, wardRetry > 0).then((items) => {
      if (active) setWards(items);
    }).catch(() => {
      if (active) setWardError('Không thể tải danh sách phường/xã.');
    }).finally(() => {
      if (active) setLoadingWards(false);
    });
    return () => { active = false; };
  }, [resolvedProvinceCode, value.mode, wardRetry]);

  const resolvedWardCode = value.wardCode
    || wards.find((ward) => ward.name === value.wardName)?.code
    || null;
  const legacyProvince = Boolean(value.provinceName && !resolvedProvinceCode);
  const legacyWard = Boolean(value.wardName && !resolvedWardCode && !loadingWards);
  const modeId = `${generatedId}-mode`;
  const provinceId = `${generatedId}-province`;
  const wardId = `${generatedId}-ward`;
  const detailId = `${generatedId}-detail`;

  function updateMode(mode: VietnamAddressValue['mode']) {
    onChange(mode === 'physical' ? { ...EMPTY_VIETNAM_ADDRESS } : { ...EMPTY_VIETNAM_ADDRESS, mode });
  }

  function updateProvince(codeText: string) {
    const code = Number(codeText);
    const selected = provinces.find((province) => province.code === code);
    onChange({
      ...value,
      provinceCode: selected?.code || null,
      provinceName: selected?.name || '',
      wardCode: null,
      wardName: '',
      mode: 'physical',
    });
    setWardRetry(0);
  }

  function updateWard(codeText: string) {
    const code = Number(codeText);
    const selected = wards.find((ward) => ward.code === code);
    onChange({ ...value, wardCode: selected?.code || null, wardName: selected?.name || '' });
  }

  return (
    <fieldset className={`location-picker ${className}`.trim()} disabled={disabled}>
      {label && <legend className="location-picker__label">{label}{required && <span className="location-picker__required"> *</span>}</legend>}
      {(allowOnline || allowRemote) && (
        <div className="location-picker__field">
          <label htmlFor={modeId}>Hình thức</label>
          <select id={modeId} value={value.mode} onChange={(event) => updateMode(event.target.value as VietnamAddressValue['mode'])}>
            <option value="physical">Tại địa điểm cụ thể</option>
            {allowRemote && <option value="remote">Remote / Làm việc từ xa</option>}
            {allowOnline && <option value="online">Trực tuyến</option>}
          </select>
        </div>
      )}

      {value.mode === 'physical' && (
        <div className="location-picker__grid">
          <div className="location-picker__field">
            <label htmlFor={provinceId}>Tỉnh / Thành phố{required && <span className="location-picker__required"> *</span>}</label>
            <select
              id={provinceId}
              value={resolvedProvinceCode || ''}
              onChange={(event) => updateProvince(event.target.value)}
              required={required}
              disabled={disabled || loadingProvinces || Boolean(provinceError)}
              aria-describedby={provinceError ? `${provinceId}-error` : undefined}
            >
              <option value="">{loadingProvinces ? 'Đang tải...' : 'Chọn tỉnh / thành phố'}</option>
              {provinces.map((province) => <option key={province.code} value={province.code}>{province.name}</option>)}
            </select>
            {legacyProvince && <p className="location-picker__legacy">Giá trị cũ: {value.provinceName}. Vui lòng chọn lại.</p>}
            {provinceError && <div id={`${provinceId}-error`} className="location-picker__error" role="alert"><span>{provinceError}</span><button type="button" className="location-picker__retry" onClick={() => setProvinceRetry((version) => version + 1)}>Thử lại</button></div>}
          </div>

          <div className="location-picker__field">
            <label htmlFor={wardId}>Phường / Xã{required && <span className="location-picker__required"> *</span>}</label>
            <select
              id={wardId}
              value={resolvedWardCode || ''}
              onChange={(event) => updateWard(event.target.value)}
              required={required}
              disabled={disabled || !resolvedProvinceCode || loadingWards || Boolean(wardError)}
              aria-describedby={wardError ? `${wardId}-error` : undefined}
            >
              <option value="">{loadingWards ? 'Đang tải...' : resolvedProvinceCode ? 'Chọn phường / xã' : 'Chọn tỉnh trước'}</option>
              {wards.map((ward) => <option key={ward.code} value={ward.code}>{ward.name}</option>)}
            </select>
            {legacyWard && <p className="location-picker__legacy">Giá trị cũ: {value.wardName}. Vui lòng chọn lại.</p>}
            {wardError && <div id={`${wardId}-error`} className="location-picker__error" role="alert"><span>{wardError}</span><button type="button" className="location-picker__retry" onClick={() => setWardRetry((version) => version + 1)}>Thử lại</button></div>}
          </div>

          <div className="location-picker__field location-picker__field--wide">
            <label htmlFor={detailId}>Địa chỉ chi tiết</label>
            <input id={detailId} value={value.detail} onChange={(event) => onChange({ ...value, detail: event.target.value })} placeholder={detailPlaceholder} maxLength={255} />
            <p className="location-picker__hint">Chỉ nhập số nhà, tên đường, tòa nhà hoặc tầng/phòng.</p>
          </div>
        </div>
      )}
    </fieldset>
  );
}

interface VietnamAddressPickerProps extends Omit<VietnamAddressFieldsProps, 'value' | 'onChange'> {
  value: string;
  onChange: (value: string) => void;
}

export default function VietnamAddressPicker({ value, onChange, ...props }: VietnamAddressPickerProps) {
  const [selection, setSelection] = useState<VietnamAddressValue>(() => ({ ...EMPTY_VIETNAM_ADDRESS }));
  const [legacyValue, setLegacyValue] = useState(value);
  const emittedValueRef = useRef('');

  useEffect(() => {
    if (value === emittedValueRef.current) return;
    if (value === 'Remote') {
      setSelection({ ...EMPTY_VIETNAM_ADDRESS, mode: 'remote' });
      setLegacyValue('');
      return;
    } else if (value === 'Trực tuyến') {
      setSelection({ ...EMPTY_VIETNAM_ADDRESS, mode: 'online' });
      setLegacyValue('');
      return;
    }
    if (!value) {
      setSelection({ ...EMPTY_VIETNAM_ADDRESS });
      setLegacyValue('');
      return;
    }

    let active = true;
    setSelection({ ...EMPTY_VIETNAM_ADDRESS });
    setLegacyValue('');
    provinceService.getProvinces().then(async (provinces) => {
      const province = [...provinces]
        .sort((left, right) => right.name.length - left.name.length)
        .find((item) => value === item.name || value.endsWith(`, ${item.name}`));
      if (!province) throw new Error('legacy-location');

      const withoutProvince = value === province.name
        ? ''
        : value.slice(0, -(province.name.length + 2));
      const wards = await provinceService.getWards(province.code);
      const ward = [...wards]
        .sort((left, right) => right.name.length - left.name.length)
        .find((item) => withoutProvince === item.name || withoutProvince.endsWith(`, ${item.name}`));
      const detail = ward
        ? (withoutProvince === ward.name ? '' : withoutProvince.slice(0, -(ward.name.length + 2)))
        : withoutProvince;

      if (active) {
        setSelection({
          provinceCode: province.code,
          provinceName: province.name,
          wardCode: ward?.code || null,
          wardName: ward?.name || '',
          detail,
          mode: 'physical',
        });
        setLegacyValue('');
      }
    }).catch(() => {
      if (active) {
        setSelection({ ...EMPTY_VIETNAM_ADDRESS });
        setLegacyValue(value);
      }
    });
    return () => { active = false; };
  }, [value]);

  function handleChange(next: VietnamAddressValue) {
    setSelection(next);
    setLegacyValue('');
    const formatted = formatVietnamAddress(next);
    emittedValueRef.current = formatted;
    onChange(formatted);
  }

  return (
    <div className="location-picker">
      {legacyValue && <p className="location-picker__legacy">Địa điểm hiện tại: {legacyValue}. Hãy chọn lại để dùng dữ liệu hành chính chuẩn.</p>}
      <VietnamAddressFields {...props} value={selection} onChange={handleChange} />
    </div>
  );
}
