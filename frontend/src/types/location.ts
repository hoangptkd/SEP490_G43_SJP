export interface AdministrativeDivision {
  code: number;
  name: string;
  codename: string;
  divisionType: string;
}

export interface VietnamAddressValue {
  provinceCode: number | null;
  provinceName: string;
  wardCode: number | null;
  wardName: string;
  detail: string;
  mode: 'physical' | 'remote' | 'online';
}

export const EMPTY_VIETNAM_ADDRESS: VietnamAddressValue = {
  provinceCode: null,
  provinceName: '',
  wardCode: null,
  wardName: '',
  detail: '',
  mode: 'physical',
};

export function formatVietnamAddress(value: VietnamAddressValue): string {
  if (value.mode === 'remote') return 'Remote';
  if (value.mode === 'online') return 'Trực tuyến';
  return [value.detail, value.wardName, value.provinceName]
    .map((part) => part.trim())
    .filter(Boolean)
    .join(', ');
}
