# Daily API incident note

**Audience:** Daily Identity/API maintainers  
**Observed:** 2026-08-07 on the production VPS  
**Scope:** Existing Daily API service only; this is not a CiphrChat relay failure.

## Summary

The PM2 process `daily-api` is repeatedly restarting because its startup health check cannot connect to PostgreSQL at `127.0.0.1:5432`.

Observed error:

```text
Error: connect ECONNREFUSED 127.0.0.1:5432
at DailyPersistence.healthcheck
```

At the time of inspection, PM2 reported `daily-api` as `waiting restart` with more than 160 restarts and zero uptime.

## VPS evidence

The running Docker PostgreSQL containers are published on these host ports:

| Container | Host port | Container port |
|---|---:|---:|
| `dailydirectory-v20-postgres-1` | 5433 | 5432 |
| `quantra-postgres` | 5434 | 5432 |
| `pg-test-1` | 5541 | 5432 |

Nothing was listening on host port `5432` during the check. The API release environment contains a `DATABASE_URL`, but its value was intentionally not printed.

## Requested investigation

Please confirm which PostgreSQL database the Daily API is intended to use and then verify:

1. The intended database container/cluster is running.
2. The database host and port in the Daily API release environment match the actual service location.
3. The database name, user, password, and migrations are correct.
4. The PM2 process is started with the intended release environment.
5. A health check succeeds before PM2 is allowed to remain online.

Do not change the port or restart any database until the intended Daily API database is identified. The three existing PostgreSQL containers may belong to different applications.

## CiphrChat impact

The CiphrChat relay is separate and healthy. It is running in `/opt/ciphrchat-relay` on TCP/UDP port `4001`, with its private health endpoint bound to `127.0.0.1:18081`. No Daily API process, database, PM2 application, or existing container was changed while deploying CiphrChat.

