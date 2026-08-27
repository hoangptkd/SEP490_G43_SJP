# Project Overview

## Product Name
Smart Recruitment Portal (SJP)

## Product Goal
Smart Recruitment Portal is a role-based recruitment platform for job discovery, candidate applications, employer job posting, company verification, and admin review workflows.

## Target Users
- Guests browse public jobs, view job details, register, login, verify email, and complete OAuth role selection.
- Candidates manage profile data, CVs, saved jobs, applications, notifications, subscription status, recommendations, and AI interview sessions.
- Employers manage company profile, branch/work locations, legal verification documents, and job postings.
- Admins review employer company verification and job postings. Some admin pages are placeholders.

## Main Modules
- Public job discovery and job detail.
- Authentication: login, registration, email verification, Google OAuth callback, OAuth role selection.
- Candidate portal: dashboard, profile, CVs, saved jobs, applications, application timeline, notifications, subscription, AI interview practice.
- Employer portal: dashboard placeholder, company profile, locations, legal verification, job management.
- Admin portal: admin login, dashboard placeholder, company review, job review, users/statistics/settings placeholders, admin profile.

## High-Level Business Flow
1. A guest browses `/jobs` and opens `/jobs/:id`.
2. A user registers as `CANDIDATE` or `EMPLOYER`, verifies email, and logs in. Google OAuth may require role selection.
3. A candidate completes profile/CV setup, saves jobs, applies to jobs, tracks applications, and can practice AI interviews.
4. An employer completes company profile, manages locations, uploads legal documents, and waits for admin verification.
5. A verified employer creates job drafts, submits jobs for admin review, and resubmits rejected jobs.
6. Admins review pending company verification and job postings, approving or rejecting with reasons.

## Short English System Explanation for Design Tools
Smart Recruitment Portal is a recruitment web application with public job browsing, a candidate workspace, an employer workspace, and an admin review console. The product includes a trust workflow where companies upload legal documents for admin approval before posting jobs, and jobs can also require admin approval before becoming public. Candidates discover jobs, manage CVs, apply, track status, and use AI interview practice. Admins work mainly in dense list-detail review screens with status filters, document previews, approval actions, rejection forms, and status badges.
