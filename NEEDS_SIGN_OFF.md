# NEEDS_SIGN_OFF — Unresolved Assumptions

This document lists every assumption that must be confirmed with the data owner / DoT-NDMA official before production use. Nothing here is silently baked in as fact.

---

### 1. `status` enum completeness
**Confirmed so far:** `'received'`, `'failed'`, `'finished'` (from real sample rows).  
**Assumption:** No other values exist.  
**Implementation:** `DisseminationStatus.java` handles these three explicitly; any other value is logged as `UNKNOWN_STATUS` and flagged, not silently defaulted.  
**Sign-off needed:** Provide the authoritative enum list from the source system (or confirm these three are exhaustive).

### 2. `status='finished'` does not mean success
A `finished` row can still carry `remarks_by_capplatform = "Unable to connect to Reliance Jio server"` and `total_expired > 0`.  
**Implementation:** Treat `status` as a lifecycle marker (window closed), not an outcome. Outcome is derived from `total_expired`, `total_delivery_success/failure`, and `remarks_by_capplatform` free text.  
**Sign-off needed:** Confirm that outcome should indeed be derived from counts, not status.

### 3. `remarks_by_capplatform` is free text / garbage HTML
Confirmed as raw error strings or dumped HTTP response bodies. Never parsed as structured data.  
**Implementation:** Stored as `areaDescription` / note verbatim; no regex/JSON parsing.  
**Sign-off needed:** Confirm no structured contract is expected for this column.

### 4. Arithmetic hypothesis: `success + failure + expired ≈ total_subscribers`
Confirmed from one real example; treated as a *strong hypothesis*, not a guaranteed invariant.  
**Implementation:** Check 7b flags `success+failure+expired != total_subscribers` as `ARITHMETIC_MISMATCH` (data-integrity warning, not a hard failure).  
**Sign-off needed:** Confirm whether this should be an exact invariant, an approximate one (± tolerance), or just informational.

### 5. Pre-fetch is a real earlier snapshot — no `**` markers
`dm` schema has `prefetch_start_time`, `prefetch_end_time`, `prefetch_total_subscribers` etc. that close *before* the final `start_time`/`end_time` window.  
**Implementation:** All prefetch logic uses those columns; the old Excel `**` text-marker workaround has been removed entirely.  
**Sign-off needed:** Confirm that `prefetch_*` columns are indeed the authoritative earlier snapshot.

### 6. `response1_received_timestamp` vs `response2_received_timestamp`
Working hypothesis: response1 = initial webhook ack, response2 = stats payload.  
**Implementation:** Both timestamps are surfaced in the discrepancy record's `relevantParameters` and note. No logic silently assumes one is authoritative. Check 2/3 use `response2` as the stats-carrying response, but this is flagged as UNCONFIRMED in the note.  
**Sign-off needed:** Confirm which response carries the real stats.

### 7. `delta_received = 'yes'` semantics
Appears to mark a follow-up/delta after prefetch.  
**Implementation:** Treated as informational; Check 2 uses `delta_received != 'yes'` with a prefetch present as a feedback-not-received signal, but flagged as tentative.  
**Sign-off needed:** Confirm whether `delta_received` is check-worthy or just metadata.

### 8. MRAD matrix bands and >30k gap
Matrix: 0–5k → 60 min, 5–15k → 90 min, 15–30k → 120 min. Source image only defines up to 30k.  
**Implementation:** `MradConfig.java` driven by `audit.mrad.band-*` props (60/90/120). Rows with `total_cell_count > 30_000` are flagged as `beyond matrix` (manual review), not silently pass/fail.  
**Sign-off needed:** Provide the official band for >30k, or confirm that flagging for manual review is correct.

### 9. Feedback delay threshold: 600s vs 300s
Spec header says 5 min, body says 10 min. Validated baseline used 600s.  
**Implementation:** `audit.threshold.feedback-delay-seconds=600` (configurable).  
**Sign-off needed:** Confirm 5 vs 10 minutes.

### 10. Subscriber-ratio threshold: 15 percentage points (placeholder)
No official number ever provided.  
**Implementation:** `audit.threshold.subscriber-ratio-deviation-pct=15.0` (configurable, single `AuditProperties`). Check 6 currently returns empty because `dm` schema has no geographic field to join to TRAI circles.  
**Sign-off needed:** Provide the real threshold and the geographic join key (which `dm` column maps to TRAI circle/state?).

### 11. TRAI baseline source location
Check 6 still needs a TRAI wireless subscriber base (market share per circle). Brief says to ask where it now lives; do not assume it's in Postgres.  
**Implementation:** Check 6 is stubbed to return 0 with a warning and a `NEEDS_SIGN_OFF` note; the threshold prop is still externalized. Supply a file path or a Postgres table (e.g. `dm.trai_baseline`) and the check will be wired up.  
**Sign-off needed:** Where does the TRAI baseline now live (file, table, API)?

### 12. `dm.t_tsp_contact_list.boundary_restriction` / `element_id`
`boundary_restriction` may scope a contact to a state/circle; `element_id` likely maps to a geography lookup table that was not found.  
**Implementation:** Recipient resolution is `WHERE tsp_name = <normalized> AND deactivated_on IS NULL AND email_notifications = true` only. `boundary_restriction` is displayed in the preview/email body as an opaque string but NOT used to filter, and flagged here as unresolved rather than guessed.  
**Sign-off needed:** Provide the boundary/geography lookup table or confirm that TSP-level filtering is sufficient.

### 13. Ordinal position 27 gap in live schema
Live schema has a dropped/renamed column at position 27.  
**Implementation:** DDL in `V1__dm_schema.sql` mirrors the gap (no phantom column). JPA entities use explicit `@Column(name=...)` so ordinal position is irrelevant.  
**Sign-off needed:** Confirm no column is expected at position 27.

### 14. SMTP relay specifics
Host, port, from-address, auth are externalized to `application.properties` / env vars (`SPRING_MAIL_HOST`, `AUDIT_MAIL_FROM`, etc.) and never hardcoded. A local dummy (port 25) is the default.  
**Sign-off needed:** Provide the real SMTP relay host/port/credentials and the approved from-address for production.

### 15. "Message late received per ground report" omitted
No data source for it exists.  
**Implementation:** Omitted rather than fabricating a proxy — per brief's explicit instruction. Flagged here as intentionally not implemented.  
**Sign-off needed:** Confirm nothing is expected here.

### 16. State/area fields not in `dm` schema
`dm.t_tsp_sms_dissemination_statistics` has no `state`, `district`, or `area_description` column. All `DiscrepancyRecord.state` values will be empty until a join (e.g. alert master table) is supplied.  
**Implementation:** Check outputs set `state=""` and note the gap; reports still generate correctly with identifier + TSP as the primary key.  
**Sign-off needed:** Which table holds the alert's geography that should be joined on `identifier`?
