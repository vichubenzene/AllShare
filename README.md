# All Share

Temporary text and file sharing. A share gets a random link, an optional password, and an expiration time. When it expires or is revoked, the content is gone.

PostgreSQL stores share metadata and text. Uploaded files go through a storage interface that currently writes to the local disk. Redis is used only for rate limits.

## Project structure

```text
all-share/
├── docker-compose.yml
├── .env.example
├── backend/
│   └── src/main/java/com/example/share/
│       ├── config/
│       ├── controller/
│       ├── dto/
│       ├── entity/
│       ├── repository/
│       ├── service/
│       ├── scheduler/
│       ├── storage/
│       └── exception/
└── frontend/
    └── src/
        ├── api/
        ├── components/
        ├── hooks/
        ├── layouts/
        ├── pages/
        ├── types/
        └── utils/
```

## Run the backend

Requires JDK 21, PostgreSQL, and Redis. `JAVA_HOME` must point at the JDK (for example `C:\Program Files\Java\jdk-21.0.12`).

```bash
cd backend
mvnw.cmd spring-boot:run
```

On macOS or Linux, use `./mvnw spring-boot:run`.

The API listens on `http://localhost:8080`.

## Run the frontend

Requires Node.js 18 or newer.

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The dev server proxies `/api` to `http://localhost:8080`.

## Docker Compose

Requires Docker.

```bash
docker compose up --build
```

- App: `http://localhost:5173`
- API: `http://localhost:8080`
- PostgreSQL: `localhost:5432` (database `share`, user `share`, password `share`)
- Redis: `localhost:6379`

Stop it with `docker compose down`. Add `-v` to delete the database volume and uploaded files.

To run only the databases and develop the apps on the host:

```bash
docker compose up postgres redis
```

Change `ACCESS_TOKEN_SECRET` before exposing this anywhere other than your own machine. The Compose default is a development value.

## Database setup

Flyway creates the `shares` table on startup. You only need an empty database.

With Docker Compose, the `postgres` service creates it.

Without Docker:

```sql
CREATE USER share WITH PASSWORD 'share';
CREATE DATABASE share OWNER share;
```

Redis has no schema. Start it locally, or use the Compose service, and point `REDIS_HOST` at it.

Uploaded files are written to `STORAGE_LOCATION` (`backend/data/uploads` when you run from `backend/`).

## API

Errors use this shape:

```json
{ "code": "SHARE_EXPIRED", "message": "This share has expired." }
```

| Code | Status | When |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | Bad input or expiration |
| `INVALID_FILE` | 400 | Missing or unreadable upload |
| `FILE_TOO_LARGE` | 413 | Over 50 MB |
| `SHARE_NOT_FOUND` | 404 | Unknown token |
| `UNAUTHORIZED` | 401 | Missing or wrong management token |
| `PASSWORD_REQUIRED` | 401 | Download without access |
| `INVALID_PASSWORD` | 401 | Wrong password |
| `SHARE_EXPIRED` | 410 | Past `expiresAt` |
| `SHARE_REVOKED` | 410 | Creator revoked it |
| `RATE_LIMITED` | 429 | Too many creates or password attempts |

Allowed `expirationMinutes` values: `15`, `60`, `360`, `1440`, `4320`, `10080`.

### Create text

`POST /api/shares`

```json
{
  "type": "TEXT",
  "content": "Hello World",
  "expirationMinutes": 60,
  "password": "optional-password"
}
```

```json
{
  "token": "a8Kx92LmP4xZ",
  "shareUrl": "/s/a8Kx92LmP4xZ",
  "managementToken": "very-long-random-secret",
  "expiresAt": "2026-09-27T23:00:00Z",
  "passwordProtected": true
}
```

The management token is returned once. It is stored only as a SHA-256 hash.

### Upload a file

`POST /api/shares/file` as `multipart/form-data` with `file`, `expirationMinutes`, and optional `password`. The response matches text creation.

### Read a share

`GET /api/shares/{token}`

Public shares return the text or file metadata. Password-protected shares return `{ "passwordRequired": true }` and do not include the content. After unlock, send the access token in `X-Share-Access`.

### Unlock

`POST /api/shares/{token}/verify`

```json
{ "password": "optional-password" }
```

A correct password returns the content and a short-lived access token. Send that token on later reads and downloads. Do not send the password again.

### Download

`GET /api/shares/{token}/download`

The file is streamed. Protected shares need `X-Share-Access`.

### Revoke

`DELETE /api/shares/{token}`

```http
Authorization: Bearer <management-token>
```

The public share token cannot revoke a share.

## Example curl commands

```bash
curl -s -X POST http://localhost:8080/api/shares \
  -H "Content-Type: application/json" \
  -d "{\"type\":\"TEXT\",\"content\":\"Hello World\",\"expirationMinutes\":60}"

curl -s -X POST http://localhost:8080/api/shares/file \
  -F "file=@notes.txt" \
  -F "expirationMinutes=60" \
  -F "password=secret"

curl -s http://localhost:8080/api/shares/TOKEN

curl -s -X POST http://localhost:8080/api/shares/TOKEN/verify \
  -H "Content-Type: application/json" \
  -d "{\"password\":\"secret\"}"

curl -L -o download.bin \
  -H "X-Share-Access: ACCESS_TOKEN" \
  http://localhost:8080/api/shares/TOKEN/download

curl -s -X DELETE http://localhost:8080/api/shares/TOKEN \
  -H "Authorization: Bearer MANAGEMENT_TOKEN"
```

## Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/share` | JDBC URL |
| `DATABASE_USERNAME` | `share` | Database user |
| `DATABASE_PASSWORD` | `share` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `STORAGE_LOCATION` | `./data/uploads` | Local file directory |
| `MAX_FILE_SIZE` | `52428800` | App-level byte limit |
| `MAX_TEXT_LENGTH` | `1000000` | Maximum text length |
| `MULTIPART_MAX_FILE_SIZE` | `50MB` | Servlet upload limit |
| `MULTIPART_MAX_REQUEST_SIZE` | `50MB` | Servlet request limit |
| `ACCESS_TOKEN_SECRET` | development placeholder | HMAC secret, at least 32 characters |
| `ACCESS_TOKEN_TTL` | `15m` | Password-unlock token lifetime |
| `CORS_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins |
| `RATE_LIMIT_CREATE_PER_MINUTE` | `20` | Creates per client address |
| `RATE_LIMIT_VERIFY_PER_MINUTE` | `10` | Password attempts per share |
| `CLEANUP_DELAY_MS` | `60000` | Delay between expiration sweeps |
| `ALLOWED_EXPIRATIONS` | `15,60,360,1440,4320,10080` | Allowed durations in minutes |
| `SERVER_PORT` | `8080` | API port |
| `VITE_API_BASE_URL` | empty | Optional absolute API origin for the frontend |

See `.env.example`.

## Tests

Unit tests cover creation, passwords, expiration, revocation, path traversal, file storage, oversized uploads, and rate limits. They do not need Docker.

```bash
cd backend
mvnw.cmd test
```

`ShareIntegrationTest` talks to PostgreSQL and Redis through Testcontainers. It runs with the same command when Docker is available, and JUnit skips it when Docker is not.

## Architecture

- PostgreSQL is the source of truth. Redis only counts rate-limit windows, and those keys expire after a minute.
- The public id is a random 12-character token. The database primary key is a UUID and is not put in URLs.
- Share passwords are BCrypt hashes from Spring Security's `PasswordEncoder`. Management tokens are 256-bit random values stored as SHA-256 hashes.
- Unlocking a share returns an HMAC access token (`v1`) bound to that share. The password is not sent again, and the API process does not keep a session.
- `FileStorageService` is the storage boundary. The local implementation streams uploads to a generated hex filename. The original filename is metadata only. Replacing this class is the path to S3 or MinIO.
- Download responses stream a `Resource`. HTML, SVG, and JavaScript are served as `application/octet-stream` with `Content-Disposition: attachment`.
- Every read checks revocation and `expiresAt` before returning content. The cleanup job deletes expired rows and files later; it is not what makes an expired share inaccessible.
- Create limits are per direct client address. Password attempts are per share, counted only after the share is known, so random tokens do not fill Redis. A reverse proxy in front of the API shares one address until forwarded headers are configured.
- Logs record event names and internal share ids. They do not include passwords, file bytes, share tokens, management tokens, or access tokens.

View limits, burn-after-reading, IP allow lists, accounts, and object storage are not implemented. `requireAvailable` in `ShareService` is the place to add view and download limits later.
