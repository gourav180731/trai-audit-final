# TSP SMS Dissemination Compliance Monitor — Spring Boot (Live PostgreSQL)

Live-SQL successor to the earlier Excel-snapshot prototype. Connects **directly to PostgreSQL** (`dm.t_tsp_sms_dissemination_statistics` + `dm.t_tsp_contact_list`), runs discrepancy checks as SQL, generates a styled `.xlsx` report on demand, and sends it by email in exactly two clicks: **Generate Report → Send Email**.

## Architecture

- **Spring Boot 3.5** (Java 22), **Spring Data JPA**, **Flyway**, **PostgreSQL** (live), **Thymeleaf**, **Apache POI**, **Spring Mail**
- `dm` schema is the source of truth — see `src/main/resources/db/migration/V1__dm_schema.sql` (exact DDL per brief, with the ordinal-27 gap preserved) and `V2__app_tables.sql` (audit tables).
- H2 is kept only for `application-test.properties` / CI; do not use file upload as the primary path in production.

## Quick Start (local dev without prod access)

```bash
# 1. Start a local Postgres (Docker example)
docker run --name trai-pg -e POSTGRES_DB=trai_audit -e POSTGRES_USER=trai_user -e POSTGRES_PASSWORD=trai_pass -p 5432:5432 -d postgres:16

# 2. Configure — env vars override application.properties defaults
#    Linux/macOS: export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/trai_audit
#    Windows PowerShell: $env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/trai_audit"

# 3. Build & run (Flyway creates dm schema on first boot)
mvn clean package -DskipTests
java -jar target/trai-audit-webapp-1.0.0.jar
# or: mvn spring-boot:run

# 4. Open http://localhost:8081/live  — click Generate Report
```

SMTP is externalized — set `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `AUDIT_MAIL_FROM` via env vars; locally use MailHog/Mailpit (`localhost:1025`) or `spring.mail.host=localhost`.

## Single Source of Truth for Thresholds

All thresholds live in `AuditProperties.java` / `MradConfig.java` and `application.properties` under `audit.*`:

```properties
audit.threshold.feedback-delay-seconds=600          # 10 min (spec says 5 vs 10 — see NEEDS_SIGN_OFF)
audit.threshold.subscriber-ratio-deviation-pct=15.0 # placeholder pending sign-off
audit.mrad.band-0-5k-minutes=60
audit.mrad.band-5-15k-minutes=90
audit.mrad.band-15-30k-minutes=120
```

No thresholds are scattered through check logic.

## Which of the 7 Checks Are Fully Computable Now

| # | Check | Live Columns | Status | Details |
|---|-------|--------------|--------|---------|
| 1 | Complete dissemination failure | `start_time`, `end_time`, `response1/2_received_timestamp` all NULL | **Fully computable** | `LiveDiscrepancyService.check1CompleteFailure` — pure SQL |
| 2 | Feedback / delta not received | `end_time` present & `response2` NULL or `delta_received != 'yes'` with prefetch | **Fully computable** | Excludes rows from #1; uses `prefetch_start_time` not `**` markers |
| 3 | Feedback delay > threshold | `response2 - end_time > 600s` | **Fully computable** | Threshold is `audit.threshold.feedback-delay-seconds` |
| 4 | Prefetch duration vs MRAD | `prefetch_end - prefetch_start` vs `total_cell_count` band | **Fully computable** | `>30k` flagged as *beyond matrix* |
| 5 | Total duration vs MRAD | `end - start` vs band | **Fully computable** | Same MRAD handling |
| 6 | Inordinate subscriber ratio | Needs TRAI baseline + geography join | **Blocked (partial)** | `dm` schema has no state/district column and TRAI source location is unconfirmed — see NEEDS_SIGN_OFF #10–11. Stub returns 0 with a warning, threshold still externalized. |
| 7a | Expired non-zero | `total_expired >0` or `sms_count_expired >0` | **Fully computable (new)** | Enabled by this schema |
| 7b | Arithmetic mismatch | `success+failure+expired != total_subscribers` | **Fully computable (new)** | Flags integrity issues; hypothesis, not hard invariant |

"Omit don't fabricate" — *message late received per ground report* is intentionally not implemented (no data source).

Remaining live-schema gaps: geography for check 6, TRAI baseline location, `boundary_restriction` lookup — all documented in `NEEDS_SIGN_OFF.md`.

## 2-Click Email Workflow

1. **Generate Report** — `POST /live/generate` runs all 7 checks, persists `discrepancy_records` + `ingestion_batches`, builds the `.xlsx` (or ZIP of per-TSP files), persists `generated_reports` (timestamp, date range, triggered-by, checksum), and redirects to **`/live/preview/{reportId}`** showing counts per check/TSP, a **Download** link, and a **Send Email** button. No auto-send.
2. **Send Email** — `POST /live/send/{reportId}` resolves recipients via `SELECT email_id FROM dm.t_tsp_contact_list WHERE LOWER(REPLACE(tsp_name,'-',' ')) = LOWER(REPLACE(:tsp,'-',' ')) AND deactivated_on IS NULL AND email_notifications = true` (hyphen/space-normalized), composes the email with the just-generated report attached, sends via the configured SMTP relay, and shows a confirmation (recipients, subject, success/failure) — never silently. If a TSP has zero active contacts, it is surfaced clearly and the send fails with `NO_RECIPIENTS`.

Recipients, subject, and status are audited back to `generated_reports.email_*`.

## Report Styling

`TspReportGenerationService` produces the styled `.xlsx` — bold colored headers, borders, one section per check type, empty sections omitted — matching the established conventions from the earlier prototype (light-blue `#B4C6E7` column headers, yellow section headers, thin borders, centered 14pt bold / 12pt data). Filter by date range and/or TSP before generation.

## Tests

Seed data in `V1000__seed_tests.sql` / test fixtures reproduces the brief's known patterns: a clean success row, a complete-failure row, a Reliance-Jio-style partial failure with `total_expired > 0` and a garbage `remarks_by_capplatform` string, an MRAD-breach row, and unknown-status + arithmetic-mismatch rows. Each check is asserted against its expected flagged set. Run with `mvn test` (H2, no Postgres needed).

## DDL / Migrations

`src/main/resources/db/migration/V1__dm_schema.sql` — exact `dm` DDL per brief.  
`src/main/resources/db/migration/V2__app_tables.sql` — `public.discrepancy_records`, `public.ingestion_batches`, `public.generated_reports`.

## Env Vars Reference

```
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/trai_audit
SPRING_DATASOURCE_USERNAME=trai_user
SPRING_DATASOURCE_PASSWORD=trai_pass
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=user
SPRING_MAIL_PASSWORD=secret
SPRING_MAIL_SMTP_AUTH=true
AUDIT_MAIL_FROM=noreply@cap-sachet.gov.in
AUDIT_MAIL_FROM_NAME=CAP Sachet Audit System
```

See `NEEDS_SIGN_OFF.md` for the 16 unresolved assumptions — mandatory reading before production.
