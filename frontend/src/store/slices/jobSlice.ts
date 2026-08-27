import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { jobService } from '@services/jobService';
import type { Job, JobFilters, JobApiResponse } from '@/types/job';

interface JobState {
  jobs: Job[];
  currentJob: Job | null;
  filters: JobFilters;
  pagination: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
  loading: boolean;
  error: string | null;
}

const initialState: JobState = {
  jobs: [],
  currentJob: null,
  filters: {},
  pagination: {
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
  },
  loading: false,
  error: null,
};

export const fetchJobs = createAsyncThunk<JobApiResponse, { filters: JobFilters; page: number; size: number }>(
  'jobs/fetchAll',
  async ({ filters, page, size }) => {
    const response = await jobService.getAll(filters, page, size);
    return response;
  }
);

export const fetchJobById = createAsyncThunk<Job, string>(
  'jobs/fetchById',
  async (id) => {
    const job = await jobService.getById(id);
    return job;
  }
);

const jobSlice = createSlice({
  name: 'jobs',
  initialState,
  reducers: {
    setFilters: (state, action: PayloadAction<JobFilters>) => {
      state.filters = action.payload;
    },
    clearCurrentJob: (state) => {
      state.currentJob = null;
    },
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchJobs.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchJobs.fulfilled, (state, action) => {
        state.loading = false;
        state.jobs = action.payload.content;
        state.pagination = {
          page: action.payload.page,
          size: action.payload.size,
          totalElements: action.payload.totalElements,
          totalPages: action.payload.totalPages,
        };
      })
      .addCase(fetchJobs.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch jobs';
      })
      .addCase(fetchJobById.fulfilled, (state, action) => {
        state.currentJob = action.payload;
      });
  },
});

export const { setFilters, clearCurrentJob, clearError } = jobSlice.actions;
export default jobSlice.reducer;
