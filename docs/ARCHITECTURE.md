# Architecture Documentation

## System Architecture

### Overview
Smart Recruitment Portal is a full-stack web application connecting job seekers with employers, featuring AI-powered interview assistance and skill assessment.

### Architecture Pattern
**Model-View-Controller (MVC)** with separation of concerns between frontend and backend.

---

## Frontend Architecture

### Component Hierarchy
```
App
├── Layout
│   ├── Header
│   ├── Sidebar
│   └── Footer
├── Pages
│   ├── Home
│   ├── Jobs
│   │   ├── JobSearch
│   │   ├── JobDetail
│   │   └── JobApplication
│   ├── Candidate
│   │   ├── CandidateProfile
│   │   ├── ApplicationStatus
│   │   └── AssessmentDashboard
│   ├── Interview
│   │   ├── AIInterview
│   │   ├── InterviewSchedule
│   │   └── InterviewFeedback
│   ├── Employer
│   │   ├── PostJob
│   │   ├── CandidateList
│   │   └── Dashboard
│   └── Auth
│       ├── Login
│       └── Register
└── Common Components
    ├── Button
    ├── Input
    ├── Modal
    ├── Card
    └── Table
```

### State Management
Using Redux Toolkit with the following slices:
- `authSlice`: User authentication state
- `jobSlice`: Job listing and filtering state
- `candidateSlice`: Candidate profile state
- `interviewSlice`: Interview scheduling and AI interview state
- `assessmentSlice`: Assessment taking state

### Data Flow
1. Components dispatch actions to Redux store
2. Redux reducers update state
3. Components subscribe to state changes via `useSelector`
4. API calls made via service layer using `axios`
5. Responses dispatched to update store

---

## Backend Architecture

### Layered Architecture
```
Controller Layer
       ↓
Service Layer
       ↓
Repository Layer
       ↓
   Database
```

### Components

#### Controllers
Handle HTTP requests and responses, request validation

#### Services
Business logic, data processing, AI integration

#### Repositories
Data access layer using Spring Data JPA

#### Models
- **Entities**: Database entities mapped to tables
- **DTOs**: Data Transfer Objects for request/response

### Security
- JWT-based authentication
- Role-based access control (RBAC)
- CORS configuration for frontend
- Password encryption with BCrypt

---

## Database Design

### Entity Relationship Diagram

```
users
├── candidate_profiles (1:1)
│   └── applications (1:N)
│       ├── jobs (N:1)
│       └── interviews (1:N)
│           └── interview_responses (1:N)
└── jobs (1:N)
    └── applications (N:1)
        └── assessments (1:N)
```

### Key Tables
- `users`: All user accounts (candidates, employers, admins)
- `candidate_profiles`: Extended candidate information
- `jobs`: Job postings
- `applications`: Job applications linking candidates to jobs
- `interviews`: Interview records
- `interview_responses`: AI interview answer records
- `assessments`: Skill assessments
- `questions`: Assessment questions

---

## AI Integration

### AI Interview Flow
1. Candidate starts AI interview
2. System presents questions sequentially
3. Candidate provides text/video responses
4. AI analyzes responses for:
   - Content relevance
   - Communication skills
   - Technical accuracy
5. Score and feedback generated

### Job Matching Algorithm
1. Extract skills from candidate profile
2. Extract skills from job requirements
3. Calculate similarity score based on:
   - Skill match percentage
   - Experience alignment
   - Location preference
4. Return ranked recommendations

---

## API Design

### RESTful Principles
- Resource-based URLs
- HTTP methods for operations (GET, POST, PUT, DELETE)
- Stateless communication
- JSON request/response format

### Versioning
API version in URL path: `/api/v1/...`

### Error Handling
Consistent error response format:
```json
{
  "error": "Error Type",
  "message": "Human readable message",
  "details": ["Additional information"]
}
```

---

## Deployment

### Environment Variables
- `DATABASE_URL`: PostgreSQL connection string
- `JWT_SECRET`: JWT signing secret
- `PORT`: Server port
- `FRONTEND_URL`: Frontend URL for CORS

### Docker
Containerized application with:
- Frontend container (Nginx + React)
- Backend container (Spring Boot)
- Database container (PostgreSQL)