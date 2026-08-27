# Smart Recruitment Portal - API Documentation

## Base URL
```
http://localhost:8080/api
```

## Authentication

All protected routes require a JWT token in the Authorization header:
```
Authorization: Bearer <token>
```

---

## Auth Endpoints

### POST /api/auth/register
Register a new user

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "role": "CANDIDATE"
}
```

**Response:** 201 Created
```json
{
  "token": "jwt_token_here",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "role": "CANDIDATE"
  }
}
```

### POST /api/auth/login
Login user

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:** 200 OK
```json
{
  "token": "jwt_token_here",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "role": "CANDIDATE"
  }
}
```

---

## Job Endpoints

### GET /api/jobs
Get all jobs with optional filters

**Query Parameters:**
- `search` - Search keyword (title, description)
- `location` - Filter by location
- `minSalary` - Minimum salary
- `maxSalary` - Maximum salary
- `page` - Page number (default: 0)
- `size` - Page size (default:10)

**Response:** 200 OK
```json
{
  "content": [
    {
      "id": 1,
      "title": "Senior Java Developer",
      "description": "We are looking for...",
      "salaryMin": 15000000,
      "salaryMax": 25000000,
      "location": "Ho Chi Minh City",
      "requirements": ["Java", "Spring Boot", "PostgreSQL"],
      "employer": {
        "id": 1,
        "name": "Tech Company"
      },
      "createdAt": "2026-05-19T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 50
}
```

### GET /api/jobs/{id}
Get job by ID

**Response:** 200 OK
```json
{
  "id": 1,
  "title": "Senior Java Developer",
  "description": "We are looking for...",
  "salaryMin": 15000000,
  "salaryMax": 25000000,
  "location": "Ho Chi Minh City",
  "requirements": ["Java", "Spring Boot", "PostgreSQL"],
  "employer": {
    "id": 1,
    "name": "Tech Company"
  },
  "createdAt": "2026-05-19T10:00:00Z"
}
```

### POST /api/jobs
Create new job (Employer only)

**Request Body:**
```json
{
  "title": "Senior Java Developer",
  "description": "We are looking for...",
  "salaryMin": 15000000,
  "salaryMax": 25000000,
  "location": "Ho Chi Minh City",
  "requirements": ["Java", "Spring Boot", "PostgreSQL"]
}
```

### PUT /api/jobs/{id}
Update job (Employer only)

### DELETE /api/jobs/{id}
Delete job (Employer only)

---

## Application Endpoints

### POST /api/applications
Apply for a job

**Request Body:**
```json
{
  "jobId": 1,
  "candidateId": 1
}
```

**Response:** 201 Created

### GET /api/applications/candidate/{candidateId}
Get all applications for a candidate

### GET /api/applications/employer/{employerId}
Get all applications for jobs posted by employer

### PUT /api/applications/{id}/status
Update application status

**Request Body:**
```json
{
  "status": "INTERVIEW_SCHEDULED"
}
```

---

## Interview Endpoints

### POST /api/interviews
Schedule interview

**Request Body:**
```json
{
  "applicationId": 1,
  "type": "AI_INTERVIEW",
  "scheduledAt": "2026-05-20T10:00:00Z",
  "durationMinutes": 30
}
```

### GET /api/interviews/application/{applicationId}
Get interviews for an application

### POST /api/interviews/{id}/responses
Submit interview response (AI Interview)

**Request Body:**
```json
{
  "questionId": 1,
  "answerText": "My answer...",
  "videoUrl": "https://..."
}
```

### GET /api/interviews/{id}/feedback
Get AI interview feedback

**Response:** 200 OK
```json
{
  "overallScore": 85.5,
  "feedback": "Good technical knowledge, communication could be improved.",
  "strengths": ["Strong Java skills", "Good problem-solving"],
  "weaknesses": ["Communication clarity"],
  "recommendations": ["Practice behavioral questions"]
}
```

---

## Assessment Endpoints

### POST /api/assessments
Create assessment (Employer only)

**Request Body:**
```json
{
  "jobId": 1,
  "title": "Java Technical Assessment",
  "type": "QUIZ",
  "durationMinutes": 60,
  "questions": [
    {
      "text": "What is the difference between == and equals() in Java?",
      "type": "MULTIPLE_CHOICE",
      "options": ["No difference", "== compares reference", "equals() compares reference", "Both same"],
      "correctAnswer": 1
    }
  ]
}
```

### GET /api/assessments/job/{jobId}
Get assessments for a job

### POST /api/assessments/{id}/submit
Submit assessment

**Request Body:**
```json
{
  "answers": [
    {
      "questionId": 1,
      "answer": 1
    }
  ]
}
```

**Response:** 200 OK
```json
{
  "score": 80,
  "totalQuestions": 10,
  "correctAnswers": 8,
  "feedback": "Good performance"
}
```

### GET /api/assessments/candidate/{candidateId}
Get all assessments for a candidate

---

## Matching Endpoints

### POST /api/matching/candidate/{candidateId}
Get job matches for a candidate

**Response:** 200 OK
```json
[
  {
    "job": {
      "id": 1,
      "title": "Senior Java Developer",
      "company": "Tech Company"
    },
    "matchScore": 92.5,
    "matchingSkills": ["Java", "Spring Boot", "PostgreSQL"],
    "missingSkills": ["Kubernetes"]
  }
]
```

### POST /api/matching/job/{jobId}
Get candidate matches for a job

**Response:** 200 OK
```json
[
  {
    "candidate": {
      "id": 1,
      "name": "John Doe",
      "email": "john@example.com"
    },
    "matchScore": 88.0,
    "matchingSkills": ["Java", "Spring Boot"],
    "missingSkills": ["React"]
  }
]
```

---

## Error Responses

### 400 Bad Request
```json
{
  "error": "Bad Request",
  "message": "Invalid input data",
  "details": ["Field 'email' is required"]
}
```

### 401 Unauthorized
```json
{
  "error": "Unauthorized",
  "message": "Invalid or expired token"
}
```

### 404 Not Found
```json
{
  "error": "Not Found",
  "message": "Job not found"
}
```

### 500 Internal Server Error
```json
{
  "error": "Internal Server Error",
  "message": "An unexpected error occurred"
}
```