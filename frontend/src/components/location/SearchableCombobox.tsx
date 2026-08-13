import { useEffect, useId, useMemo, useRef, useState } from 'react';

export interface SearchableOption {
  value: string;
  label: string;
  keywords?: string;
}

interface SearchableComboboxProps {
  value: string;
  options: SearchableOption[];
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  loading?: boolean;
  ariaLabel?: string;
  inputClassName?: string;
  inputId?: string;
  clearAfterSelect?: boolean;
}

export function normalizeVietnameseSearch(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLocaleLowerCase('vi')
    .replace(/\s+/g, ' ')
    .trim();
}

export default function SearchableCombobox({
  value,
  options,
  onChange,
  placeholder = 'Nhập để tìm kiếm',
  disabled = false,
  required = false,
  loading = false,
  ariaLabel,
  inputClassName = '',
  inputId,
  clearAfterSelect = false,
}: SearchableComboboxProps) {
  const id = useId().replace(/:/g, '');
  const rootRef = useRef<HTMLDivElement>(null);
  const selectedOption = options.find((option) => option.value === value);
  const [query, setQuery] = useState(selectedOption?.label || value);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);

  useEffect(() => {
    setQuery(selectedOption?.label || value);
  }, [selectedOption?.label, value]);

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const filtered = useMemo(() => {
    const normalizedQuery = normalizeVietnameseSearch(query);
    if (!normalizedQuery || selectedOption?.label === query) return options;
    return options.filter((option) => normalizeVietnameseSearch(
      `${option.label} ${option.keywords || ''}`,
    ).includes(normalizedQuery));
  }, [options, query, selectedOption?.label]);

  useEffect(() => setActiveIndex(0), [query]);

  function choose(option: SearchableOption) {
    onChange(option.value);
    setQuery(clearAfterSelect ? '' : option.label);
    setOpen(false);
  }

  function handleBlur() {
    window.setTimeout(() => {
      if (!rootRef.current?.contains(document.activeElement)) {
        setOpen(false);
        setQuery(selectedOption?.label || '');
      }
    }, 0);
  }

  return (
    <div className="searchable-combobox" ref={rootRef} onBlur={handleBlur}>
      <input
        id={inputId}
        className={inputClassName}
        role="combobox"
        aria-label={ariaLabel}
        aria-expanded={open}
        aria-controls={`${id}-listbox`}
        aria-autocomplete="list"
        aria-activedescendant={open && filtered[activeIndex] ? `${id}-option-${activeIndex}` : undefined}
        autoComplete="off"
        value={query}
        placeholder={loading ? 'Đang tải...' : placeholder}
        disabled={disabled || loading}
        required={required && !value}
        onFocus={() => setOpen(true)}
        onChange={(event) => {
          setQuery(event.target.value);
          if (value) onChange('');
          setOpen(true);
        }}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown') {
            event.preventDefault();
            setOpen(true);
            setActiveIndex((index) => Math.min(index + 1, filtered.length - 1));
          } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            setActiveIndex((index) => Math.max(index - 1, 0));
          } else if (event.key === 'Enter' && open && filtered[activeIndex]) {
            event.preventDefault();
            choose(filtered[activeIndex]);
          } else if (event.key === 'Escape') {
            setOpen(false);
            setQuery(selectedOption?.label || '');
          }
        }}
      />
      <span className="searchable-combobox__icon" aria-hidden="true">⌄</span>
      {open && !disabled && !loading && (
        <div className="searchable-combobox__menu" id={`${id}-listbox`} role="listbox">
          {filtered.length ? filtered.map((option, index) => (
            <button
              id={`${id}-option-${index}`}
              key={option.value}
              type="button"
              role="option"
              aria-selected={option.value === value}
              className={index === activeIndex ? 'active' : ''}
              onMouseDown={(event) => event.preventDefault()}
              onMouseEnter={() => setActiveIndex(index)}
              onClick={() => choose(option)}
            >
              <span>{option.label}</span>
              {option.value === value && <span aria-hidden="true">✓</span>}
            </button>
          )) : <p className="searchable-combobox__empty">Không tìm thấy địa điểm phù hợp.</p>}
        </div>
      )}
    </div>
  );
}
