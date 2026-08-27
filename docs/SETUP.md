# Smart Recruitment Portal - Setup Guide

## Environment Setup

### 1. Prerequisites

Ensure you have the following installed:
- **Java**: OpenJDK 17 or higher
- **Maven**: 3.8 or higher
- **Node.js**: 18 or higher
- **PostgreSQL**: 15 or higher (or use Docker)
- **Git**: Latest version

### 2. Database Setup

#### Option A: Using Docker (Recommended)
```bash
docker-compose up -d
```

#### Option B: Local PostgreSQL
1. Create database:
```sql
CREATE DATABASE smart_recruitment;
```

2. Run migrations:
```bash
psql -d smart_recruitment -f database/migrations/V1__initial_schema.sql
```

### 3. Backend Setup

```bash
cd backend

# Update database credentials in application.yml
# Default: postgres/postgres

# Run with Maven
mvn clean install
mvn spring-boot:run
```

Backend will start at: `http://localhost:8080`

### 4. Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev
```

Frontend will start at: `http://localhost:5173`

## Configuration

### Backend Configuration

Edit `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smart_recruitment
    username: postgres
    password: postgres
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

server:
  port: 8080
```

### Frontend Configuration

Edit `frontend/vite.config.ts`:

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

## Development

### Running Tests

**Backend:**
```bash
cd backend
mvn test
```

**Frontend:**
```bash
cd frontend
npm run test
```

### Build for Production

**Backend:**
```bash
cd backend
mvn clean package -DskipTests
java -jar target/recruitment-portal-0.0.1-SNAPSHOT.jar
```

**Frontend:**
```bash
cd frontend
npm run build
npm run preview
```

## Git Workflow

1. Create feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. Make changes and commit:
   ```bash
   git add .
   git commit -m "feat: your feature description"
   ```

3. Push and create PR:
   ```bash
   git push origin feature/your-feature-name
   ```

## Code Style

### Backend
- Follow Spring Boot naming conventions
- Use Lombok annotations
- DTO pattern for request/response
- Entity pattern for database models

### Frontend
- TypeScript strict mode
- Functional components with hooks
- Redux Toolkit for state management
- Tailwind CSS for styling

## Troubleshooting

### Port Already in Use
- Backend: Change port in `application.yml`
- Frontend: Change port in `vite.config.ts`

### Database Connection Issues
- Verify PostgreSQL is running
- Check credentials in `application.yml`
- Ensure database exists