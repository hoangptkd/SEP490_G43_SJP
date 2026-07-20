# Realistic UI Sample Data

Purpose: natural English sample data for Google Stitch UI exploration. This file is documentation only. It must not be used as database seed data.

## Data Rules
- Use existing frontend/backend fields where they are known.
- Sections marked `Optional design placeholder` come from schema-only or not-yet-wired UI areas. They are for visual exploration only.
- Keep these sample records consistent across screens.
- Include full, sparse, empty, desktop, mobile, and overflow cases.

## Shared Sample IDs
Use stable-looking IDs for visual references:
- Candidate profile id: `8d0d6c0e-8db6-41ad-9325-7f596e70c101`
- Candidate user id: `c45f70ea-409d-4960-876f-f39a8d90a201`
- Employer id: `f926c6be-5144-4cde-9adb-9bfb68771112`
- Employer user id: `5d774a41-7867-49d2-8f60-8df40fdf4f01`
- Company id: `28ce5b31-3c7a-4148-b0f0-33fd35d9e201`
- Primary job id: `74c8fb3a-96f4-4d3f-a1a8-6d4d6df2a901`
- Secondary job id: `f0c2229e-8d65-4989-9ad8-85fd3b57b902`
- Application id: `b0bb0d23-16c4-4c54-9c68-07043a5e6401`

## Candidate Profile
Full data:
```json
{
  "id": "8d0d6c0e-8db6-41ad-9325-7f596e70c101",
  "userId": "c45f70ea-409d-4960-876f-f39a8d90a201",
  "fullName": "Nguyen Minh Anh",
  "phone": "+84 912 345 678",
  "location": "Hanoi, Vietnam",
  "bio": "Backend developer with 3 years of experience building Spring Boot APIs, PostgreSQL data models, and internal hiring workflow tools. Looking for a role where clean architecture, practical product thinking, and reliable delivery matter.",
  "skills": ["Java", "Spring Boot", "PostgreSQL", "React", "Docker", "REST APIs", "Redis"],
  "education": [],
  "workExperience": [],
  "projects": [],
  "certifications": [],
  "applyReady": true
}
```

Sparse data:
```json
{
  "id": "d4dcaf1e-22db-4393-bc3d-a76a8791c801",
  "userId": "fbe40e53-9b48-4e08-bb1f-0d38f30a1c01",
  "fullName": "Le Bao Tran",
  "phone": "",
  "location": "Da Nang, Vietnam",
  "bio": "",
  "skills": ["HTML", "CSS"],
  "education": [],
  "workExperience": [],
  "projects": [],
  "certifications": [],
  "applyReady": false
}
```

Empty state:
- No candidate profile loaded yet: show a loading state first.
- If profile is incomplete: show a clear completion prompt, not an empty table.

Overflow test string:
- `bio`: "I worked on a cross-functional recruitment operations platform where one candidate profile could be connected to multiple CV versions, saved jobs, AI interview practice sessions, application timelines, and notification events, so the UI must handle long professional summaries without breaking the card layout or hiding the save button."

## Employer Profile
Known fields come from `Employer` and related `User`/`Company`.
```json
{
  "id": "f926c6be-5144-4cde-9adb-9bfb68771112",
  "user": {
    "id": "5d774a41-7867-49d2-8f60-8df40fdf4f01",
    "email": "huy.tran@fptsoftware.com",
    "fullName": "Tran Quang Huy",
    "phone": "+84 903 221 909",
    "avatarUrl": "",
    "role": "employer",
    "status": "active"
  },
  "companyId": "28ce5b31-3c7a-4148-b0f0-33fd35d9e201",
  "position": "Talent Acquisition Manager",
  "verificationStatus": "verified",
  "owner": true
}
```

Sparse data:
```json
{
  "id": "1f8cfec8-2f7f-4bc5-b4c3-84721afc2001",
  "user": {
    "email": "recruiting@newteam.vn",
    "fullName": "",
    "phone": "",
    "role": "employer",
    "status": "active"
  },
  "companyId": "de10ff31-d1c5-482d-8271-06d2bf6af901",
  "position": "",
  "verificationStatus": "pending",
  "owner": false
}
```

## Company Profile
```json
{
  "id": "28ce5b31-3c7a-4148-b0f0-33fd35d9e201",
  "name": "FPT Software",
  "logoUrl": "https://example.com/logos/fpt-software.png",
  "description": "FPT Software is a global technology and IT services company delivering software engineering, cloud migration, AI, data, and digital transformation projects for enterprise customers across Asia, Europe, and North America.",
  "website": "https://fptsoftware.com",
  "industry": "Information Technology",
  "location": "FPT Tower, Cau Giay, Hanoi",
  "companySize": 30000,
  "taxCode": "0101601092",
  "verificationStatus": "verified",
  "status": "active"
}
```

Sparse company:
```json
{
  "id": "de10ff31-d1c5-482d-8271-06d2bf6af901",
  "name": "BrightPath Labs",
  "logoUrl": "",
  "description": "",
  "website": "",
  "industry": "",
  "location": "",
  "companySize": null,
  "taxCode": "",
  "verificationStatus": "unverified",
  "status": "pending"
}
```

Overflow test:
- Company name: `International Applied Data Platform Engineering and Recruitment Operations Center Vietnam Limited`
- Description: `A long company description should wrap across multiple lines while preserving the verification badge, logo upload control, and save action without pushing important controls outside the viewport.`

## Company Branches
```json
[
  {
    "id": "8c858012-4f2d-4d9a-9e9d-13242d4a1001",
    "branchName": "Hanoi Head Office",
    "address": "FPT Tower, 10 Pham Van Bach Street",
    "city": "Hanoi",
    "district": "Cau Giay",
    "country": "Vietnam",
    "headquarter": true
  },
  {
    "id": "8c858012-4f2d-4d9a-9e9d-13242d4a1002",
    "branchName": "Ho Chi Minh City Delivery Center",
    "address": "High-Tech Park, District 9",
    "city": "Ho Chi Minh City",
    "district": "Thu Duc City",
    "country": "Vietnam",
    "headquarter": false
  },
  {
    "id": "8c858012-4f2d-4d9a-9e9d-13242d4a1003",
    "branchName": "Da Nang Engineering Hub With A Very Long Branch Name For Overflow Testing",
    "address": "FPT Complex, Nam Ky Khoi Nghia Street",
    "city": "Da Nang",
    "district": "Ngu Hanh Son",
    "country": "Vietnam",
    "headquarter": false
  }
]
```

Empty state:
```json
[]
```

## Job Categories
```json
[
  {
    "id": "7b8e2df3-13b0-4f6b-b90b-50b25890c001",
    "name": "Software Engineering",
    "slug": "software-engineering",
    "parentId": null,
    "description": "Backend, frontend, full-stack, mobile, QA, DevOps, and platform engineering roles.",
    "status": "active"
  },
  {
    "id": "7b8e2df3-13b0-4f6b-b90b-50b25890c002",
    "name": "Data and AI",
    "slug": "data-and-ai",
    "parentId": null,
    "description": "Data engineering, analytics, machine learning, and AI product roles.",
    "status": "active"
  },
  {
    "id": "7b8e2df3-13b0-4f6b-b90b-50b25890c003",
    "name": "Product and Design",
    "slug": "product-and-design",
    "parentId": null,
    "description": "Product management, UX research, UI design, and product operations.",
    "status": "active"
  }
]
```

## Skills
```json
[
  {"id": "skill-001", "name": "Java", "slug": "java", "category": "Backend", "description": "Object-oriented backend development and enterprise application programming."},
  {"id": "skill-002", "name": "Spring Boot", "slug": "spring-boot", "category": "Backend", "description": "Production-grade REST APIs, security, validation, and service integration."},
  {"id": "skill-003", "name": "PostgreSQL", "slug": "postgresql", "category": "Database", "description": "Relational schema design, SQL queries, indexes, and migrations."},
  {"id": "skill-004", "name": "React", "slug": "react", "category": "Frontend", "description": "Component-based frontend development with modern routing and state management."},
  {"id": "skill-005", "name": "Docker", "slug": "docker", "category": "DevOps", "description": "Containerized local development and deployment workflows."}
]
```

## Job Posts
Primary job:
```json
{
  "id": "74c8fb3a-96f4-4d3f-a1a8-6d4d6df2a901",
  "title": "Senior Java Spring Boot Developer",
  "description": "Build and maintain recruitment workflow services, candidate matching APIs, and integration endpoints for enterprise hiring teams.",
  "requirements": [
    "3+ years of backend development experience with Java and Spring Boot",
    "Strong SQL and PostgreSQL schema design experience",
    "Comfortable with REST APIs, authentication, Docker, and production troubleshooting"
  ],
  "skills": ["Java", "Spring Boot", "PostgreSQL", "Docker", "REST APIs"],
  "benefits": "13th-month salary, annual health check, hybrid work, learning budget, performance bonus, and mentorship from senior architects.",
  "vacancies": 3,
  "workingTime": "Monday to Friday, 08:30 - 17:30",
  "salaryType": "range",
  "salaryMin": 25000000,
  "salaryMax": 42000000,
  "location": "Hanoi Head Office",
  "companyLocationId": "8c858012-4f2d-4d9a-9e9d-13242d4a1001",
  "jobType": "full_time",
  "workMode": "hybrid",
  "experienceLevel": "senior",
  "status": "published",
  "deadline": "2026-08-30",
  "viewsCount": 482,
  "saved": true,
  "applied": true,
  "matchScore": 92
}
```

More jobs:
```json
[
  {
    "id": "f0c2229e-8d65-4989-9ad8-85fd3b57b902",
    "title": "Frontend React Developer",
    "description": "Create responsive hiring dashboards, candidate-facing application flows, and reusable UI components.",
    "requirements": ["2+ years with React", "Good understanding of responsive UI", "Experience consuming REST APIs"],
    "skills": ["React", "TypeScript", "REST APIs", "CSS"],
    "salaryType": "range",
    "salaryMin": 18000000,
    "salaryMax": 30000000,
    "location": "Ho Chi Minh City Delivery Center",
    "jobType": "full_time",
    "workMode": "onsite",
    "experienceLevel": "middle",
    "status": "pending_review",
    "deadline": "2026-09-15",
    "viewsCount": 137,
    "saved": false,
    "applied": false,
    "matchScore": 76
  },
  {
    "id": "bdabfc50-e5e5-4ce8-bff5-41aa0a631901",
    "title": "Data Analyst Intern",
    "description": "Support recruitment funnel reporting, dashboard QA, and monthly hiring insights.",
    "requirements": ["Basic SQL", "Attention to detail", "Clear written communication"],
    "skills": ["SQL", "Excel", "Data Visualization"],
    "salaryType": "negotiable",
    "salaryMin": null,
    "salaryMax": null,
    "location": "Da Nang Engineering Hub",
    "jobType": "internship",
    "workMode": "hybrid",
    "experienceLevel": "intern",
    "status": "draft",
    "deadline": "2026-10-01",
    "viewsCount": 0,
    "saved": false,
    "applied": false,
    "matchScore": 64
  }
]
```

Overflow title:
- `Principal Platform Reliability Engineer for Distributed Recruitment Matching, Interview Automation, Candidate Ranking, and Workflow Observability`

Empty state:
```json
[]
```

## Job Recommendations
Uses frontend `Recommendation` shape.
```json
[
  {
    "job": "Use primary job object: Senior Java Spring Boot Developer",
    "matchScore": 92,
    "matchedSkills": ["Java", "Spring Boot", "PostgreSQL", "Docker"],
    "missingSkills": ["Kubernetes"],
    "reason": "Your backend experience, PostgreSQL background, and Spring Boot projects strongly match the role requirements.",
    "lowConfidence": false
  },
  {
    "job": "Use secondary job object: Frontend React Developer",
    "matchScore": 76,
    "matchedSkills": ["React", "REST APIs"],
    "missingSkills": ["Advanced TypeScript", "Design systems"],
    "reason": "You have React experience, but your profile is stronger on backend services than frontend UI ownership.",
    "lowConfidence": false
  }
]
```

Low-data recommendation:
```json
{
  "job": "Use Data Analyst Intern job object",
  "matchScore": 41,
  "matchedSkills": ["SQL"],
  "missingSkills": ["Data Visualization", "Excel"],
  "reason": "Needs more candidate profile data before the match can be trusted.",
  "lowConfidence": true
}
```

Empty state:
```json
[]
```

## Saved Jobs
```json
[
  "Use primary job object: Senior Java Spring Boot Developer",
  {
    "id": "9c8c80f2-8d7f-499d-8414-a820a07e3201",
    "title": "Backend API Engineer",
    "description": "Develop integration APIs for employer systems and internal recruitment workflows.",
    "requirements": ["Java", "REST APIs", "PostgreSQL"],
    "skills": ["Java", "REST APIs", "PostgreSQL"],
    "salaryType": "range",
    "salaryMin": 22000000,
    "salaryMax": 36000000,
    "location": "Hanoi",
    "jobType": "full_time",
    "workMode": "hybrid",
    "experienceLevel": "middle",
    "status": "published",
    "deadline": "2026-09-05",
    "viewsCount": 203,
    "saved": true,
    "applied": false,
    "matchScore": 88
  }
]
```

Empty state:
```json
[]
```

## Applications
```json
[
  {
    "id": "b0bb0d23-16c4-4c54-9c68-07043a5e6401",
    "job": "Use primary job object: Senior Java Spring Boot Developer",
    "cv": {
      "id": "2d45e380-c21e-4b93-9b6d-9f4129333c01",
      "originalFileName": "Nguyen-Minh-Anh-Backend-CV.pdf",
      "contentType": "application/pdf",
      "fileSize": 384240,
      "defaultCv": true,
      "deleted": false,
      "createdAt": "2026-07-02T09:10:00"
    },
    "status": "INTERVIEW_SCHEDULED",
    "submittedAt": "2026-07-06T10:15:00",
    "updatedAt": "2026-07-12T15:30:00",
    "timeline": [
      {
        "id": "history-001",
        "fromStatus": null,
        "toStatus": "SUBMITTED",
        "publicNote": "Your application was submitted successfully.",
        "createdAt": "2026-07-06T10:15:00"
      },
      {
        "id": "history-002",
        "fromStatus": "SUBMITTED",
        "toStatus": "UNDER_REVIEW",
        "publicNote": "The employer is reviewing your CV and profile.",
        "createdAt": "2026-07-08T14:20:00"
      },
      {
        "id": "history-003",
        "fromStatus": "UNDER_REVIEW",
        "toStatus": "INTERVIEW_SCHEDULED",
        "publicNote": "You have been invited to a technical interview.",
        "createdAt": "2026-07-12T15:30:00"
      }
    ]
  }
]
```

Sparse application:
```json
{
  "id": "b7bf76b6-7546-477b-8af5-e077a091e001",
  "job": "Use Backend API Engineer saved job object",
  "cv": null,
  "cvVersion": null,
  "status": "SUBMITTED",
  "submittedAt": "2026-07-13T08:25:00",
  "updatedAt": "2026-07-13T08:25:00",
  "timeline": []
}
```

Empty state:
```json
[]
```

## Application Statuses
Frontend labels should handle:
```json
[
  "SUBMITTED",
  "UNDER_REVIEW",
  "SHORTLISTED",
  "INTERVIEW_SCHEDULED",
  "INTERVIEWED",
  "EVALUATED",
  "ACCEPTED",
  "REJECTED",
  "HIRED"
]
```

Backend/database values also seen:
```json
["applied", "reviewed", "shortlisted", "interview_scheduled", "accepted", "rejected", "withdrawn"]
```

## Interview Schedules
Optional design placeholder: schema exists, but no current frontend screen/service is wired for this workflow.

Use schema fields only:
```json
[
  {
    "id": "0bd99435-3d3d-438a-b468-a97cedd40101",
    "application_id": "b0bb0d23-16c4-4c54-9c68-07043a5e6401",
    "employer_id": "f926c6be-5144-4cde-9adb-9bfb68771112",
    "job_seeker_id": "8d0d6c0e-8db6-41ad-9325-7f596e70c101",
    "round_number": 1,
    "scheduled_at": "2026-07-18T09:30:00+07:00",
    "meeting_link": "https://meet.example.com/fpt-senior-java-round-1",
    "location": "Online",
    "status": "scheduled",
    "note": "Technical discussion with backend team lead. Candidate should prepare one project walkthrough."
  },
  {
    "id": "0bd99435-3d3d-438a-b468-a97cedd40102",
    "application_id": "b0bb0d23-16c4-4c54-9c68-07043a5e6401",
    "employer_id": "f926c6be-5144-4cde-9adb-9bfb68771112",
    "job_seeker_id": "8d0d6c0e-8db6-41ad-9325-7f596e70c101",
    "round_number": 2,
    "scheduled_at": "2026-07-22T14:00:00+07:00",
    "meeting_link": "",
    "location": "Hanoi Head Office",
    "status": "rescheduled",
    "note": "Rescheduled due to interviewer availability."
  }
]
```

Empty state:
```json
[]
```

## AI Interview Coach Sessions
Uses frontend `AiInterviewSession` shape.
```json
[
  {
    "id": "21c4ef07-4e50-4645-817a-67f7cc3e5101",
    "title": "Senior Java Spring Boot Developer practice",
    "contextType": "application",
    "status": "completed",
    "totalQuestions": 5,
    "overallScore": 84,
    "applicationId": "b0bb0d23-16c4-4c54-9c68-07043a5e6401",
    "job": "Use primary job object",
    "practiceContext": {},
    "startedAt": "2026-07-11T20:10:00",
    "completedAt": "2026-07-11T20:34:00",
    "createdAt": "2026-07-11T20:08:00",
    "updatedAt": "2026-07-11T20:34:00",
    "questions": [
      {
        "id": "q-001",
        "orderIndex": 1,
        "questionType": "technical",
        "content": "Explain how you would design a Spring Boot service that handles job applications, status history, and notification events reliably.",
        "difficulty": "senior",
        "skillTag": "Spring Boot",
        "timeLimitSeconds": 180,
        "answer": {
          "id": "a-001",
          "questionId": "q-001",
          "transcript": "I would separate application submission, status transitions, and notifications into service boundaries with transactional updates for application state and an async event for notifications.",
          "skipped": false,
          "transcriptStatus": "completed",
          "feedbackStatus": "completed",
          "answeredAt": "2026-07-11T20:15:00"
        }
      }
    ],
    "summary": {
      "overallScore": 84,
      "summary": "Strong backend structure and practical trade-off awareness. Improve specificity around failure recovery and idempotency.",
      "strengths": ["Clear service boundaries", "Good database awareness", "Practical communication"],
      "weaknesses": ["Could explain retry strategy in more detail", "Needs stronger examples for observability"],
      "improvementPlan": ["Prepare one detailed architecture story", "Practice concise STAR examples", "Review idempotent event processing"],
      "source": "provider",
      "fallback": false
    }
  },
  {
    "id": "21c4ef07-4e50-4645-817a-67f7cc3e5102",
    "title": "Backend Developer free practice",
    "contextType": "practice",
    "status": "in_progress",
    "totalQuestions": 5,
    "overallScore": null,
    "practiceContext": {
      "targetRole": "Backend Developer",
      "skills": ["Java", "PostgreSQL", "Docker"]
    },
    "questions": [],
    "summary": null
  }
]
```

Empty state:
```json
[]
```

## Interview Feedback
Uses frontend `AiInterviewFeedback` and `AiInterviewSummary` shapes.
```json
{
  "id": "feedback-001",
  "score": 86,
  "feedback": "Your answer explains the core service design well and shows awareness of transactional consistency. Add more detail about retry policies, dead-letter handling, and monitoring alerts to make the answer stronger for a senior-level interview.",
  "strengths": ["Structured answer", "Strong API and database reasoning", "Clear communication"],
  "weaknesses": ["Limited failure-mode detail", "No concrete metric or alert examples"],
  "suggestions": ["Mention idempotency keys", "Describe what happens when notification delivery fails", "Add one real production incident example"],
  "source": "provider",
  "fallback": false
}
```

Fallback feedback:
```json
{
  "id": "feedback-002",
  "score": 60,
  "feedback": "Fallback evaluation was generated because the AI provider was temporarily unavailable. Treat this as a practice-only estimate.",
  "strengths": [],
  "weaknesses": ["Needs AI retry for reliable scoring"],
  "suggestions": ["Retry feedback later"],
  "source": "fallback",
  "fallback": true
}
```

## Candidate Ranking
Optional design placeholder: schema exists for `ai_ranking_jobs` and `ai_ranking_results`, but no current frontend screen/service is wired.

Ranking job:
```json
{
  "id": "rank-job-001",
  "employer_id": "f926c6be-5144-4cde-9adb-9bfb68771112",
  "job_id": "74c8fb3a-96f4-4d3f-a1a8-6d4d6df2a901",
  "ranking_criteria": {
    "mustHaveSkills": ["Java", "Spring Boot", "PostgreSQL"],
    "niceToHaveSkills": ["Docker", "Redis"],
    "minimumExperienceLevel": "middle"
  },
  "status": "completed",
  "total_candidates": 24,
  "processed_candidates": 24,
  "jd_snapshot_hash": "jd-20260713-senior-java",
  "error_message": null,
  "model_used": "matching-v1",
  "requested_at": "2026-07-13T09:00:00+07:00",
  "started_at": "2026-07-13T09:00:20+07:00",
  "completed_at": "2026-07-13T09:03:40+07:00"
}
```

Ranking results:
```json
[
  {
    "id": "rank-result-001",
    "ranking_job_id": "rank-job-001",
    "application_id": "b0bb0d23-16c4-4c54-9c68-07043a5e6401",
    "rank_position": 1,
    "match_score": 92.40,
    "ai_summary": "Strong Java/Spring Boot match with relevant PostgreSQL and Docker experience.",
    "score_breakdown": {
      "skills": 94,
      "experience": 88,
      "location": 95,
      "profileCompleteness": 90
    }
  }
]
```

## Employer Internal Notes
Optional design placeholder: schema exists as `application_internal_notes`, but no current frontend screen/service is wired.

Use schema fields only:
```json
[
  {
    "id": "note-001",
    "application_id": "b0bb0d23-16c4-4c54-9c68-07043a5e6401",
    "employer_id": "f926c6be-5144-4cde-9adb-9bfb68771112",
    "note": "Strong technical profile. Ask follow-up questions about event-driven notification failure handling and database migration strategy.",
    "created_at": "2026-07-12T16:10:00+07:00",
    "updated_at": "2026-07-12T16:10:00+07:00"
  },
  {
    "id": "note-002",
    "application_id": "b0bb0d23-16c4-4c54-9c68-07043a5e6401",
    "employer_id": "f926c6be-5144-4cde-9adb-9bfb68771112",
    "note": "Overflow test: Candidate mentioned a very long enterprise platform migration involving legacy applicant tracking data, multi-tenant notification rules, audit logging, delayed email delivery, and compliance reporting across multiple hiring regions.",
    "created_at": "2026-07-13T11:20:00+07:00",
    "updated_at": "2026-07-13T11:22:00+07:00"
  }
]
```

## Notifications
Uses frontend `NotificationItem` shape.
```json
[
  {
    "id": "notif-001",
    "type": "application_status",
    "title": "Interview scheduled",
    "message": "FPT Software scheduled a technical interview for Senior Java Spring Boot Developer on July 18 at 09:30.",
    "read": false,
    "relatedEntityType": "application",
    "relatedEntityId": "b0bb0d23-16c4-4c54-9c68-07043a5e6401",
    "createdAt": "2026-07-12T15:35:00"
  },
  {
    "id": "notif-002",
    "type": "ai_interview",
    "title": "AI feedback is ready",
    "message": "Your AI interview practice summary is ready. Review your strengths, improvement areas, and suggested practice plan.",
    "read": true,
    "relatedEntityType": "ai_interview_session",
    "relatedEntityId": "21c4ef07-4e50-4645-817a-67f7cc3e5101",
    "createdAt": "2026-07-11T20:35:00"
  }
]
```

Long notification:
```json
{
  "id": "notif-long",
  "type": "application_status",
  "title": "Application update with a long title that should wrap cleanly in both desktop tables and mobile cards",
  "message": "The employer updated your application and added a detailed public note explaining the next steps, the expected interview preparation topics, and the timeline for follow-up communication after the technical discussion.",
  "read": false,
  "relatedEntityType": "application",
  "relatedEntityId": "b0bb0d23-16c4-4c54-9c68-07043a5e6401",
  "createdAt": "2026-07-13T13:45:00"
}
```

Empty state:
```json
[]
```

## Recruitment Packages
Uses `Plan`, `Subscription`, and frontend `SubscriptionView` fields.

Plans:
```json
[
  {
    "id": "plan-001",
    "name": "Candidate Pro",
    "targetRole": "job_seeker",
    "description": "For candidates who want stronger visibility and more AI practice.",
    "price": 99000,
    "currency": "VND",
    "durationDays": 30,
    "features": {
      "benefits": ["More saved jobs", "More CV uploads", "AI interview practice history", "Priority recommendations"]
    },
    "status": "active",
    "sortOrder": 1
  },
  {
    "id": "plan-002",
    "name": "Employer Growth",
    "targetRole": "employer",
    "description": "For hiring teams that need more active job posts and candidate review support.",
    "price": 1499000,
    "currency": "VND",
    "durationDays": 30,
    "features": {
      "benefits": ["More active job postings", "Company verification support", "Candidate ranking placeholder", "Recruitment analytics placeholder"]
    },
    "status": "active",
    "sortOrder": 2
  }
]
```

Candidate subscription view:
```json
{
  "planCode": "Candidate Pro",
  "planName": "Candidate Pro",
  "status": "active",
  "price": 99000,
  "benefits": ["More saved jobs", "More CV uploads", "AI interview practice history", "Priority recommendations"],
  "startedAt": "2026-07-01T00:00:00",
  "expiresAt": "2026-07-31T23:59:59",
  "savedJobsCount": 8,
  "cvCount": 3,
  "unreadNotificationsCount": 2
}
```

Sparse/free subscription view:
```json
{
  "planCode": "Free",
  "planName": "Free",
  "status": "active",
  "price": 0,
  "benefits": ["Basic job search", "Limited saved jobs", "One CV upload"],
  "startedAt": null,
  "expiresAt": null,
  "savedJobsCount": 0,
  "cvCount": 0,
  "unreadNotificationsCount": 0
}
```

## Transactions
Optional design placeholder: schema exists as `payments`, but no current frontend screen/service is wired.

Use schema fields only:
```json
[
  {
    "id": "pay-001",
    "subscription_id": "sub-001",
    "user_id": "c45f70ea-409d-4960-876f-f39a8d90a201",
    "amount": 99000,
    "currency": "VND",
    "payment_method": "vnpay",
    "gateway": "VNPAY",
    "gateway_order_id": "SJP-20260701-0001",
    "status": "paid",
    "transaction_id": "VNP-20260701-887201",
    "gateway_response": {},
    "failure_reason": null,
    "paid_at": "2026-07-01T09:03:20+07:00",
    "created_at": "2026-07-01T09:01:45+07:00"
  },
  {
    "id": "pay-002",
    "subscription_id": "sub-002",
    "user_id": "5d774a41-7867-49d2-8f60-8df40fdf4f01",
    "amount": 1499000,
    "currency": "VND",
    "payment_method": "bank_transfer",
    "gateway": null,
    "gateway_order_id": "SJP-20260710-0042",
    "status": "pending",
    "transaction_id": "BANK-PENDING-0042",
    "gateway_response": {},
    "failure_reason": null,
    "paid_at": null,
    "created_at": "2026-07-10T16:40:00+07:00"
  },
  {
    "id": "pay-003",
    "subscription_id": "sub-003",
    "user_id": "fbe40e53-9b48-4e08-bb1f-0d38f30a1c01",
    "amount": 99000,
    "currency": "VND",
    "payment_method": "momo",
    "gateway": "MOMO",
    "gateway_order_id": "SJP-20260712-0065",
    "status": "failed",
    "transaction_id": "MOMO-FAILED-0065",
    "gateway_response": {},
    "failure_reason": "Payment session expired before confirmation.",
    "paid_at": null,
    "created_at": "2026-07-12T21:12:00+07:00"
  }
]
```

## Admin Statistics
Optional design placeholder: current admin dashboard displays static placeholder metric cards and no API is wired.

Use as display-only values, not backend fields:
```json
{
  "totalUsersLabel": "Total users",
  "totalUsersValue": "12,480",
  "activeJobsLabel": "Active jobs",
  "activeJobsValue": "1,236",
  "pendingReviewLabel": "Waiting for review",
  "pendingReviewValue": "42",
  "applicationsTodayLabel": "Applications today",
  "applicationsTodayValue": "318"
}
```

Sparse admin statistics:
```json
{
  "totalUsersValue": "--",
  "activeJobsValue": "--",
  "pendingReviewValue": "--",
  "applicationsTodayValue": "--"
}
```

## Revenue Statistics
Optional design placeholder: no current frontend API/chart library. Derive only from `payments` fields if implemented later.

Display-only examples:
```json
{
  "paidAmountThisMonth": "28,940,000 VND",
  "pendingAmount": "4,497,000 VND",
  "failedAmount": "297,000 VND",
  "topPaymentMethod": "vnpay",
  "paidTransactions": 186,
  "refundedTransactions": 3
}
```

Empty state:
```json
{
  "paidAmountThisMonth": "0 VND",
  "pendingAmount": "0 VND",
  "failedAmount": "0 VND",
  "paidTransactions": 0,
  "refundedTransactions": 0
}
```

## User Statistics
Optional design placeholder: no current frontend API. Use only display labels for admin concept screens.

Display-only examples:
```json
{
  "candidateUsers": 8420,
  "employerUsers": 3890,
  "adminUsers": 12,
  "activeUsers": 11024,
  "pendingVerificationUsers": 118,
  "suspendedUsers": 27,
  "emailVerifiedRate": "91%"
}
```

Empty state:
```json
{
  "candidateUsers": 0,
  "employerUsers": 0,
  "adminUsers": 0,
  "activeUsers": 0,
  "pendingVerificationUsers": 0,
  "suspendedUsers": 0,
  "emailVerifiedRate": "0%"
}
```

## Desktop and Mobile Layout Sample Sets
Desktop dense review screen:
```json
{
  "leftListItems": [
    "FPT Software - verified - 2 pending documents",
    "BrightPath Labs - pending - 1 pending document",
    "International Applied Data Platform Engineering and Recruitment Operations Center Vietnam Limited - rejected - 0 pending documents"
  ],
  "detailPanelRecord": "Use Company Profile + Company Branches + Employer Profile + Job Posts primary records"
}
```

Mobile stacked card screen:
```json
{
  "topCard": "Senior Java Spring Boot Developer",
  "primaryMetaLine": "FPT Software • Hanoi Head Office • Hybrid",
  "secondaryMetaLine": "25,000,000 - 42,000,000 VND • Senior • Deadline Aug 30, 2026",
  "badges": ["92% match", "Saved", "Applied"],
  "primaryAction": "View details",
  "secondaryAction": "Unsave"
}
```

Mobile empty states:
```json
{
  "savedJobs": "You have not saved any jobs yet.",
  "applications": "No applications yet. Apply to a job to track your progress here.",
  "aiSessions": "No interview practice sessions yet.",
  "notifications": "No notifications."
}
```

## Long Strings for Overflow Testing
- Job title: `Principal Platform Reliability Engineer for Distributed Recruitment Matching, Interview Automation, Candidate Ranking, and Workflow Observability`
- Company branch: `Da Nang Engineering Hub With A Very Long Branch Name For Overflow Testing`
- Candidate bio: `Backend developer with experience in job application workflows, asynchronous notification delivery, AI interview practice sessions, candidate ranking pipelines, subscription usage limits, payment reconciliation, and admin review operations across multiple recruitment teams.`
- Notification message: `Your application status was updated after the employer reviewed your CV, AI match score, interview practice summary, and internal hiring notes; please check the application timeline for the complete next-step explanation and prepare for the scheduled technical discussion.`
- Rejection reason: `The job description is too broad and does not clearly explain the responsibilities, required skills, salary expectations, working mode, or interview process. Please revise the posting with specific requirements before submitting it for review again.`
