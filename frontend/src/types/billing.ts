export interface PlanCatalogItem {
  id: string;
  name: string;
  targetRole: string;
  description?: string;
  price: number;
  currency: string;
  durationDays: number;
  benefits: string[];
  sortOrder: number;
  maxJobs?: number | null;
  maxCv?: number | null;
  maxApplicationsPerDay?: number | null;
  maxAiSessionsPerDay?: number | null;
  listingPriority?: number | null;
}

export interface FeatureUsage {
  featureKey: string;
  label: string;
  used: number;
  limit: number;
  daily: boolean;
}

export interface BankTransferInfo {
  paymentId: string;
  orderCode?: string;
  planName?: string;
  bankName: string;
  bankCode?: string;
  accountNumber: string;
  accountName: string;
  branch?: string;
  transferContent: string;
  amount: number;
  currency: string;
  qrUrl?: string;
  createdAt?: string;
  expiresAt?: string;
  status?: string;
}

export interface CheckoutResult {
  paymentId: string;
  subscriptionId: string;
  status: string;
  payUrl?: string | null;
  message: string;
  paymentMethod?: string;
  bankTransfer?: BankTransferInfo | null;
}

export interface PaymentStatus {
  id: string;
  status: string;
  amount: number;
  currency: string;
  paymentMethod?: string;
  planName?: string;
  subscriptionStatus?: string;
  paidAt?: string;
  failureReason?: string;
  createdAt?: string;
}
