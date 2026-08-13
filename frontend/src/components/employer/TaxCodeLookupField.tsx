import { useEffect, useState } from 'react';
import { employerService, type TaxCodeLookupResult } from '../../services/employerService';
import { FiCheckCircle, FiRefreshCw } from '../Icons';
import { isVietnamTaxCodeFormat, normalizeVietnamTaxCode } from '../../utils/taxCode';

interface TaxCodeLookupFieldProps {
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
}

type LookupState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'found'; result: TaxCodeLookupResult }
  | { status: 'unavailable' };

export function TaxCodeLookupField({ value, onChange, error, disabled = false }: TaxCodeLookupFieldProps) {
  const [lookup, setLookup] = useState<LookupState>({ status: 'idle' });

  useEffect(() => {
    if (!isVietnamTaxCodeFormat(value)) {
      setLookup({ status: 'idle' });
      return;
    }

    const controller = new AbortController();
    const timeoutId = window.setTimeout(async () => {
      setLookup({ status: 'loading' });
      try {
        const result = await employerService.lookupTaxCode(normalizeVietnamTaxCode(value), controller.signal);
        setLookup({ status: 'found', result });
      } catch (requestError: any) {
        if (controller.signal.aborted) return;
        if (requestError?.response?.data?.code === 'TAX_LOOKUP_UNAVAILABLE') {
          setLookup({ status: 'unavailable' });
          return;
        }
        // Theo yêu cầu nghiệp vụ, MST không tồn tại chỉ hiển thị lỗi sau khi người dùng bấm Lưu.
        setLookup({ status: 'idle' });
      }
    }, 700);

    return () => {
      window.clearTimeout(timeoutId);
      controller.abort();
    };
  }, [value]);

  return (
    <div className="space-y-2">
      <label htmlFor="company-tax-code" className="block text-sm font-medium text-gray-700">
        Mã số thuế
      </label>
      <div className="relative">
        <input
          id="company-tax-code"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="Ví dụ: 0316794479"
          inputMode="numeric"
          autoComplete="off"
          disabled={disabled}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? 'company-tax-code-error' : undefined}
          className={`w-full rounded-xl border px-4 py-2.5 pr-11 outline-none transition-all focus:ring-2 disabled:bg-gray-50 disabled:text-gray-500 ${
            error
              ? 'border-red-500 focus:border-red-500 focus:ring-red-200'
              : 'border-gray-200 focus:border-emerald-500 focus:ring-emerald-200'
          }`}
        />
        {lookup.status === 'loading' ? (
          <>
            <FiRefreshCw className="absolute right-4 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-emerald-600" aria-hidden="true" />
            <span className="sr-only" role="status">Đang tra cứu mã số thuế</span>
          </>
        ) : null}
      </div>

      {error ? (
        <p id="company-tax-code-error" className="text-xs font-medium text-red-500">
          {error}
        </p>
      ) : null}

      {lookup.status === 'found' ? (
        <div role="status" className="flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3.5 py-3 text-emerald-800">
          <FiCheckCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <div className="min-w-0">
            <p className="text-xs font-medium text-emerald-700">Tên pháp lý theo mã số thuế</p>
            <p className="mt-0.5 break-words text-sm font-semibold">{lookup.result.companyName}</p>
          </div>
        </div>
      ) : null}

      {lookup.status === 'unavailable' ? (
        <p role="status" className="text-xs text-amber-700">Chưa thể tra cứu tên công ty lúc này. Hệ thống sẽ thử lại khi bạn lưu.</p>
      ) : null}
    </div>
  );
}
