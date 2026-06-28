# Dev auth and health checks

## Why `admin/admin1234` may fail locally

The first visible login is bootstrapped only when there are no active users with a password:

```java
userRepository.countByHiddenFalseAndPasswordIsNotNull() == 0
```

Older local databases can already contain `users` rows with BCrypt password hashes. In that case `/api/v1/auth/login` validates the stored hash and does not replace it. On the current local database, users `admin` and `bro` exist with saved password hashes, so `admin/admin1234` can return `401 Invalid username or password`.

Do not reset existing local passwords automatically. Use one of the manual dev-only paths below.

## Create or reset a dev user manually

Option A: use the app user API when you already have a valid admin JWT.

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin1234"}'
```

If the user already exists, change its password:

```bash
curl -X PATCH http://localhost:8080/api/v1/users/<USER_ID>/password \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"password":"admin1234"}'
```

Option B: use a temporary SQL update in a disposable local database only.

Generate a BCrypt hash with the backend encoder:

```bash
jshell --class-path "$env:USERPROFILE\.m2\repository\org\springframework\security\spring-security-crypto\6.4.6\spring-security-crypto-6.4.6.jar;$env:USERPROFILE\.m2\repository\org\springframework\spring-core\6.2.7\spring-core-6.2.7.jar;$env:USERPROFILE\.m2\repository\org\springframework\spring-jcl\6.2.7\spring-jcl-6.2.7.jar"
```

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
new BCryptPasswordEncoder().encode("admin1234");
```

Then update the local row:

```sql
update users
set password = '<BCrypt hash>', role = 'ADMIN', hidden = false
where lower(username) = 'admin';
```

If there is no admin row:

```sql
insert into users (username, language_code, timezone, password, role, hidden, created_at, updated_at)
values ('admin', 'ru', 'Europe/Moscow', '<BCrypt hash>', 'ADMIN', false, now(), now());
```

## Verify auth

Login:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin1234"}'
```

Verify the token:

```bash
curl -i http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <JWT>"
```

Expected response:

```json
{
  "userId": 1,
  "username": "admin",
  "role": "ADMIN"
}
```

## Production Redis health

`docker-compose.prod.yml` defines a `redis` service and the backend depends on it:

```yaml
depends_on:
  redis:
    condition: service_healthy
environment:
  REDIS_HOST: ${REDIS_HOST:-redis}
  REDIS_PORT: ${REDIS_PORT:-6379}
```

Production verification commands:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml exec redis redis-cli ping
docker compose -f docker-compose.prod.yml exec backend printenv REDIS_HOST
curl -fsS http://127.0.0.1:8080/actuator/health
```

Expected Redis check:

```text
PONG
```

Expected actuator result after Redis and PostgreSQL are healthy:

```json
{"status":"UP"}
```

If Redis is intentionally optional in an environment, do not silently disable it. Add a dedicated health group or change the healthcheck target in a separate reviewed change.
