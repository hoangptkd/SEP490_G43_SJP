import React, { useEffect, useState } from 'react';
import { setDialogListener, DialogOptions } from '../../utils/dialog';
import { Modal } from './Modal';

export const DialogContainer: React.FC = () => {
  const [dialog, setDialog] = useState<DialogOptions | null>(null);
  const [inputValue, setInputValue] = useState('');

  useEffect(() => {
    setDialogListener((options) => {
      setDialog(options);
      if (options.type === 'prompt') {
        setInputValue(options.defaultValue || '');
      }
    });
    return () => setDialogListener(() => {});
  }, []);

  const handleClose = (result: any) => {
    if (dialog) {
      dialog.onClose(result);
      setDialog(null);
    }
  };

  if (!dialog) return null;

  return (
    <Modal
      isOpen={true}
      onClose={() => handleClose(false)}
      title={dialog.title || (dialog.type === 'confirm' ? 'Xác nhận' : 'Thông báo')}
    >
      <div className="py-2">
        <p className="text-gray-700 text-base mb-4">{dialog.message}</p>
        {dialog.type === 'prompt' && (
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500"
            autoFocus
          />
        )}
      </div>
      <div className="mt-6 flex justify-end gap-3">
        {(dialog.type === 'confirm' || dialog.type === 'prompt') && (
          <button
            onClick={() => handleClose(dialog.type === 'prompt' ? null : false)}
            className="button outline"
          >
            Hủy
          </button>
        )}
        <button
          onClick={() => handleClose(dialog.type === 'prompt' ? inputValue : true)}
          className={`button primary`}
        >
          {dialog.type === 'alert' ? 'Đóng' : 'Đồng ý'}
        </button>
      </div>
    </Modal>
  );
};
