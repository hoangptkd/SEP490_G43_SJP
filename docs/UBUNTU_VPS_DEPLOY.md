# Huong dan deploy lai tren Ubuntu VPS

Tai lieu nay dung cho du an SJP dang deploy tren VPS Ubuntu.

## Thong tin hien tai

- VPS: `180.93.115.96`
- Thu muc source tren VPS: `/var/www/sjp`
- Backend service: `sjp-backend`
- Backend port noi bo: `8080`
- Frontend serve bang Nginx port: `80`
- Database: PostgreSQL database `sjp`
- User database: `sjp_user`
- Nhanh deploy mac dinh: `dev`

Khong ghi password VPS, database, mail hoac secret vao file nay.

## Deploy lai sau khi push code len GitHub

SSH vao VPS:

```bash
ssh root@180.93.115.96
```

Keo code moi:

```bash
cd /var/www/sjp
git pull origin dev
```

Neu deploy nhanh ca backend va frontend:

```bash
cd /var/www/sjp && \
git pull origin dev && \
cd backend && \
mvn clean package -DskipTests && \
systemctl restart sjp-backend && \
cd ../frontend && \
npm install && \
npm run build && \
nginx -t && \
systemctl restart nginx && \
systemctl status sjp-backend --no-pager
```

## Chi deploy backend

Dung khi chi sua Java, API, migration database hoac cau hinh backend.

```bash
cd /var/www/sjp
git pull origin dev

cd backend
mvn clean package -DskipTests
systemctl restart sjp-backend
systemctl status sjp-backend --no-pager
```

Xem log backend:

```bash
journalctl -u sjp-backend -f
```

Test API backend tren VPS:

```bash
curl http://127.0.0.1:8080/api/auth/config
```

## Chi deploy frontend

Dung khi chi sua React, CSS, UI hoac file trong `frontend`.

```bash
cd /var/www/sjp
git pull origin dev

cd frontend
npm install
npm run build
nginx -t
systemctl restart nginx
```

Mo web:

```text
http://180.93.115.96
```

## Sua file backend .env

Mo file:

```bash
nano /var/www/sjp/backend/.env
```

Luu trong nano:

```text
Ctrl + O
Enter
Ctrl + X
```

Sau khi sua `.env`, restart backend:

```bash
systemctl restart sjp-backend
systemctl status sjp-backend --no-pager
```

Nginx khong can restart neu chi sua `.env` backend.

## Database va migration

Backend dung Flyway. Khi deploy backend moi, migration moi trong:

```text
backend/src/main/resources/db/migration
```

se tu chay khi backend start neu `.env` co:

```env
SPRING_FLYWAY_ENABLED=true
```

Kiem tra Flyway tren VPS:

```bash
sudo -u postgres psql -d sjp -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Neu chi sua du lieu trong database thi khong can restart backend. Neu sua cau truc DB nhu them cot, xoa constraint, them bang thi nen restart backend.


## Xu ly loi thuong gap

Xem backend service:

```bash
systemctl status sjp-backend --no-pager
```

Xem log backend:

```bash
journalctl -u sjp-backend -n 100 --no-pager
```

Restart backend:

```bash
systemctl restart sjp-backend
```

Kiem tra Nginx:

```bash
nginx -t
systemctl status nginx --no-pager
```

Restart Nginx:

```bash
systemctl restart nginx
```

Neu `git pull` bao conflict, dung lai va gui nguyen output conflict cho nguoi phu trach merge.
