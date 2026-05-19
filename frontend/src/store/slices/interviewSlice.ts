import { createAsyncThunk, createSlice, PayloadAction } from '@reduxjs/toolkit';
import { interviewService, CreateInterviewRequest } from '../services/interviewService';
import type { Interview } from '../types/interview';

interface InterviewState {
  interviews: Interview[];
  currentInterview: Interview | null;
  loading: boolean;
  error: string | null;
}

const initialState: InterviewState = {
  interviews: [],
  currentInterview: null,
  loading: false,
  error: null,
};

export const fetchInterviewsByApplication = createAsyncThunk<Interview[], number>(
  'interviews/fetchByApplication',
  async (applicationId) => {
    const interviews = await interviewService.getAllByApplication(applicationId);
    return interviews;
  }
);

export const createInterview = createAsyncThunk<Interview, CreateInterviewRequest>(
  'interviews/create',
  async (data) => {
    const interview = await interviewService.create(data);
    return interview;
  }
);

const interviewSlice = createSlice({
  name: 'interviews',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    setCurrentInterview: (state, action: PayloadAction<Interview | null>) => {
      state.currentInterview = action.payload;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchInterviewsByApplication.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchInterviewsByApplication.fulfilled, (state, action) => {
        state.loading = false;
        state.interviews = action.payload;
      })
      .addCase(fetchInterviewsByApplication.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch interviews';
      })
      .addCase(createInterview.fulfilled, (state, action) => {
        state.interviews.push(action.payload);
      });
  },
});

export const { clearError, setCurrentInterview } = interviewSlice.actions;
export default interviewSlice.reducer;