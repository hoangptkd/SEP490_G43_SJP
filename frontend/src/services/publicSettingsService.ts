import { api } from './api';

export type PublicSettings = {
  siteName: string;
  supportEmail: string;
  maintenanceMode: boolean;
  aiInterviewEnabled: boolean;
  paymentGatewayEnabled: boolean;
  paymentGatewayProvider: string;
  themeMode: string;
  themePrimaryColor: string;
};

const DEFAULT_SETTINGS: PublicSettings = {
  siteName: 'Smart Recruitment Portal',
  supportEmail: 'support@sjp.local',
  maintenanceMode: false,
  aiInterviewEnabled: true,
  paymentGatewayEnabled: true,
  paymentGatewayProvider: 'payos',
  themeMode: 'light',
  themePrimaryColor: '#00507d',
};

export const publicSettingsService = {
  get: async (): Promise<PublicSettings> => {
    const response = await api.get<PublicSettings>('/settings/public');
    return { ...DEFAULT_SETTINGS, ...response.data };
  },
  defaults: DEFAULT_SETTINGS,
};
