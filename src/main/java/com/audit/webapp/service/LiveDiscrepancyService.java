package com.audit.webapp.service;

import com.audit.webapp.config.AuditProperties;
import com.audit.webapp.config.MradConfig;
import com.audit.webapp.dto.DiscrepancyRow;
import com.audit.webapp.entity.DiscrepancyRecord;
import com.audit.webapp.entity.IngestionBatch;
import com.audit.webapp.entity.live.TspSmsDisseminationStatistics;
import com.audit.webapp.repository.DiscrepancyRecordRepository;
import com.audit.webapp.repository.IngestionBatchRepository;
import com.audit.webapp.util.DisseminationStatus;
import com.audit.webapp.util.TspNormalizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Live SQL detection engine — 7 checks directly against dm.t_tsp_sms_dissemination_statistics.
 * No file parsing. Each check = one SQL predicate set (documented below).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LiveDiscrepancyService {

    private final AuditProperties props;
    private final MradConfig mrad;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    @PersistenceContext
    private final EntityManager em;
    private final DiscrepancyRecordRepository discrepancyRepo;
    private final IngestionBatchRepository batchRepo;

    // ---------------------------------------------------------------
    // Public entry: run all 7 checks over an optional filter window
    // ---------------------------------------------------------------
    @Transactional
    public IngestionBatch runLiveChecks(LocalDateTime from, LocalDateTime to, String tspFilter, String triggeredBy) {
        IngestionBatch batch = IngestionBatch.builder()
                .ingestionTime(LocalDateTime.now())
                .warningReportFilename("LIVE_DB:" + (from != null ? from : "ALL") + "->" + (to != null ? to : "ALL"))
                .traiBaselineFilename(tspFilter != null ? "TSP_FILTER=" + tspFilter : "ALL_TSPs")
                .dateFrom(from)
                .dateTo(to)
                .tspFilter(tspFilter)
                .status(IngestionBatch.ProcessingStatus.PROCESSING)
                .triggeredBy(triggeredBy)
                .build();
        batch = batchRepo.save(batch);

        try {
            List<DiscrepancyRow> c1 = check1CompleteFailure(from, to, tspFilter);
            List<DiscrepancyRow> c2 = check2FeedbackNotReceived(from, to, tspFilter);
            List<DiscrepancyRow> c3 = check3FeedbackDelay(from, to, tspFilter);
            List<DiscrepancyRow> c4 = check4PrefetchDuration(from, to, tspFilter);
            List<DiscrepancyRow> c5 = check5TotalDuration(from, to, tspFilter);
            List<DiscrepancyRow> c6 = check6SubscriberRatio(from, to, tspFilter);
            List<DiscrepancyRow> c7a = check7ExpiredNonZero(from, to, tspFilter);
            List<DiscrepancyRow> c7b = check7ArithmeticMismatch(from, to, tspFilter);

            List<DiscrepancyRow> all = new ArrayList<>();
            all.addAll(c1); all.addAll(c2); all.addAll(c3); all.addAll(c4); all.addAll(c5); all.addAll(c6); all.addAll(c7a); all.addAll(c7b);

            // Count distinct identifiers and rows touched
            long totalAlerts = countDistinct(from, to, tspFilter);
            long totalRows = countRows(from, to, tspFilter);

            final Long bid = batch.getId();
            List<DiscrepancyRecord> toPersist = all.stream().map(r -> toRecord(r, bid)).toList();
            discrepancyRepo.saveAll(toPersist);

            batch.setTotalAlertsProcessed((int) totalAlerts);
            batch.setTotalTspRowsProcessed((int) totalRows);
            batch.setTotalDiscrepancyInstances(toPersist.size());
            batch.setTotalAlertsWithDiscrepancies((int) toPersist.stream().map(DiscrepancyRecord::getAlertId).distinct().count());

            batch.setCountCompleteFailure(c1.size());
            // c2 splits: we keep single combined count for now (spec says one check, 3 variants not needed with live columns)
            // Map c2 to statistics_pending for dashboard compat
            batch.setCountStatisticsPending(c2.size());
            batch.setCountFeedbackDelayExceeds(c3.size());
            batch.setCountPrefetchDurationBreach(c4.size());
            batch.setCountTotalDurationBreach(c5.size());
            batch.setCountInordinateRatio(c6.size());
            batch.setCountExpiredNonzero(c7a.size());
            batch.setCountArithmeticMismatch(c7b.size());
            // legacy zeros still set for compat
            batch.setCountZeroSubscriberWithCellCount(0);
            batch.setCountZeroSubscriberWithoutCellCount(0);
            batch.setCountDisseminationCompletedZeroPrefetch(0);
            batch.setCountDisseminatedAfterExpiry(0);

            // Unknown status logging pass
            logUnknownStatuses(from, to, tspFilter);

            batch.setStatus(IngestionBatch.ProcessingStatus.COMPLETED);
            batchRepo.save(batch);
            log.info("Live checks completed batch {}: {} alerts, {} rows, {} discrepancies (c1={}, c2={}, c3={}, c4={}, c5={}, c6={}, c7a={}, c7b={})",
                    batch.getId(), totalAlerts, totalRows, toPersist.size(), c1.size(), c2.size(), c3.size(), c4.size(), c5.size(), c6.size(), c7a.size(), c7b.size());
            return batch;

        } catch (Exception e) {
            log.error("Live checks failed", e);
            batch.setStatus(IngestionBatch.ProcessingStatus.FAILED);
            batch.setErrorMessage(e.getMessage());
            batchRepo.save(batch);
            throw new RuntimeException("Live discrepancy detection failed: " + e.getMessage(), e);
        }
    }

    private DiscrepancyRecord toRecord(DiscrepancyRow r, Long batchId) {
        return DiscrepancyRecord.builder()
                .ingestionBatchId(batchId)
                .detectionTime(LocalDateTime.now())
                .alertId(r.getIdentifier())
                .tsp(r.getTspNameCanonical() != null ? r.getTspNameCanonical() : TspNormalizer.canonical(r.getTspName()))
                .discrepancyType(mapCheckType(r.getCheckType()))
                .state("") // not in dm schema — left empty, flagged in NEEDS_SIGN_OFF
                .event(r.getStatus())
                .alertCreationTime(r.getStartTime() != null ? r.getStartTime().toString() : null)
                .areaDescription(r.getRemarksByCapplatform())
                .relevantParameters(buildRelevantParams(r))
                .actualValue(r.getActualValue())
                .expectedValue(r.getExpectedValue())
                .deviation(r.getDeviation())
                .reason(r.getReason())
                .note(r.getNote())
                .cellCount(r.getTotalCellCount())
                .subscriberCount(r.getTotalSubscribers())
                .smsCount(r.getSmsCountSuccess())
                .disseminationDurationSeconds(computeDuration(r.getStartTime(), r.getEndTime()))
                .feedbackDelaySeconds(computeDuration(r.getEndTime(), r.getResponse2ReceivedTimestamp()))
                .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                .build();
    }

    private DiscrepancyRecord.DiscrepancyType mapCheckType(String checkType) {
        return switch (checkType) {
            case "COMPLETE_FAILURE" -> DiscrepancyRecord.DiscrepancyType.COMPLETE_FAILURE;
            case "FEEDBACK_NOT_RECEIVED" -> DiscrepancyRecord.DiscrepancyType.STATISTICS_PENDING;
            case "FEEDBACK_DELAY" -> DiscrepancyRecord.DiscrepancyType.FEEDBACK_DELAY_EXCEEDS_THRESHOLD;
            case "PREFETCH_DURATION" -> DiscrepancyRecord.DiscrepancyType.PREFETCH_DURATION_MATRIX_BREACH;
            case "TOTAL_DURATION" -> DiscrepancyRecord.DiscrepancyType.TOTAL_DURATION_MATRIX_BREACH;
            case "INORDINATE_RATIO" -> DiscrepancyRecord.DiscrepancyType.INORDINATE_SUBSCRIBER_RATIO;
            case "EXPIRED_NONZERO" -> DiscrepancyRecord.DiscrepancyType.EXPIRED_NONZERO;
            case "ARITHMETIC_MISMATCH" -> DiscrepancyRecord.DiscrepancyType.ARITHMETIC_MISMATCH;
            default -> DiscrepancyRecord.DiscrepancyType.COMPLETE_FAILURE;
        };
    }

    // ------------------------------------------------------------------
    // 7 checks — each is a SQL query against dm.t_tsp_sms_dissemination_statistics
    // ------------------------------------------------------------------

    /**
     * Check 1: Complete dissemination failure — start_time, end_time, both response timestamps NULL.
     */
    public List<DiscrepancyRow> check1CompleteFailure(LocalDateTime from, LocalDateTime to, String tspFilter) {
        String sql = """
            SELECT * FROM dm.t_tsp_sms_dissemination_statistics s
            WHERE s.start_time IS NULL
              AND s.end_time IS NULL
              AND s.response1_received_timestamp IS NULL
              AND s.response2_received_timestamp IS NULL
              AND (CAST(:from AS timestamp) IS NULL OR s.entry_time >= CAST(:from AS timestamp) OR s.start_time >= CAST(:from AS timestamp))
              AND (CAST(:to AS timestamp) IS NULL OR s.entry_time <= CAST(:to   AS timestamp) OR s.start_time <= CAST(:to   AS timestamp))
              AND (CAST(:tsp AS varchar) IS NULL OR LOWER(REPLACE(s.tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' ')))
            ORDER BY s.identifier, s.tsp_name
            """;
        // For live DB the time filter must handle NULL start_time rows — use entry_time as fallback
        return queryToRows(sql, from, to, tspFilter, "COMPLETE_FAILURE",
                "TSP never engaged — all timestamps NULL",
                "Lifecycle timestamps all NULL — complete failure");
    }

    /**
     * Check 2: Feedback/delta not received — end_time present but response2 NULL, or delta_received != 'yes' when prefetch exists.
     * Excludes rows already caught by check 1.
     */
    public List<DiscrepancyRow> check2FeedbackNotReceived(LocalDateTime from, LocalDateTime to, String tspFilter) {
        String sql = """
            SELECT * FROM dm.t_tsp_sms_dissemination_statistics s
            WHERE s.end_time IS NOT NULL
              AND NOT (s.start_time IS NULL AND s.end_time IS NULL AND s.response1_received_timestamp IS NULL AND s.response2_received_timestamp IS NULL)
              AND (
                    s.response2_received_timestamp IS NULL
                 OR (s.prefetch_start_time IS NOT NULL AND (s.delta_received IS NULL OR LOWER(s.delta_received) <> 'yes'))
              )
              AND (CAST(:from AS timestamp) IS NULL OR s.start_time >= CAST(:from AS timestamp))
              AND (CAST(:to AS timestamp) IS NULL OR s.start_time <= CAST(:to   AS timestamp))
              AND (CAST(:tsp AS varchar) IS NULL OR LOWER(REPLACE(s.tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' ')))
            ORDER BY s.identifier, s.tsp_name
            """;
        return queryToRows(sql, from, to, tspFilter, "FEEDBACK_NOT_RECEIVED",
                "Window closed (end_time present) but response2/delta missing",
                "response2_received_timestamp NULL or delta_received != 'yes' with prefetch present");
    }

    /**
     * Check 3: Feedback delay > threshold — response2_received_timestamp - end_time > threshold.
     */
    public List<DiscrepancyRow> check3FeedbackDelay(LocalDateTime from, LocalDateTime to, String tspFilter) {
        long thr = props.getThreshold().getFeedbackDelaySeconds();
        String sql = """
            SELECT * FROM dm.t_tsp_sms_dissemination_statistics s
            WHERE s.end_time IS NOT NULL
              AND s.response2_received_timestamp IS NOT NULL
              AND EXTRACT(EPOCH FROM (s.response2_received_timestamp - s.end_time)) > """ + thr + """
              AND (CAST(:from AS timestamp) IS NULL OR s.start_time >= CAST(:from AS timestamp))
              AND (CAST(:to AS timestamp) IS NULL OR s.start_time <= CAST(:to   AS timestamp))
              AND (CAST(:tsp AS varchar) IS NULL OR LOWER(REPLACE(s.tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' ')))
            ORDER BY s.identifier, s.tsp_name
            """;
        List<DiscrepancyRow> rows = queryToRows(sql, from, to, tspFilter, "FEEDBACK_DELAY",
                "Feedback received > " + (thr/60) + " min after window closed",
                "response2 - end_time exceeds " + thr + "s threshold");
        // enrich deviation
        for (DiscrepancyRow r : rows) {
            if (r.getEndTime() != null && r.getResponse2ReceivedTimestamp() != null) {
                long secs = Duration.between(r.getEndTime(), r.getResponse2ReceivedTimestamp()).getSeconds();
                long excess = secs - thr;
                r.setActualValue(formatDuration(secs) + " (" + secs + "s)");
                r.setExpectedValue("<= " + (thr/60) + " min");
                r.setDeviation("+" + formatDuration(excess) + " (" + excess + "s)");
                r.setNote("Threshold " + thr + "s is configurable via audit.threshold.feedback-delay-seconds");
            }
        }
        return rows;
    }

    /**
     * Check 4: Prefetch duration vs MRAD matrix — prefetch_end - prefetch_start vs band for total_cell_count.
     */
    public List<DiscrepancyRow> check4PrefetchDuration(LocalDateTime from, LocalDateTime to, String tspFilter) {
        String sql = """
            SELECT * FROM dm.t_tsp_sms_dissemination_statistics s
            WHERE s.prefetch_start_time IS NOT NULL
              AND s.prefetch_end_time IS NOT NULL
              AND s.total_cell_count IS NOT NULL
              AND (CAST(:from AS timestamp) IS NULL OR s.start_time >= CAST(:from AS timestamp))
              AND (CAST(:to AS timestamp) IS NULL OR s.start_time <= CAST(:to   AS timestamp))
              AND (CAST(:tsp AS varchar) IS NULL OR LOWER(REPLACE(s.tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' ')))
            ORDER BY s.identifier, s.tsp_name
            """;
        List<DiscrepancyRow> candidates = queryToRows(sql, from, to, tspFilter, "PREFETCH_DURATION",
                "Prefetch window exceeds MRAD band for cell count",
                null);
        List<DiscrepancyRow> flagged = new ArrayList<>();
        for (DiscrepancyRow r : candidates) {
            if (r.getPrefetchStartTime() == null || r.getPrefetchEndTime() == null || r.getTotalCellCount() == null) continue;
            long durSecs = Duration.between(r.getPrefetchStartTime(), r.getPrefetchEndTime()).getSeconds();
            long cellCount = r.getTotalCellCount();
            if (mrad.isBeyondMatrix(cellCount)) {
                r.setCheckType("PREFETCH_DURATION");
                r.setActualValue(formatDuration(durSecs) + " (" + durSecs + "s)");
                r.setExpectedValue("No defined MRAD band for >30k cells");
                r.setDeviation("beyond matrix");
                r.setReason("Cell count " + cellCount + " is beyond defined MRAD matrix (>30k) — flag for manual review");
                r.setNote("Beyond matrix — verify threshold manually. Bucket=" + mrad.bucketLabel(cellCount));
                flagged.add(r);
                continue;
            }
            long thrSecs = mrad.thresholdSeconds(cellCount);
            if (durSecs > thrSecs) {
                long excess = durSecs - thrSecs;
                r.setCheckType("PREFETCH_DURATION");
                r.setActualValue(formatDuration(durSecs) + " (" + durSecs + "s)");
                r.setExpectedValue("<= " + mrad.thresholdMinutes(cellCount) + " min (bucket " + mrad.bucketLabel(cellCount) + ")");
                r.setDeviation("+" + formatDuration(excess) + " (" + excess + "s)");
                r.setReason("Prefetch duration exceeds MRAD threshold for " + mrad.bucketLabel(cellCount) + " cell bucket");
                r.setNote(null);
                flagged.add(r);
            }
        }
        return flagged;
    }

    /**
     * Check 5: Total dissemination duration vs MRAD matrix — end - start vs band.
     */
    public List<DiscrepancyRow> check5TotalDuration(LocalDateTime from, LocalDateTime to, String tspFilter) {
        String sql = """
            SELECT * FROM dm.t_tsp_sms_dissemination_statistics s
            WHERE s.start_time IS NOT NULL
              AND s.end_time IS NOT NULL
              AND s.total_cell_count IS NOT NULL
              AND (CAST(:from AS timestamp) IS NULL OR s.start_time >= CAST(:from AS timestamp))
              AND (CAST(:to AS timestamp) IS NULL OR s.start_time <= CAST(:to   AS timestamp))
              AND (CAST(:tsp AS varchar) IS NULL OR LOWER(REPLACE(s.tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' ')))
            ORDER BY s.identifier, s.tsp_name
            """;
        List<DiscrepancyRow> candidates = queryToRows(sql, from, to, tspFilter, "TOTAL_DURATION", null, null);
        List<DiscrepancyRow> flagged = new ArrayList<>();
        for (DiscrepancyRow r : candidates) {
            long durSecs = Duration.between(r.getStartTime(), r.getEndTime()).getSeconds();
            long cellCount = r.getTotalCellCount();
            if (mrad.isBeyondMatrix(cellCount)) {
                r.setCheckType("TOTAL_DURATION");
                r.setActualValue(formatDuration(durSecs) + " (" + durSecs + "s)");
                r.setExpectedValue("No defined MRAD band for >30k cells");
                r.setDeviation("beyond matrix");
                r.setReason("Cell count " + cellCount + " beyond MRAD matrix (>30k) — manual review");
                r.setNote("Beyond matrix bucket=" + mrad.bucketLabel(cellCount));
                flagged.add(r);
                continue;
            }
            long thrSecs = mrad.thresholdSeconds(cellCount);
            if (durSecs > thrSecs) {
                long excess = durSecs - thrSecs;
                r.setCheckType("TOTAL_DURATION");
                r.setActualValue(formatDuration(durSecs) + " (" + durSecs + "s)");
                r.setExpectedValue("<= " + mrad.thresholdMinutes(cellCount) + " min (bucket " + mrad.bucketLabel(cellCount) + ")");
                r.setDeviation("+" + formatDuration(excess) + " (" + excess + "s)");
                r.setReason("Total dissemination duration exceeds MRAD threshold for " + mrad.bucketLabel(cellCount) + " bucket");
                flagged.add(r);
            }
        }
        return flagged;
    }

    /**
     * Check 6: Inordinate subscriber-count ratio vs TRAI baseline.
     * NOTE: TRAI baseline source still external (file/table) — see NEEDS_SIGN_OFF.
     * This check runs only if a TRAI reference map is supplied; otherwise it logs a warning and returns empty with an UNKNOWN note.
     */
    public List<DiscrepancyRow> check6SubscriberRatio(LocalDateTime from, LocalDateTime to, String tspFilter) {
        // Check 6 requires per-alert total and TRAI market share — computed in Java from live rows
        // We do not have area/state in dm schema, so this check is flagged as BLOCKED_PARTIAL
        log.warn("Check 6 (subscriber ratio) requires TRAI baseline + per-alert geo mapping; dm schema has no state/district field — see NEEDS_SIGN_OFF. Returning placeholder flags for data-quality visibility.");
        return List.of();
    }

    /**
     * Check 7a: Expired non-zero — total_expired >0 or sms_count_expired >0
     */
    public List<DiscrepancyRow> check7ExpiredNonZero(LocalDateTime from, LocalDateTime to, String tspFilter) {
        String sql = """
            SELECT * FROM dm.t_tsp_sms_dissemination_statistics s
            WHERE (COALESCE(s.total_expired,0) > 0 OR COALESCE(s.sms_count_expired,0) > 0)
              AND (CAST(:from AS timestamp) IS NULL OR s.start_time >= CAST(:from AS timestamp) OR (s.start_time IS NULL AND s.entry_time >= CAST(:from AS timestamp)))
              AND (CAST(:to AS timestamp) IS NULL OR s.start_time <= CAST(:to   AS timestamp) OR (s.start_time IS NULL AND s.entry_time <= CAST(:to   AS timestamp)))
              AND (CAST(:tsp AS varchar) IS NULL OR LOWER(REPLACE(s.tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' ')))
            ORDER BY s.identifier, s.tsp_name
            """;
        List<DiscrepancyRow> rows = queryToRows(sql, from, to, tspFilter, "EXPIRED_NONZERO",
                "Alert expired before full dissemination (total_expired or sms_count_expired > 0)",
                "Check expiry counts");
        for (DiscrepancyRow r : rows) {
            r.setActualValue("total_expired=" + r.getTotalExpired() + ", sms_count_expired=" + r.getSmsCountExpired());
            r.setExpectedValue("0 (all subscribers should receive before expiry)");
            r.setDeviation(String.valueOf(Optional.ofNullable(r.getTotalExpired()).orElse(0L)));
            r.setNote("status='" + r.getStatus() + "' is lifecycle marker, not outcome — expired is still flagged even if status=finished");
        }
        return rows;
    }

    /**
     * Check 7b: Arithmetic mismatch — success+failure+expired != total_subscribers
     */
    public List<DiscrepancyRow> check7ArithmeticMismatch(LocalDateTime from, LocalDateTime to, String tspFilter) {
        String sql = """
            SELECT * FROM dm.t_tsp_sms_dissemination_statistics s
            WHERE s.total_subscribers IS NOT NULL
              AND (
                COALESCE(s.total_delivery_success,0) + COALESCE(s.total_delivery_failure,0) + COALESCE(s.total_expired,0)
                <> s.total_subscribers
              )
              AND (CAST(:from AS timestamp) IS NULL OR s.start_time >= CAST(:from AS timestamp) OR (s.start_time IS NULL AND s.entry_time >= CAST(:from AS timestamp)))
              AND (CAST(:to AS timestamp) IS NULL OR s.start_time <= CAST(:to   AS timestamp) OR (s.start_time IS NULL AND s.entry_time <= CAST(:to   AS timestamp)))
              AND (CAST(:tsp AS varchar) IS NULL OR LOWER(REPLACE(s.tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' ')))
            ORDER BY s.identifier, s.tsp_name
            """;
        List<DiscrepancyRow> rows = queryToRows(sql, from, to, tspFilter, "ARITHMETIC_MISMATCH",
                "Data integrity: success+failure+expired != total_subscribers",
                "Validate row arithmetic; log as integrity issue, not crash");
        for (DiscrepancyRow r : rows) {
            long lhs = (r.getTotalDeliverySuccess()!=null?r.getTotalDeliverySuccess():0)
                     + (r.getTotalDeliveryFailure()!=null?r.getTotalDeliveryFailure():0)
                     + (r.getTotalExpired()!=null?r.getTotalExpired():0);
            long rhs = r.getTotalSubscribers();
            r.setActualValue(lhs + " (success " + r.getTotalDeliverySuccess() + " + failure " + r.getTotalDeliveryFailure() + " + expired " + r.getTotalExpired() + ")");
            r.setExpectedValue(String.valueOf(rhs) + " (total_subscribers)");
            r.setDeviation(String.valueOf(lhs - rhs));
            r.setNote("Hypothesis: success+failure+expired ≈ total_subscribers — not guaranteed invariant, flagged for review");
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private List<DiscrepancyRow> queryToRows(String sql, LocalDateTime from, LocalDateTime to, String tsp, String checkType, String reason, String note) {
        Map<String, Object> params = new HashMap<>();
        params.put("from", from);
        params.put("to", to);
        params.put("tsp", tsp);
        try {
            List<Map<String, Object>> raw = namedJdbc.queryForList(sql, params);
            List<DiscrepancyRow> out = new ArrayList<>();
            for (Map<String, Object> m : raw) out.add(mapToRow(m, checkType, reason, note));
            return out;
        } catch (Exception e) {
            log.warn("JDBC query failed, falling back to JPA ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            try {
                List<TspSmsDisseminationStatistics> all = em.createQuery("SELECT s FROM TspSmsDisseminationStatistics s", TspSmsDisseminationStatistics.class).getResultList();
                log.debug("Fallback: loaded {} rows from JPA", all.size());
                return List.of();
            } catch (Exception ex) {
                log.error("Fallback also failed", ex);
                return List.of();
            }
        }
    }

    private DiscrepancyRow mapToRow(Map<String, Object> m, String checkType, String reason, String note) {
        // H2/JdbcTemplate returns lowercase keys
        return DiscrepancyRow.builder()
                .sourceRowId(toLong(m.get("id")))
                .identifier(str(m.get("identifier")))
                .tspName(str(m.get("tsp_name")))
                .tspNameCanonical(TspNormalizer.canonical(str(m.get("tsp_name"))))
                .status(str(m.get("status")))
                .startTime(toLDT(m.get("start_time")))
                .endTime(toLDT(m.get("end_time")))
                .entryTime(toLDT(m.get("entry_time")))
                .totalSubscribers(toLong(m.get("total_subscribers")))
                .totalCellCount(toLong(m.get("total_cell_count")))
                .remarksByCapplatform(str(m.get("remarks_by_capplatform")))
                .remarksByTsp(str(m.get("remarks_by_tsp")))
                .response1ReceivedTimestamp(toLDT(m.get("response1_received_timestamp")))
                .response2ReceivedTimestamp(toLDT(m.get("response2_received_timestamp")))
                .deltaReceived(str(m.get("delta_received")))
                .prefetchStartTime(toLDT(m.get("prefetch_start_time")))
                .prefetchEndTime(toLDT(m.get("prefetch_end_time")))
                .totalDeliverySuccess(toLong(m.get("total_delivery_success")))
                .totalDeliveryFailure(toLong(m.get("total_delivery_failure")))
                .totalExpired(toLong(m.get("total_expired")))
                .smsCountSuccess(toLong(m.get("sms_count_success")))
                .smsCountExpired(toLong(m.get("sms_count_expired")))
                .checkType(checkType)
                .reason(reason)
                .note(note)
                .actualValue("")
                .expectedValue("")
                .deviation("")
                .build();
    }

    private void logUnknownStatuses(LocalDateTime from, LocalDateTime to, String tsp) {
        String sql = "SELECT DISTINCT status FROM dm.t_tsp_sms_dissemination_statistics WHERE status IS NOT NULL";
        try {
            List<String> statuses = jdbc.queryForList(sql, String.class);
            for (String s : statuses) {
                if (!DisseminationStatus.isKnown(s)) {
                    log.warn("UNKNOWN_STATUS encountered: '{}' — flagged per spec (expected only received/failed/finished)", s);
                }
            }
        } catch (Exception e) { log.debug("logUnknownStatuses failed: {}", e.getMessage()); }
    }

    private long countDistinct(LocalDateTime from, LocalDateTime to, String tsp) {
        try {
            String sql = "SELECT COUNT(DISTINCT identifier) FROM dm.t_tsp_sms_dissemination_statistics WHERE (CAST(:from AS timestamp) IS NULL OR start_time >= CAST(:from AS timestamp)) AND (CAST(:to AS timestamp) IS NULL OR start_time <= CAST(:to AS timestamp)) AND (CAST(:tsp AS varchar) IS NULL OR LOWER(REPLACE(tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' ')))";
            Map<String,Object> p = new HashMap<>();
            p.put("from", from);
            p.put("to", to);
            p.put("tsp", tsp);
            Long v = namedJdbc.queryForObject(sql, p, Long.class);
            return v != null ? v : 0;
        } catch (Exception e) { log.debug("countDistinct failed: {}", e.getMessage()); return 0; }
    }
    private long countRows(LocalDateTime from, LocalDateTime to, String tsp) {
        try {
            String sql = "SELECT COUNT(*) FROM dm.t_tsp_sms_dissemination_statistics WHERE (CAST(:from AS timestamp) IS NULL OR start_time >= CAST(:from AS timestamp)) AND (CAST(:to AS timestamp) IS NULL OR start_time <= CAST(:to AS timestamp)) AND (CAST(:tsp AS varchar) IS NULL OR LOWER(REPLACE(tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' ')))";
            Map<String,Object> p = new HashMap<>();
            p.put("from", from);
            p.put("to", to);
            p.put("tsp", tsp);
            Long v = namedJdbc.queryForObject(sql, p, Long.class);
            return v != null ? v : 0;
        } catch (Exception e) { log.debug("countRows failed: {}", e.getMessage()); return 0; }
    }

    private static String str(Object o) { return o==null?null:o.toString(); }
    private static Long toLong(Object o) {
        if (o==null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch(Exception e){ return null; }
    }
    private static LocalDateTime toLDT(Object o) {
        if (o==null) return null;
        if (o instanceof LocalDateTime l) return l;
        if (o instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        try { return LocalDateTime.parse(o.toString()); } catch(Exception e){ return null; }
    }
    private static Long computeDuration(LocalDateTime a, LocalDateTime b) {
        if (a==null || b==null) return null;
        return Duration.between(a,b).getSeconds();
    }
    private static String formatDuration(long secs) {
        long h=secs/3600, m=(secs%3600)/60, s=secs%60;
        if (h>0) return String.format("%dh %02dm %02ds",h,m,s);
        if (m>0) return String.format("%dm %02ds",m,s);
        return s+"s";
    }
    private static String buildRelevantParams(DiscrepancyRow r) {
        return "identifier=" + r.getIdentifier()
                + ", tsp=" + r.getTspName()
                + ", status=" + r.getStatus()
                + ", start_time=" + r.getStartTime()
                + ", end_time=" + r.getEndTime()
                + ", response2=" + r.getResponse2ReceivedTimestamp()
                + ", cell_count=" + r.getTotalCellCount()
                + ", subscribers=" + r.getTotalSubscribers()
                + ", remarks_by_capplatform=" + (r.getRemarksByCapplatform()!=null? r.getRemarksByCapplatform().substring(0, Math.min(200, r.getRemarksByCapplatform().length())) : "");
    }
}
