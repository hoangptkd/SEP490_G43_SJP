const SUPPORT_WAIT_MS = 15 * 60 * 1000;
export const ZALO_SUPPORT_PHONE = '0869601813';
export const ZALO_SUPPORT_URL = `https://zalo.me/${ZALO_SUPPORT_PHONE}`;

/** Hiện banner hỗ trợ sau 15 phút vẫn chưa xác nhận, hoặc khi đơn đã hết hạn/thất bại. */
export function shouldShowBankTransferSupport(opts: {
  status?: string | null;
  createdAt?: string | null;
  now?: number;
}) {
  const status = (opts.status || '').toLowerCase();
  if (['paid'].includes(status)) return false;
  if (['expired', 'failed', 'cancelled'].includes(status)) return true;
  if (!opts.createdAt) return false;
  const created = new Date(opts.createdAt).getTime();
  if (Number.isNaN(created)) return false;
  return (opts.now ?? Date.now()) - created >= SUPPORT_WAIT_MS;
}

export function BankTransferSupportBanner({
  compact = false,
}: {
  compact?: boolean;
}) {
  return (
    <div
      style={{
        background: '#ecfdf5',
        border: '1px solid #a7f3d0',
        borderRadius: compact ? 12 : 0,
        color: '#065f46',
        display: 'grid',
        gap: 8,
        margin: compact ? '16px 0 0' : undefined,
        padding: '14px 18px',
      }}
    >
      <strong style={{ fontSize: '0.95rem' }}>Cần hỗ trợ xác nhận thanh toán?</strong>
      <p style={{ margin: 0, fontSize: '0.9rem', lineHeight: 1.45 }}>
        Đơn đã quá 15 phút mà chưa được duyệt. Vui lòng nhắn Zalo kèm <strong>nội dung chuyển khoản</strong> và ảnh
        biên lai để được hỗ trợ nhanh:
      </p>
      <a
        href={ZALO_SUPPORT_URL}
        target="_blank"
        rel="noreferrer"
        style={{
          alignItems: 'center',
          background: '#0068ff',
          borderRadius: 10,
          color: '#fff',
          display: 'inline-flex',
          fontWeight: 700,
          gap: 8,
          justifyContent: 'center',
          padding: '10px 14px',
          textDecoration: 'none',
          width: 'fit-content',
        }}
      >
        Nhắn Zalo hỗ trợ · {ZALO_SUPPORT_PHONE}
      </a>
    </div>
  );
}
