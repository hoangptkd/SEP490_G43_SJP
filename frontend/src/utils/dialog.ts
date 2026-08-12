export type DialogOptions = {
  type: 'alert' | 'confirm' | 'prompt';
  message: string;
  title?: string;
  defaultValue?: string;
  onClose: (result: any) => void;
};

let dialogListener: ((options: DialogOptions) => void) | null = null;

export const setDialogListener = (listener: (options: DialogOptions) => void) => {
  dialogListener = listener;
};

export const customAlert = (message: string, title?: string): Promise<void> => {
  return new Promise((resolve) => {
    if (dialogListener) {
      dialogListener({ type: 'alert', message, title, onClose: () => resolve() });
    } else {
      window.alert(message);
      resolve();
    }
  });
};

export const customConfirm = (message: string, title?: string): Promise<boolean> => {
  return new Promise((resolve) => {
    if (dialogListener) {
      dialogListener({ type: 'confirm', message, title, onClose: resolve });
    } else {
      resolve(window.confirm(message));
    }
  });
};

export const customPrompt = (message: string, defaultValue: string = '', title?: string): Promise<string | null> => {
  return new Promise((resolve) => {
    if (dialogListener) {
      dialogListener({ type: 'prompt', message, defaultValue, title, onClose: resolve });
    } else {
      resolve(window.prompt(message, defaultValue));
    }
  });
};
