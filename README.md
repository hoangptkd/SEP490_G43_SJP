# Smart Recruitment Portal

Nền tảng kết nối việc làm và hỗ trợ phỏng vấn thông minh

## Tech Stack

- **Frontend**: React 18 + TypeScript + Vite
- **Backend**: Java Spring Boot 3.x
- **Database**: PostgreSQL 15
- **State Management**: Redux Toolkit
- **Styling**: Tailwind CSS
- **API Client**: Axios

## Project Structure

```
├── frontend/          # React TypeScript Application
├── backend/           # Java Spring Boot Application
├── database/          # Database migrations and seeds
├── docs/              # Documentation
└── docker-compose.yml # Development environment
```

## Getting Started

### Prerequisites
- Node.js 18+
- Java 17+
- Maven 3.8+
- Docker (optional, for PostgreSQL)

### Installation

1. Clone the repository
2. Start PostgreSQL with Docker:
   ```bash
   docker-compose up -d
   ```
3. Setup Backend:
   ```bash
   cd backend
   mvn clean install
   mvn spring-boot:run
   ```
4. Setup Frontend:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

## Features

- 🔍 Tìm kiếm & Matching việc làm
- 🤖 Phỏng vấn AI thông minh
- 📊 Quản lý ứng tuyển
- ✅ Đánh giá kỹ năng

## License

MIT