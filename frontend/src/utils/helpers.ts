export const classNames = (...classes: (string | undefined | null | false)[]): string => {
  return classes.filter(Boolean).join(' ');
};

export const formatDate = (date: string): string => {
  return new Date(date).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
};

export const formatDateTime = (date: string): string => {
  return new Date(date).toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

export const formatCurrency = (amount: number, currency: string = 'VND'): string => {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency,
  }).format(amount);
};

export const truncate = (text: string, maxLength: number): string => {
  if (text.length <= maxLength) return text;
  return text.substring(0, maxLength) + '...';
};

export const validateEmail = (email: string): boolean => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
};

export const validatePassword = (password: string): { valid: boolean; errors: string[] } => {
  const errors: string[] = [];

  if (password.length < 8) {
    errors.push('Password must be at least 8 characters');
  }
  if (!/[A-Z]/.test(password)) {
    errors.push('Password must contain at least one uppercase letter');
  }
  if (!/[0-9]/.test(password)) {
    errors.push('Password must contain at least one number');
  }

  return { valid: errors.length === 0, errors };
};

/** Open file URL in a new tab (browser native image/PDF viewer or Google Docs Viewer). */
export const openFileInNewTab = (fileUrl?: string) => {
  if (!fileUrl) return;
  let url = fileUrl;
  if (url.includes('res.cloudinary.com') && url.includes('/raw/upload/') && !url.toLowerCase().includes('.pdf') && !url.toLowerCase().includes('.png') && !url.toLowerCase().includes('.jpg')) {
    url = url + '.pdf';
  }

  const lower = url.toLowerCase();
  if (lower.includes('.pdf') || lower.includes('/raw/upload/')) {
    window.open(`https://docs.google.com/gview?url=${encodeURIComponent(url)}`, '_blank', 'noopener,noreferrer');
  } else {
    window.open(url, '_blank', 'noopener,noreferrer');
  }
};

/**
 * Force download with original filename.
 * Falls back to opening the URL if CORS blocks the fetch (e.g. some CDNs).
 */
export const downloadFile = async (fileUrl?: string, fileName?: string) => {
  if (!fileUrl) return;
  let url = fileUrl;
  if (url.includes('res.cloudinary.com') && url.includes('/raw/upload/') && !url.toLowerCase().includes('.pdf') && !url.toLowerCase().includes('.png') && !url.toLowerCase().includes('.jpg')) {
    url = url + '.pdf';
  }

  let name = fileName?.trim() || 'tai_lieu.pdf';
  if (!name.includes('.')) {
    name += url.toLowerCase().includes('.pdf') ? '.pdf' : '.png';
  }

  try {
    const response = await fetch(url, { mode: 'cors' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = name;
    anchor.rel = 'noopener';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
  } catch {
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = name;
    anchor.target = '_blank';
    anchor.rel = 'noopener noreferrer';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  }
};