export interface User {
  id: number;
  email: string;
  role: 'CANDIDATE' | 'EMPLOYER' | 'ADMIN';
  createdAt: string;
}

export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}