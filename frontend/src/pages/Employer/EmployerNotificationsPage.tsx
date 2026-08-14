import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { employerService } from '../../services/employerService';
import type { NotificationItem } from '../../types/candidateDomain';
import { NotificationInbox } from '../../components/NotificationInbox';

const fadeUp = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0 },
};
const EASE_OUT = [0.23, 1, 0.32, 1] as const;

export default function EmployerNotificationsPage() {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);

  async function load() {
    const data = await employerService.getNotifications();
    setItems(data);
    setLoading(false);
  }

  useEffect(() => { void load(); }, []);

  const unreadCount = items.filter((item) => !item.read).length;

  const getNotificationLink = (item: NotificationItem) => {
    if (item.relatedEntityType === 'JOB' && item.relatedEntityId) {
      return `/employer/jobs`;
    }
    if (item.relatedEntityType === 'APPLICATION' && item.relatedEntityId) {
      return `/employer/applications?appId=${item.relatedEntityId}`;
    }
    return null;
  };

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Thông báo</h1>
        <p>
          Cập nhật từ ứng viên và hệ thống
          {unreadCount > 0 ? ` · ${unreadCount} chưa đọc` : ''}
        </p>
      </div>

      <NotificationInbox
        items={items}
        loading={loading}
        emptyTitle="Không có thông báo mới"
        emptyHint="Bạn sẽ nhận thông báo khi có hồ sơ ứng tuyển mới hoặc thông báo từ Admin."
        getLink={getNotificationLink}
        onMarkRead={(id) => { void employerService.markNotificationRead(id).then(load); }}
        onMarkAllRead={() => { void employerService.markAllNotificationsRead().then(load); }}
      />
    </motion.div>
  );
}
