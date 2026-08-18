package com.audit.webapp.service;

import com.audit.checks.*;
import com.audit.io.TraiBaselineReader;
import com.audit.io.WarningReportReader;
import com.audit.model.AlertGroup;
import com.audit.model.TspRow;
import com.audit.webapp.entity.DiscrepancyRecord;
import com.audit.webapp.entity.DiscrepancyRecord.DiscrepancyType;
import com.audit.webapp.entity.IngestionBatch;
import com.audit.webapp.entity.IngestionBatch.ProcessingStatus;
import com.audit.webapp.repository.DiscrepancyRecordRepository;
import com.audit.webapp.repository.IngestionBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Core detection engine service that:
 * 1. Reuses the validated CLI engine's parsing/detection logic exactly
 * 2. Extends it with the 3 new categories (split checks 1/2/3, add checks 8/9)
 * 3. Persists all discrepancies as structured DB records
 * 
 * REGRESSION BASELINE (Section G): Categories 5, 6, 7 must match:
 * - Category 5 (old Check #4): 147 flagged
 * - Category 6 (old Check #5): 571 flagged
 * - Category 7 (old Check #6): 479 flagged
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DiscrepancyDetectionService {

    private final DiscrepancyRecordRepository discrepancyRepository;
    private final IngestionBatchRepository batchRepository;

    @Value("${audit.threshold.feedback-delay-seconds:600}")
    private long feedbackDelayThresholdSeconds;

    @Value("${audit.threshold.subscriber-ratio-deviation-pct:15.0}")
    private double subscriberRatioDeviationPct;

    @Value("${audit.threshold.recency-window-hours:24}")
    private long recencyWindowHours;

    /**
     * Main ingestion method: reads files, runs all 9 detection categories, persists results.
     */
    @Transactional
    public IngestionBatch processFiles(String warningReportPath, String traiBaselinePath) {
        log.info("Starting ingestion: warningReport={}, traiBaseline={}", warningReportPath, traiBaselinePath);

        IngestionBatch batch = IngestionBatch.builder()
                .ingestionTime(LocalDateTime.now())
                .warningReportFilename(extractFilename(warningReportPath))
                .traiBaselineFilename(extractFilename(traiBaselinePath))
                .status(ProcessingStatus.PROCESSING)
                .totalAlertsProcessed(0)
                .totalTspRowsProcessed(0)
                .totalAlertsWithDiscrepancies(0)
                .totalDiscrepancyInstances(0)
                .build();
        batch = batchRepository.save(batch);

        try {
            // Read input files using validated CLI engine parsers
            List<AlertGroup> groups = WarningReportReader.read(warningReportPath);
            Map<String, Map<String, Double>> traiBaseline = TraiBaselineReader.read(traiBaselinePath);

            int totalTspRows = groups.stream().mapToInt(g -> g.tspRows.size()).sum();
            batch.setTotalAlertsProcessed(groups.size());
            batch.setTotalTspRowsProcessed(totalTspRows);

            log.info("Parsed {} alerts, {} TSP rows", groups.size(), totalTspRows);

            // Run all 9 categories and persist
            List<DiscrepancyRecord> allDiscrepancies = new ArrayList<>();

            allDiscrepancies.addAll(detectCategory1CompleteFailure(groups, batch));
            allDiscrepancies.addAll(detectCategory2ZeroSubscriberCount(groups, batch));
            allDiscrepancies.addAll(detectCategory3FeedbackNotReceived(groups, batch));
            allDiscrepancies.addAll(detectCategory4FeedbackDelay(groups, batch));
            allDiscrepancies.addAll(detectCategory5PrefetchDuration(groups, batch));
            allDiscrepancies.addAll(detectCategory6TotalDuration(groups, batch));
            allDiscrepancies.addAll(detectCategory7SubscriberRatio(groups, traiBaseline, batch));
            allDiscrepancies.addAll(detectCategory8DisseminationCompletedZeroPrefetch(groups, batch));
            allDiscrepancies.addAll(detectCategory9AfterExpiry(groups, batch));

            // Persist all discrepancies
            discrepancyRepository.saveAll(allDiscrepancies);

            // Update batch statistics
            batch.setTotalDiscrepancyInstances(allDiscrepancies.size());
            batch.setTotalAlertsWithDiscrepancies(
                    (int) allDiscrepancies.stream().map(DiscrepancyRecord::getAlertId).distinct().count()
            );

            // Category-wise counts
            Map<DiscrepancyType, Long> typeCounts = new HashMap<>();
            allDiscrepancies.forEach(d -> typeCounts.merge(d.getDiscrepancyType(), 1L, Long::sum));

            batch.setCountCompleteFailure(typeCounts.getOrDefault(DiscrepancyType.COMPLETE_FAILURE, 0L).intValue());
            batch.setCountZeroSubscriberWithCellCount(typeCounts.getOrDefault(DiscrepancyType.ZERO_SUBSCRIBER_WITH_CELL_COUNT, 0L).intValue());
            batch.setCountZeroSubscriberWithoutCellCount(typeCounts.getOrDefault(DiscrepancyType.ZERO_SUBSCRIBER_WITHOUT_CELL_COUNT, 0L).intValue());
            batch.setCountStatisticsPending(typeCounts.getOrDefault(DiscrepancyType.STATISTICS_PENDING, 0L).intValue());
            batch.setCountStatisticsAwaited(typeCounts.getOrDefault(DiscrepancyType.STATISTICS_AWAITED, 0L).intValue());
            batch.setCountDeltaPending(typeCounts.getOrDefault(DiscrepancyType.DELTA_PENDING, 0L).intValue());
            batch.setCountFeedbackDelayExceeds(typeCounts.getOrDefault(DiscrepancyType.FEEDBACK_DELAY_EXCEEDS_THRESHOLD, 0L).intValue());
            batch.setCountPrefetchDurationBreach(typeCounts.getOrDefault(DiscrepancyType.PREFETCH_DURATION_MATRIX_BREACH, 0L).intValue());
            batch.setCountTotalDurationBreach(typeCounts.getOrDefault(DiscrepancyType.TOTAL_DURATION_MATRIX_BREACH, 0L).intValue());
            batch.setCountInordinateRatio(typeCounts.getOrDefault(DiscrepancyType.INORDINATE_SUBSCRIBER_RATIO, 0L).intValue());
            batch.setCountDisseminationCompletedZeroPrefetch(typeCounts.getOrDefault(DiscrepancyType.DISSEMINATION_COMPLETED_ZERO_PREFETCH, 0L).intValue());
            batch.setCountDisseminatedAfterExpiry(typeCounts.getOrDefault(DiscrepancyType.DISSEMINATED_AFTER_EXPIRY, 0L).intValue());

            batch.setStatus(ProcessingStatus.COMPLETED);
            batchRepository.save(batch);

            log.info("Ingestion completed: {} discrepancies across {} alerts", 
                    allDiscrepancies.size(), batch.getTotalAlertsWithDiscrepancies());

            // Log regression check for unchanged categories
            logRegressionCheck(batch);

            return batch;

        } catch (Exception e) {
            log.error("Ingestion failed", e);
            batch.setStatus(ProcessingStatus.FAILED);
            batch.setErrorMessage(e.getMessage());
            batchRepository.save(batch);
            throw new RuntimeException("File processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Category 1: Alert Dissemination Complete Failure
     * Reuses Check1CompleteFailure logic exactly
     */
    private List<DiscrepancyRecord> detectCategory1CompleteFailure(List<AlertGroup> groups, IngestionBatch batch) {
        log.debug("Running Category 1: Complete Failure");
        List<DiscrepancyRecord> records = new ArrayList<>();

        for (AlertGroup g : groups) {
            for (TspRow t : g.tspRows) {
                if (!isDash(t.cellCountRaw) || !isDash(t.disseminationDurationRaw)) continue;

                records.add(DiscrepancyRecord.builder()
                        .ingestionBatchId(batch.getId())
                        .detectionTime(batch.getIngestionTime())
                        .alertId(g.identifier)
                        .tsp(t.tsp)
                        .discrepancyType(DiscrepancyType.COMPLETE_FAILURE)
                        .state(g.state)
                        .event(g.event)
                        .alertCreationTime(g.alertCreationTime)
                        .areaDescription(g.areaDescription)
                        .slNo(g.slNo)
                        .relevantParameters("Cell Count: " + t.cellCountRaw + ", Dissemination Duration: " + t.disseminationDurationRaw)
                        .actualValue("No data")
                        .expectedValue("Cell Count and Dissemination Duration")
                        .reason("TSP produced no response at all for this alert - complete dissemination failure")
                        .note("Cell Count and Dissemination Duration both \"--\" (no data sent)")
                        .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                        .build());
            }
        }

        log.info("Category 1 flagged: {}", records.size());
        return records;
    }

    /**
     * Category 2: Zero Subscriber Count - split into 2 sub-categories
     */
    private List<DiscrepancyRecord> detectCategory2ZeroSubscriberCount(List<AlertGroup> groups, IngestionBatch batch) {
        log.debug("Running Category 2: Zero Subscriber Count (WITH/WITHOUT Cell Count)");
        List<DiscrepancyRecord> records = new ArrayList<>();

        for (AlertGroup g : groups) {
            for (TspRow t : g.tspRows) {
                boolean subMissing = isDash(t.subscriberCountRaw) || t.subscriberCount == null || t.subscriberCount == 0;
                boolean cellPresent = !isDash(t.cellCountRaw) && t.cellCount != null && t.cellCount > 0;
                boolean cellMissing = isDash(t.cellCountRaw);

                // Sub-category 2a: WITH Cell Count
                if (subMissing && cellPresent) {
                    records.add(DiscrepancyRecord.builder()
                            .ingestionBatchId(batch.getId())
                            .detectionTime(batch.getIngestionTime())
                            .alertId(g.identifier)
                            .tsp(t.tsp)
                            .discrepancyType(DiscrepancyType.ZERO_SUBSCRIBER_WITH_CELL_COUNT)
                            .state(g.state)
                            .event(g.event)
                            .alertCreationTime(g.alertCreationTime)
                            .areaDescription(g.areaDescription)
                            .slNo(g.slNo)
                            .cellCount(t.cellCount != null ? t.cellCount.longValue() : null)
                            .subscriberCount(t.subscriberCount)
                            .relevantParameters("Cell Count: " + t.cellCountRaw + ", Subscriber Count: " + t.subscriberCountRaw)
                            .actualValue("Subscriber Count: " + (t.subscriberCount != null ? t.subscriberCount : "0/missing"))
                            .expectedValue("Subscriber Count > 0 when Cell Count present")
                            .reason("TSP counted cells but reported zero/missing subscribers - data inconsistency")
                            .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                            .build());
                }

                // Sub-category 2b: WITHOUT Cell Count (overlaps with Category 1, intentionally)
                if (subMissing && cellMissing) {
                    records.add(DiscrepancyRecord.builder()
                            .ingestionBatchId(batch.getId())
                            .detectionTime(batch.getIngestionTime())
                            .alertId(g.identifier)
                            .tsp(t.tsp)
                            .discrepancyType(DiscrepancyType.ZERO_SUBSCRIBER_WITHOUT_CELL_COUNT)
                            .state(g.state)
                            .event(g.event)
                            .alertCreationTime(g.alertCreationTime)
                            .areaDescription(g.areaDescription)
                            .slNo(g.slNo)
                            .relevantParameters("Cell Count: " + t.cellCountRaw + ", Subscriber Count: " + t.subscriberCountRaw)
                            .actualValue("Both missing")
                            .expectedValue("Cell Count and Subscriber Count")
                            .reason("Both Cell Count and Subscriber Count missing - overlaps with Category 1 (intentional)")
                            .note("This record legitimately belongs to both Category 1 and Category 2b")
                            .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                            .build());
                }
            }
        }

        log.info("Category 2 flagged: {}", records.size());
        return records;
    }

    /**
     * Category 3: SMS Dissemination Feedback Not Received - split into 3 sub-categories
     * Reuses Check2FeedbackNotReceived logic but splits by recency
     */
    private List<DiscrepancyRecord> detectCategory3FeedbackNotReceived(List<AlertGroup> groups, IngestionBatch batch) {
        log.debug("Running Category 3: Feedback Not Received (Statistics Pending/Awaited/Delta)");
        List<DiscrepancyRecord> records = new ArrayList<>();
        LocalDateTime cutoffRecent = batch.getIngestionTime().minusHours(recencyWindowHours);

        for (AlertGroup g : groups) {
            LocalDateTime alertTime = parseAlertTime(g.alertCreationTime);
            boolean isRecent = alertTime != null && alertTime.isAfter(cutoffRecent);

            for (TspRow t : g.tspRows) {
                if (isDash(t.cellCountRaw)) continue; // Category 1, not this one

                boolean subAwaited = isAwaited(t.subscriberCountRaw);
                boolean smsAwaited = isAwaited(t.smsCountRaw);
                boolean preFetch = t.isPreFetch();

                // Sub-category 3a: Statistics Pending (recent, Awaited)
                if ((subAwaited || smsAwaited) && isRecent) {
                    records.add(DiscrepancyRecord.builder()
                            .ingestionBatchId(batch.getId())
                            .detectionTime(batch.getIngestionTime())
                            .alertId(g.identifier)
                            .tsp(t.tsp)
                            .discrepancyType(DiscrepancyType.STATISTICS_PENDING)
                            .state(g.state)
                            .event(g.event)
                            .alertCreationTime(g.alertCreationTime)
                            .areaDescription(g.areaDescription)
                            .slNo(g.slNo)
                            .relevantParameters("Subscriber Count: " + t.subscriberCountRaw + ", SMS Count: " + t.smsCountRaw)
                            .actualValue("Awaited")
                            .expectedValue("Confirmed statistics")
                            .reason("Statistics still pending (alert is recent, within " + recencyWindowHours + "h window)")
                            .note(buildAwaitedNote(subAwaited, smsAwaited))
                            .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                            .build());
                }

                // Sub-category 3b: Statistics Awaited (old, still Awaited - more serious)
                if ((subAwaited || smsAwaited) && !isRecent) {
                    records.add(DiscrepancyRecord.builder()
                            .ingestionBatchId(batch.getId())
                            .detectionTime(batch.getIngestionTime())
                            .alertId(g.identifier)
                            .tsp(t.tsp)
                            .discrepancyType(DiscrepancyType.STATISTICS_AWAITED)
                            .state(g.state)
                            .event(g.event)
                            .alertCreationTime(g.alertCreationTime)
                            .areaDescription(g.areaDescription)
                            .slNo(g.slNo)
                            .relevantParameters("Subscriber Count: " + t.subscriberCountRaw + ", SMS Count: " + t.smsCountRaw)
                            .actualValue("Awaited (overdue)")
                            .expectedValue("Confirmed statistics")
                            .reason("Statistics still awaited beyond " + recencyWindowHours + "h window - overdue response")
                            .note(buildAwaitedNote(subAwaited, smsAwaited) + " [OVERDUE]")
                            .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                            .build());
                }

                // Sub-category 3c: Delta Pending (pre-fetch marker)
                if (preFetch) {
                    records.add(DiscrepancyRecord.builder()
                            .ingestionBatchId(batch.getId())
                            .detectionTime(batch.getIngestionTime())
                            .alertId(g.identifier)
                            .tsp(t.tsp)
                            .discrepancyType(DiscrepancyType.DELTA_PENDING)
                            .state(g.state)
                            .event(g.event)
                            .alertCreationTime(g.alertCreationTime)
                            .areaDescription(g.areaDescription)
                            .slNo(g.slNo)
                            .subscriberCount(t.subscriberCount)
                            .smsCount(t.smsCount)
                            .relevantParameters("Subscriber Count: " + t.subscriberCountRaw + ", SMS Count: " + t.smsCountRaw)
                            .actualValue("Pre-fetch/provisional (**)")
                            .expectedValue("Live confirmed statistics")
                            .reason("Pre-fetch estimate only, live delta confirmation pending")
                            .note("Carries ** marker on " + buildPreFetchMarkerSource(t))
                            .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                            .build());
                }
            }
        }

        log.info("Category 3 flagged: {}", records.size());
        return records;
    }

    /**
     * Category 4: Feedback Delay Greater Than Threshold
     * Reuses Check3FeedbackDelay logic exactly (validated at 600s = 10 minutes)
     */
    private List<DiscrepancyRecord> detectCategory4FeedbackDelay(List<AlertGroup> groups, IngestionBatch batch) {
        log.debug("Running Category 4: Feedback Delay > {} seconds", feedbackDelayThresholdSeconds);
        List<DiscrepancyRecord> records = new ArrayList<>();

        for (AlertGroup g : groups) {
            for (TspRow t : g.tspRows) {
                if (t.feedbackDelaySeconds < 0) continue; // "--", nothing to evaluate

                if (t.feedbackDelaySeconds > feedbackDelayThresholdSeconds) {
                    long excessSeconds = t.feedbackDelaySeconds - feedbackDelayThresholdSeconds;
                    records.add(DiscrepancyRecord.builder()
                            .ingestionBatchId(batch.getId())
                            .detectionTime(batch.getIngestionTime())
                            .alertId(g.identifier)
                            .tsp(t.tsp)
                            .discrepancyType(DiscrepancyType.FEEDBACK_DELAY_EXCEEDS_THRESHOLD)
                            .state(g.state)
                            .event(g.event)
                            .alertCreationTime(g.alertCreationTime)
                            .areaDescription(g.areaDescription)
                            .slNo(g.slNo)
                            .feedbackDelaySeconds(t.feedbackDelaySeconds)
                            .relevantParameters("Feedback Delay: " + t.feedbackDelayRaw)
                            .actualValue(t.feedbackDelayRaw + " (" + t.feedbackDelaySeconds + "s)")
                            .expectedValue("<= " + (feedbackDelayThresholdSeconds / 60) + " minutes")
                            .deviation("+" + (excessSeconds / 60) + " minutes (" + excessSeconds + "s)")
                            .reason("Feedback delay exceeds " + (feedbackDelayThresholdSeconds / 60) + "-minute threshold")
                            .note("NOTE: Problem statement has contradictory thresholds (5min vs 10min) - using 10min per validated baseline. See README NEEDS_SIGN_OFF.")
                            .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                            .build());
                }
            }
        }

        log.info("Category 4 flagged: {}", records.size());
        return records;
    }

    /**
     * Category 5: Pre-Fetch Dissemination Duration Not Following DoT Matrix
     * Reuses Check4PreFetch logic exactly (validated: 147 flagged)
     */
    private List<DiscrepancyRecord> detectCategory5PrefetchDuration(List<AlertGroup> groups, IngestionBatch batch) {
        log.debug("Running Category 5: Pre-fetch Duration vs MRAD Matrix");
        List<DiscrepancyRecord> records = new ArrayList<>();

        for (AlertGroup g : groups) {
            for (TspRow t : g.tspRows) {
                if (!t.isPreFetch()) continue;
                if (t.cellCount == null || t.disseminationSeconds < 0) continue;

                long thresholdSec = MradMatrix.thresholdSeconds(t.cellCount);
                if (t.disseminationSeconds > thresholdSec) {
                    long excessSec = t.disseminationSeconds - thresholdSec;
                    records.add(DiscrepancyRecord.builder()
                            .ingestionBatchId(batch.getId())
                            .detectionTime(batch.getIngestionTime())
                            .alertId(g.identifier)
                            .tsp(t.tsp)
                            .discrepancyType(DiscrepancyType.PREFETCH_DURATION_MATRIX_BREACH)
                            .state(g.state)
                            .event(g.event)
                            .alertCreationTime(g.alertCreationTime)
                            .areaDescription(g.areaDescription)
                            .slNo(g.slNo)
                            .cellCount(t.cellCount.longValue())
                            .disseminationDurationSeconds(t.disseminationSeconds)
                            .relevantParameters("Cell Count: " + t.cellCount + " (" + MradMatrix.bucketLabel(t.cellCount) + 
                                    "), Dissemination Duration: " + t.disseminationDurationRaw + ", Pre-fetch: " + buildPreFetchMarkerSource(t))
                            .actualValue(t.disseminationDurationRaw + " (" + t.disseminationSeconds + "s)")
                            .expectedValue("<= " + MradMatrix.thresholdMinutes(t.cellCount) + " minutes per MRAD matrix")
                            .deviation("+" + (excessSec / 60) + " minutes (" + excessSec + "s)")
                            .reason("Pre-fetch dissemination duration exceeds MRAD threshold for " + MradMatrix.bucketLabel(t.cellCount) + " cell bucket")
                            .note("PRE-FETCH: provisional data only, live confirmation not yet received" +
                                    (MradMatrix.isOutOfDefinedRange(t.cellCount) ? "; Cell Count > 30,000 (outside defined matrix)" : ""))
                            .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                            .build());
                }
            }
        }

        log.info("Category 5 flagged: {} (expected 147 from regression baseline)", records.size());
        return records;
    }

    /**
     * Category 6: Total Dissemination Duration Too High as per Cell Count
     * Reuses Check5Dissemination logic exactly (validated: 571 flagged)
     */
    private List<DiscrepancyRecord> detectCategory6TotalDuration(List<AlertGroup> groups, IngestionBatch batch) {
        log.debug("Running Category 6: Total Duration vs MRAD Matrix");
        List<DiscrepancyRecord> records = new ArrayList<>();

        for (AlertGroup g : groups) {
            for (TspRow t : g.tspRows) {
                if (t.cellCount == null || t.disseminationSeconds < 0) continue;

                long thresholdSec = MradMatrix.thresholdSeconds(t.cellCount);
                if (t.disseminationSeconds > thresholdSec) {
                    long excessSec = t.disseminationSeconds - thresholdSec;
                    records.add(DiscrepancyRecord.builder()
                            .ingestionBatchId(batch.getId())
                            .detectionTime(batch.getIngestionTime())
                            .alertId(g.identifier)
                            .tsp(t.tsp)
                            .discrepancyType(DiscrepancyType.TOTAL_DURATION_MATRIX_BREACH)
                            .state(g.state)
                            .event(g.event)
                            .alertCreationTime(g.alertCreationTime)
                            .areaDescription(g.areaDescription)
                            .slNo(g.slNo)
                            .cellCount(t.cellCount.longValue())
                            .disseminationDurationSeconds(t.disseminationSeconds)
                            .relevantParameters("Cell Count: " + t.cellCount + " (" + MradMatrix.bucketLabel(t.cellCount) + 
                                    "), Dissemination Duration: " + t.disseminationDurationRaw)
                            .actualValue(t.disseminationDurationRaw + " (" + t.disseminationSeconds + "s)")
                            .expectedValue("<= " + MradMatrix.thresholdMinutes(t.cellCount) + " minutes per MRAD matrix")
                            .deviation("+" + (excessSec / 60) + " minutes (" + excessSec + "s)")
                            .reason("Total dissemination duration exceeds MRAD threshold for " + MradMatrix.bucketLabel(t.cellCount) + " cell bucket")
                            .note(MradMatrix.isOutOfDefinedRange(t.cellCount) ? 
                                    "Cell Count > 30,000 - outside defined MRAD matrix, verify threshold manually" : null)
                            .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                            .build());
                }
            }
        }

        log.info("Category 6 flagged: {} (expected 571 from regression baseline)", records.size());
        return records;
    }

    /**
     * Category 7: Inordinate Subscriber Count Ratio Compared with Other TSPs
     * Reuses Check6SubscriberRatio logic exactly (validated: 479 flagged)
     */
    private List<DiscrepancyRecord> detectCategory7SubscriberRatio(List<AlertGroup> groups, 
            Map<String, Map<String, Double>> traiBaseline, IngestionBatch batch) {
        log.debug("Running Category 7: Subscriber Ratio Deviation");
        List<DiscrepancyRecord> records = new ArrayList<>();

        for (AlertGroup g : groups) {
            TraiBaselineReader.CircleMapping mapping = TraiBaselineReader.mapStateToCircle(g.state);

            // Handle unmapped states (AMBIGUOUS/NO_BASELINE)
            if (mapping.status == TraiBaselineReader.MappingStatus.NO_BASELINE || 
                    mapping.status == TraiBaselineReader.MappingStatus.AMBIGUOUS) {
                records.add(DiscrepancyRecord.builder()
                        .ingestionBatchId(batch.getId())
                        .detectionTime(batch.getIngestionTime())
                        .alertId(g.identifier)
                        .tsp("-")
                        .discrepancyType(DiscrepancyType.INORDINATE_SUBSCRIBER_RATIO)
                        .state(g.state)
                        .event(g.event)
                        .alertCreationTime(g.alertCreationTime)
                        .areaDescription(g.areaDescription)
                        .slNo(g.slNo)
                        .relevantParameters("State: " + g.state + ", Mapping Status: " + mapping.status)
                        .actualValue("N/A")
                        .expectedValue("N/A")
                        .reason(mapping.status == TraiBaselineReader.MappingStatus.AMBIGUOUS ?
                                "AMBIGUOUS: Uttar Pradesh requires manual U.P.(E)/U.P.(W) decision - ratio not computed" :
                                "NO_BASELINE: No matching TRAI circle for this state - ratio not computed")
                        .note("See README NEEDS_SIGN_OFF section for state→circle mapping resolution")
                        .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                        .build());
                continue;
            }

            if (g.totalSubscriberCount == null || g.totalSubscriberCount <= 0) continue;

            Map<String, Double> circlePct = traiBaseline.get(mapping.circleName);
            if (circlePct == null) continue;

            for (TspRow t : g.tspRows) {
                if (t.subscriberCount == null) continue;

                double reportPct = (t.subscriberCount * 100.0) / g.totalSubscriberCount;
                Double traiPct = circlePct.get(t.tsp);

                if (traiPct != null) {
                    double deviation = reportPct - traiPct;
                    if (Math.abs(deviation) > subscriberRatioDeviationPct) {
                        records.add(DiscrepancyRecord.builder()
                                .ingestionBatchId(batch.getId())
                                .detectionTime(batch.getIngestionTime())
                                .alertId(g.identifier)
                                .tsp(t.tsp)
                                .discrepancyType(DiscrepancyType.INORDINATE_SUBSCRIBER_RATIO)
                                .state(g.state)
                                .event(g.event)
                                .alertCreationTime(g.alertCreationTime)
                                .areaDescription(g.areaDescription)
                                .slNo(g.slNo)
                                .subscriberCount(t.subscriberCount)
                                .relevantParameters("TSP Subscriber Count: " + t.subscriberCount + 
                                        ", Alert Total: " + g.totalSubscriberCount + 
                                        ", TRAI Circle: " + mapping.circleName)
                                .actualValue(String.format("%.2f%%", reportPct))
                                .expectedValue(String.format("~%.2f%% (TRAI baseline)", traiPct))
                                .deviation(String.format("%+.2f percentage points", deviation))
                                .reason("Subscriber ratio deviates from TRAI market share by more than " + subscriberRatioDeviationPct + " percentage points")
                                .note((t.subscriberPreFetch ? "Subscriber Count is pre-fetch/provisional (**); " : "") +
                                        "Threshold (" + subscriberRatioDeviationPct + "%) is placeholder pending DoT confirmation. See README NEEDS_SIGN_OFF.")
                                .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                                .build());
                    }
                }
            }
        }

        log.info("Category 7 flagged: {} (expected 479 from regression baseline)", records.size());
        return records;
    }

    /**
     * Category 8: Dissemination Completed but Pre-Fetch Shows Zero Subscriber Count (NEW)
     * Best-effort definition given available data sources
     */
    private List<DiscrepancyRecord> detectCategory8DisseminationCompletedZeroPrefetch(List<AlertGroup> groups, IngestionBatch batch) {
        log.debug("Running Category 8: Dissemination Completed but Zero Pre-fetch");
        List<DiscrepancyRecord> records = new ArrayList<>();

        for (AlertGroup g : groups) {
            if (g.totalSubscriberCount == null || g.totalSubscriberCount <= 0) continue;

            for (TspRow t : g.tspRows) {
                boolean hasPreFetchMarker = t.subscriberPreFetch;
                boolean subscriberZeroOrMissing = t.subscriberCount == null || t.subscriberCount == 0;

                if (hasPreFetchMarker && subscriberZeroOrMissing) {
                    records.add(DiscrepancyRecord.builder()
                            .ingestionBatchId(batch.getId())
                            .detectionTime(batch.getIngestionTime())
                            .alertId(g.identifier)
                            .tsp(t.tsp)
                            .discrepancyType(DiscrepancyType.DISSEMINATION_COMPLETED_ZERO_PREFETCH)
                            .state(g.state)
                            .event(g.event)
                            .alertCreationTime(g.alertCreationTime)
                            .areaDescription(g.areaDescription)
                            .slNo(g.slNo)
                            .subscriberCount(t.subscriberCount)
                            .relevantParameters("Alert Total Subscriber Count: " + g.totalSubscriberCount + 
                                    ", TSP Subscriber Count: " + t.subscriberCountRaw)
                            .actualValue("TSP pre-fetch Subscriber Count: " + (t.subscriberCount != null ? t.subscriberCount : "0/missing"))
                            .expectedValue("Subscriber Count > 0 when alert total > 0")
                            .reason("Alert shows completed dissemination (total > 0) but this TSP's pre-fetch value is zero/missing")
                            .note("INFERENCE ONLY - input files don't distinguish pre-fetch snapshot from final value. " +
                                    "If CAP Sachet system exposes actual pre-fetch history, rebuild against that. See README NEEDS_SIGN_OFF.")
                            .status(DiscrepancyRecord.DiscrepancyStatus.OPEN)
                            .build());
                }
            }
        }

        log.info("Category 8 flagged: {} (NEW category, inferred definition)", records.size());
        return records;
    }

    /**
     * Category 9: Alerts Disseminated After Expiry Time (NEW, BLOCKED)
     * Requires Alert Expiry Time which doesn't exist in current input files
     */
    private List<DiscrepancyRecord> detectCategory9AfterExpiry(List<AlertGroup> groups, IngestionBatch batch) {
        log.warn("Category 9 (After Expiry Time) is BLOCKED - Alert Expiry Time data source not available");
        // Return empty list - schema/UI ready but detection can't run until data source provided
        // DO NOT fabricate or infer expiry times
        return new ArrayList<>();
    }

    // Helper methods
    private boolean isDash(String raw) {
        return raw != null && raw.trim().equals("--");
    }

    private boolean isAwaited(String raw) {
        return raw != null && raw.trim().equalsIgnoreCase("Awaited");
    }

    private String buildAwaitedNote(boolean subAwaited, boolean smsAwaited) {
        if (subAwaited && smsAwaited) return "Awaited: Subscriber Count + SMS Count";
        if (subAwaited) return "Awaited: Subscriber Count";
        return "Awaited: SMS Count";
    }

    private String buildPreFetchMarkerSource(TspRow t) {
        if (t.subscriberPreFetch && t.smsPreFetch) return "Subscriber Count + SMS Count";
        if (t.subscriberPreFetch) return "Subscriber Count";
        return "SMS Count";
    }

    private LocalDateTime parseAlertTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            // Try common formats
            DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            };
            for (DateTimeFormatter fmt : formatters) {
                try {
                    return LocalDateTime.parse(raw.trim(), fmt);
                } catch (DateTimeParseException ignored) {}
            }
        } catch (Exception e) {
            log.debug("Could not parse alert time: {}", raw);
        }
        return null;
    }

    private String extractFilename(String path) {
        if (path == null) return "";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Log regression check against validated baseline (Section G)
     */
    private void logRegressionCheck(IngestionBatch batch) {
        log.info("=== REGRESSION BASELINE CHECK (Section G) ===");
        log.info("Category 5 (Pre-fetch Duration):  {} flagged (expected: 147)", batch.getCountPrefetchDurationBreach());
        log.info("Category 6 (Total Duration):       {} flagged (expected: 571)", batch.getCountTotalDurationBreach());
        log.info("Category 7 (Subscriber Ratio):     {} flagged (expected: 479)", batch.getCountInordinateRatio());
        log.info("==============================================");
    }
}
