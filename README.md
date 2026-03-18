# ms-chatai

Backend Spring Boot (MVC) para el chat de feedback del frontend `isp-dashboard`.

## 1) Levantar Postgres local (WSL + Docker)

Desde la raiz del monorepo:

```bash
cd ./ms-chatai-postgres
docker compose up -d
```

Valores por default (`ms-chatai-postgres/docker-compose.yml`):
- `POSTGRES_DB=ms_chatai`
- `POSTGRES_USER=chatai`
- `POSTGRES_PASSWORD=chatai_dev_password`
- host `localhost`
- puerto `55432`

## 2) Variables de entorno

Necesarias:
- `OPENAI_API_KEY`
- `AUTH_PUBLIC_KEY_PEM` (public key Ed25519 PEM, usada para validar `Authorization: Bearer <token>`)
- `AUTH_SERVICE_TOKEN` (opcional, bearer estatico compartido para clientes server-side como Next/Tauri)
- `JDBC_URL` (default `jdbc:postgresql://localhost:55432/ms_chatai`)
- `JDBC_USER` (default `chatai`)
- `JDBC_PASSWORD` (default `chatai_dev_password`)

Opcionales comunes:
- `OPENAI_MODEL` (default `gpt-4o-mini`)
- `AUTH_ACCEPTED_SCOPES` (default `feedback_chat`)
- `AUTH_SERVICE_SUBJECT` (default `service-client`)
- `RATE_LIMIT_RPM` (default `30`)
- `RATE_LIMIT_BURST` (default `10`)
- `CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`)
- `AI_FLOW_FEEDBACK_CHAT_MAX_TOKENS` (default `220`)
- `AI_FLOW_IMPROVE_MESSAGE_MAX_TOKENS` (default `140`)
- `AI_NORMALIZATION_MODE` (default `balanced`)
- `AI_MAX_INPUT_CHARS_FEEDBACK_CHAT` (default `1800`)
- `AI_MAX_INPUT_CHARS_IMPROVE_MESSAGE` (default `1400`)
- `AI_CACHE_IMPROVE_MESSAGE_ENABLED` (default `true`)
- `AI_CACHE_IMPROVE_MESSAGE_TTL_SECONDS` (default `60`)
- `AI_CACHE_IMPROVE_MESSAGE_MAX_ENTRIES` (default `1000`)
- `AI_PRICE_GPT5MINI_INPUT_PER_1M_USD` / `AI_PRICE_GPT5MINI_CACHED_INPUT_PER_1M_USD` / `AI_PRICE_GPT5MINI_OUTPUT_PER_1M_USD`
- `PUBLIC_BASE_URL` (default `http://localhost`, usado para construir URL publica de media)
- `MEDIA_ROOT` (default `/data/uploads`)
- `MEDIA_PUBLIC_PATH` (default `/media`)
- `MAX_UPLOAD_MB` (default `10`)

## 3) Ejecutar backend

```bash
cd ./ms-chatai
./mvnw spring-boot:run
```

Health:

```bash
curl http://localhost:8080/actuator/health
```

## 3.1) Modos de autenticacion soportados

Puedes usar cualquiera de estos dos modos:

1. JWT Ed25519:
- Backend valida `Authorization: Bearer <jwt>` usando `AUTH_PUBLIC_KEY_PEM`.
- Recomendado cuando ya tienes un emisor de tokens.

2. Service token compartido:
- Backend valida `Authorization: Bearer <token>` comparandolo con `AUTH_SERVICE_TOKEN`.
- Recomendado para `isp-dashboard` (proxy server-side Next) e `isp-admin` (proxy Tauri) en despliegues on-prem.
- En este modo, el frontend server-side o desktop debe enviar el mismo valor por `FEEDBACK_CHAT_BACKEND_TOKEN`.

## 4) Probar endpoints con curl

`POST /api/feedback-chat`:

```bash
curl -X POST "http://localhost:8080/api/feedback-chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN_ED25519>" \
  -H "X-Request-Id: req-local-001" \
  -d '{
    "message": "El boton guardar no responde",
    "route": "/dashboard/dev",
    "sectionTag": "dev",
    "role": "dev",
    "roleTag": "DEV",
    "taskId": "task-123",
    "taskType": "BUG",
    "priority": "HIGH",
    "userName": "Estor",
    "isGeneralMode": false,
    "modelPreference": "gpt-5-mini"
  }'
```

`POST /api/feedback-chat/improve-message`:

```bash
curl -X POST "http://localhost:8080/api/feedback-chat/improve-message" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN_ED25519>" \
  -H "X-Request-Id: req-local-002" \
  -d '{
    "message": "el boton guardar no jala en clientes",
    "route": "/dashboard/clients",
    "sectionTag": "clients",
    "role": "dev",
    "roleTag": "DEV",
    "taskType": "BUG",
    "priority": "MEDIUM",
    "userName": "Estor",
    "isGeneralMode": false,
    "modelPreference": "default"
  }'
```

`GET /api/feedback-chat/usage-summary`:

```bash
curl "http://localhost:8080/api/feedback-chat/usage-summary" \
  -H "Authorization: Bearer <TOKEN_ED25519>" \
  -H "X-Request-Id: req-local-003"
```

`GET /api/feedback-chat/sessions`:

```bash
curl "http://localhost:8080/api/feedback-chat/sessions" \
  -H "Authorization: Bearer <TOKEN_ED25519>" \
  -H "X-Request-Id: req-local-003a"
```

`POST /api/feedback-chat/sessions`:

```bash
curl -X POST "http://localhost:8080/api/feedback-chat/sessions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN_ED25519>" \
  -H "X-Request-Id: req-local-003b" \
  -d '{"sessionId":"SES-LOCAL-001","title":"New Session"}'
```

`POST /api/feedback-chat/sessions/{sessionId}/messages`:

```bash
curl -X POST "http://localhost:8080/api/feedback-chat/sessions/SES-LOCAL-001/messages" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN_ED25519>" \
  -H "X-Request-Id: req-local-003c" \
  -d '{"sender":"USER","text":"Necesito ajustar el flujo de pagos"}'
```

`DELETE /api/feedback-chat/sessions/{sessionId}/messages`:

```bash
curl -X DELETE "http://localhost:8080/api/feedback-chat/sessions/SES-LOCAL-001/messages" \
  -H "Authorization: Bearer <TOKEN_ED25519>" \
  -H "X-Request-Id: req-local-003d"
```

`POST /api/chat/media/upload`:

```bash
curl -X POST "http://localhost:8080/api/chat/media/upload" \
  -H "Authorization: Bearer <TOKEN_ED25519>" \
  -H "X-Request-Id: req-local-004" \
  -F "file=@./captura.png"
```

`GET /api/chat/media/capabilities`:

```bash
curl "http://localhost:8080/api/chat/media/capabilities" \
  -H "Authorization: Bearer <TOKEN_ED25519>" \
  -H "X-Request-Id: req-local-005"
```
