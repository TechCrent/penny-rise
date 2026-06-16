# Stash Monolith

Core backend service housing platform/, financial/, social/, and tracker/ modules.

## Running locally

**Prerequisites:** Docker Compose stack running (`docker compose up -d` from repo root).

```bash
# From the monolith/ directory
export MONOLITH_DB_HOST=localhost
export MONOLITH_DB_PORT=5436
export MONOLITH_DB_NAME=monolith_db
export MONOLITH_DB_USER=stash_monolith
export MONOLITH_DB_PASSWORD=<your value from .env>
export SPRING_PROFILES_ACTIVE=local

mvn spring-boot:run
```

## Health check

```bash
curl http://localhost:8080/actuator/health
```

## Environment variables

See `.env.example` for all required variables.

## Port

`8080`