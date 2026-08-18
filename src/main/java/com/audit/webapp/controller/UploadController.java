package com.audit.webapp.controller;

import com.audit.webapp.entity.IngestionBatch;
import com.audit.webapp.service.DiscrepancyDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Controller for file upload and ingestion triggering
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final DiscrepancyDetectionService detectionService;

    @GetMapping("/upload")
    public String uploadPage(Model model) {
        return "upload";
    }

    @PostMapping("/upload")
    public String uploadFiles(
            @RequestParam("warningReport") MultipartFile warningReport,
            @RequestParam("traiBaseline") MultipartFile traiBaseline,
            RedirectAttributes redirectAttributes) {

        // Validate files
        if (warningReport.isEmpty() || traiBaseline.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Both files are required");
            return "redirect:/upload";
        }

        if (!warningReport.getOriginalFilename().endsWith(".xlsx") || 
            !traiBaseline.getOriginalFilename().endsWith(".xlsx")) {
            redirectAttributes.addFlashAttribute("error", "Both files must be .xlsx format");
            return "redirect:/upload";
        }

        try {
            // Create temp directory if not exists
            Path tempDir = Paths.get("./temp");
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            // Save uploaded files temporarily
            String warningPath = saveTemporaryFile(warningReport, tempDir);
            String traiPath = saveTemporaryFile(traiBaseline, tempDir);

            log.info("Processing uploaded files: {}, {}", warningPath, traiPath);

            // Process files through detection engine
            IngestionBatch batch = detectionService.processFiles(warningPath, traiPath);

            // Clean up temp files
            Files.deleteIfExists(Paths.get(warningPath));
            Files.deleteIfExists(Paths.get(traiPath));

            redirectAttributes.addFlashAttribute("success",
                    String.format("Successfully processed %d alerts with %d discrepancy instances across %d categories",
                            batch.getTotalAlertsProcessed(),
                            batch.getTotalDiscrepancyInstances(),
                            countNonZeroCategories(batch)));
            redirectAttributes.addFlashAttribute("batchId", batch.getId());

            return "redirect:/";

        } catch (Exception e) {
            log.error("File upload and processing failed", e);
            redirectAttributes.addFlashAttribute("error",
                    "Processing failed: " + e.getMessage());
            return "redirect:/upload";
        }
    }

    private String saveTemporaryFile(MultipartFile file, Path tempDir) throws IOException {
        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path targetPath = tempDir.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toAbsolutePath().toString();
    }

    private int countNonZeroCategories(IngestionBatch batch) {
        int count = 0;
        if (batch.getCountCompleteFailure() != null && batch.getCountCompleteFailure() > 0) count++;
        if (batch.getCountZeroSubscriberWithCellCount() != null && batch.getCountZeroSubscriberWithCellCount() > 0) count++;
        if (batch.getCountZeroSubscriberWithoutCellCount() != null && batch.getCountZeroSubscriberWithoutCellCount() > 0) count++;
        if (batch.getCountStatisticsPending() != null && batch.getCountStatisticsPending() > 0) count++;
        if (batch.getCountStatisticsAwaited() != null && batch.getCountStatisticsAwaited() > 0) count++;
        if (batch.getCountDeltaPending() != null && batch.getCountDeltaPending() > 0) count++;
        if (batch.getCountFeedbackDelayExceeds() != null && batch.getCountFeedbackDelayExceeds() > 0) count++;
        if (batch.getCountPrefetchDurationBreach() != null && batch.getCountPrefetchDurationBreach() > 0) count++;
        if (batch.getCountTotalDurationBreach() != null && batch.getCountTotalDurationBreach() > 0) count++;
        if (batch.getCountInordinateRatio() != null && batch.getCountInordinateRatio() > 0) count++;
        if (batch.getCountDisseminationCompletedZeroPrefetch() != null && batch.getCountDisseminationCompletedZeroPrefetch() > 0) count++;
        if (batch.getCountDisseminatedAfterExpiry() != null && batch.getCountDisseminatedAfterExpiry() > 0) count++;
        return count;
    }
}
