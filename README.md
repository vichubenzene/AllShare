# All Share

Temporary text and file sharing under a name you choose.

- Text: `http://localhost:5173/vivi`
- File `report.pdf` shared as `vivi`: `http://localhost:5173/vivi.pdf`

Shares expire after 15 minutes to 7 days, can have a password, and can be revoked with a management token shown once at creation.

```text
React (Vite, JSX)  ->  Spring Boot  ->  PostgreSQL   shares, text, file metadata
                                    ->  Redis        rate limits
                                    ->  MongoDB      request logs (request_logs)
                                    ->  data/uploads uploaded files
```

## Requirements

- Java 21+ (`JAVA_HOME` must point at the JDK, for example `C:\Program Files\Java\jdk-21.0.12`)
- Node.js 18+
- PostgreSQL
- Redis
- MongoDB

Maven is not required. `backend\mvnw.cmd` downloads it on first use.

## Database setup

### PostgreSQL

Create a user and database once, for example in `psql -U postgres`:

```sql
CREATE USER share WITH PASSWORD 'share';
CREATE DATABASE share OWNER share;
```

The backend creates the `shares` table on startup (Flyway migration `V1__create_shares.sql`). Share names are unique through the `uk_shares_name` constraint.

### Redis

Run a Redis server on `localhost:6379`. No setup is needed. On Windows, [Memurai](https://www.memurai.com/) or the [Redis for Windows port](https://github.com/tporadowski/redis/releases) both work. Redis only holds one-minute rate-limit counters.

### MongoDB

Run MongoDB on `localhost:27017`. No setup is needed. The backend writes to the database `temporary_share_logs`, collection `request_logs`, and MongoDB creates both on the first write.

To run without MongoDB, set `REQUEST_LOG_ENABLED=false`. Otherwise the backend refuses to start when MongoDB is unreachable.

## Start the backend

```cmd
cd backend
mvnw.cmd spring-boot:run
```

On startup the log shows one line per service:

```text
event=startup_check service=postgresql status=ok
event=startup_check service=redis status=ok
event=startup_check service=mongodb status=ok database=temporary_share_logs
```

The API runs on `http://localhost:8080`. Uploaded files are stored in `backend\data\uploads` under generated names, never under the share name or the uploaded filename.

## Start the frontend

```cmd
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The dev server forwards `/api` to the backend.

## Tests

```cmd
cd backend
mvnw.cmd clean test
```

```cmd
cd frontend
npm run build
```

## Configuration

Set these as environment variables before starting the backend, for example `set DATABASE_PASSWORD=secret` in `cmd`.

| Variable | Default |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/share` |
| `DATABASE_USERNAME` | `share` |
| `DATABASE_PASSWORD` | `share` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / empty |
| `MONGODB_URI` | `mongodb://localhost:27017/temporary_share_logs` |
| `REQUEST_LOG_ENABLED` | `true` |
| `STORAGE_LOCATION` | `./data/uploads` |
| `ACCESS_TOKEN_SECRET` | development value; set 32+ random characters outside your own PC |
| `RATE_LIMIT_CREATE_PER_MINUTE` | `20` per client IP |
| `RATE_LIMIT_VERIFY_PER_MINUTE` | `10` password attempts per share |
| `CORS_ORIGINS` | `http://localhost:5173` |
| `SERVER_PORT` | `8080` |

## Share names

- Lowercase letters, numbers, `-` and `_`, 1-63 characters, starting with a letter or number. Input is lowercased.
- A file share adds the uploaded file's extension: `vivi` + `report.pdf` is `/vivi.pdf`. A file without an extension is just `/vivi`.
- One name belongs to one share. Creating `vivi` while it exists returns `409 SHARE_NAME_TAKEN` and never overwrites. The web page then opens the existing share.
- A name becomes free again after its share expires.
- Reserved: `api`, `created`, `assets`, `src`, `node_modules`, `favicon`, `robots`, `index`, `file`, `static`, `public`.

## API

`{slug}` is the public path without the slash: `vivi` or `vivi.pdf`.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/shares` | Create a text share |
| `POST` | `/api/shares/file` | Create a file share (multipart: `name`, `file`, `expirationMinutes`, `password`) |
| `GET` | `/api/shares/{slug}` | Read a share. Protected shares return `{"passwordRequired": true, ...}` without content |
| `POST` | `/api/shares/{slug}/verify` | Check a password. Returns content and a 15-minute access token |
| `GET` | `/api/shares/{slug}/download` | Download the file. Protected files need the `X-Share-Access` header |
| `DELETE` | `/api/shares/{slug}` | Revoke. Needs `Authorization: Bearer <management-token>` |

`expirationMinutes` is one of `15`, `60`, `360`, `1440`, `4320`, `10080`.

Errors look like `{"code": "SHARE_EXPIRED", "message": "This share has expired."}`:

| Code | Status |
| --- | --- |
| `VALIDATION_ERROR` | 400 |
| `INVALID_FILE` | 400 |
| `UNAUTHORIZED`, `INVALID_PASSWORD`, `PASSWORD_REQUIRED` | 401 |
| `SHARE_NOT_FOUND` | 404 |
| `SHARE_NAME_TAKEN` | 409 (includes `shareUrl` of the existing share) |
| `SHARE_EXPIRED`, `SHARE_REVOKED` | 410 |
| `FILE_TOO_LARGE` | 413 (limit 50 MB) |
| `RATE_LIMITED` | 429 |
| `INTERNAL_ERROR` | 500 |

### Examples (`cmd`)

```cmd
curl -X POST http://localhost:8080/api/shares -H "Content-Type: application/json" -d "{\"name\":\"vivi\",\"content\":\"Hello World\",\"expirationMinutes\":60}"

curl -X POST http://localhost:8080/api/shares/file -F "name=report" -F "file=@report.pdf" -F "expirationMinutes=60" -F "password=secret"

curl http://localhost:8080/api/shares/vivi

curl -X POST http://localhost:8080/api/shares/report.pdf/verify -H "Content-Type: application/json" -d "{\"password\":\"secret\"}"

curl -o report.pdf -H "X-Share-Access: ACCESS_TOKEN" http://localhost:8080/api/shares/report.pdf/download

curl -X DELETE http://localhost:8080/api/shares/vivi -H "Authorization: Bearer MANAGEMENT_TOKEN"
```

Create response:

```json
{
  "name": "vivi",
  "type": "TEXT",
  "shareUrl": "/vivi",
  "managementToken": "ps3KQOzh8KcrikUNmKyE8U7xsXxozUCwWO-pHWgqJko",
  "expiresAt": "2026-09-28T18:04:37.410Z",
  "passwordProtected": false
}
```

## Request logs

A servlet filter (`RequestLogFilter`) writes one document per API request to MongoDB on a background thread:

```json
{
  "timestamp": "2026-09-28T17:03:13.673Z",
  "method": "GET",
  "path": "/api/shares/vivi.pdf/download",
  "status": 200,
  "ip": "127.0.0.1",
  "userAgent": "Mozilla/5.0 ...",
  "shareName": "vivi",
  "action": "DOWNLOAD",
  "responseTimeMs": 9
}
```

Actions: `CREATE_TEXT`, `CREATE_FILE`, `VIEW`, `DOWNLOAD`, `PASSWORD_OK`, `PASSWORD_FAILED`, `PASSWORD_REQUIRED`, `REVOKE`, `REVOKED_ACCESS`, `EXPIRED_ACCESS`, `NOT_FOUND`, `NAME_TAKEN`, `RATE_LIMITED`, `UNAUTHORIZED`, `ERROR`, and `<action>_REJECTED` for other validation failures. Failed requests also carry `errorCode`.

Logs never contain passwords, password hashes, management or access tokens, share content, file bytes, `Authorization` headers, or cookies.

Browser page loads such as `/vivi` are served by the frontend. The backend logs the API calls those pages make, such as `GET /api/shares/vivi`.

Useful `mongosh` queries:

```js
use temporary_share_logs
db.request_logs.find().sort({ timestamp: -1 }).limit(20)
db.request_logs.find({ shareName: "vivi" })
db.request_logs.aggregate([{ $group: { _id: "$action", count: { $sum: 1 } } }])
```

## How it works

- PostgreSQL is the source of truth. Expiration and revocation are checked on every request, so an expired share returns `410` even before the cleanup job (every minute) deletes it and its file.
- Passwords are BCrypt hashes. After a correct password the backend returns a short-lived HMAC access token bound to that share, so the password is not sent again.
- Management tokens are 256-bit random values stored only as SHA-256 hashes. Knowing `/vivi` is not enough to revoke it.
- The uploaded file's type is detected from its bytes (Apache Tika), not from the browser. Downloads are always attachments, and HTML, SVG, and JavaScript are served as `application/octet-stream`.
- The Vite dev server forwards the client IP (`X-Forwarded-For`), which the backend trusts only from loopback and private addresses.
