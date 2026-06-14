# NeuroInfoGrinder Java Backend

Стартовый Spring Boot backend для нового ручного Java-проекта.

Работает как отдельный runtime-модуль в паре с `C:\Users\Adam\Documents\Work\LarbCorp\Products\NeuroInfoGrinder\frontend`.
Спецификация взаимодействия лежит в `C:\Users\Adam\Documents\Work\LarbCorp\Products\NeuroInfoGrinder\docs\frontend-backend-spec.md`.

Эту папку можно открывать напрямую в IntelliJ IDEA как отдельный Maven-проект.

## Стек

- Java 17
- Spring Boot 3.4.x
- Spring Web
- Spring Validation
- Spring Data JPA
- Spring Data Redis
- Spring Security
- Flyway
- PostgreSQL
- H2 для локального старта
- Springdoc OpenAPI
- TDLib JNI

## Локальный запуск

```bash
mvn -f backend/pom.xml spring-boot:run
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

Actuator health:

```text
http://localhost:8080/actuator/health
```

## TDLib JNI

Backend теперь работает через официальный Java JNI-слой TDLib (`Client` + `TdApi`), а не через JSON bridge.

Нативные библиотеки ожидаются в:

```text
C:\Users\Adam\Documents\Work\LarbCorp\Products\NeuroInfoGrinder\backend\native
```

Сейчас там уже лежат:

- `tdjni.dll`
- `libcrypto-3-x64.dll`
- `libssl-3-x64.dll`
- `zlib1.dll`

Минимальные переменные окружения:

```text
TDLIB_ENABLED=true
TDLIB_LIBRARY_NAME=tdjni
TDLIB_LIBRARY_PATH=.\native
TDLIB_API_ID=38702302
TDLIB_API_HASH=008317b3378b61620a6faebb81129792
```

## Telegram endpoints

```text
GET  /api/v1/telegram/auth/state
POST /api/v1/telegram/auth/phone
POST /api/v1/telegram/auth/code
POST /api/v1/telegram/auth/password
GET  /api/v1/telegram/chats
GET  /api/v1/telegram/chats/{chatId}/topics
GET  /api/v1/telegram/chats/{chatId}/messages
POST /api/v1/telegram/messages/batch
```

## Что уже проверено

- `mvn -f backend/pom.xml test`
- запуск backend на `http://localhost:8080`
- `GET /api/v1/telegram/auth/state` доходит до `AuthorizationStateWaitPhoneNumber`
