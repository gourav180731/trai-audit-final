package com.audit.webapp.controller;

import com.audit.webapp.entity.DiscrepancyRecord;
import com.audit.webapp.entity.IngestionBatch;
import com.audit.webapp.service.DashboardService;
import com.audit.webapp.service.DiscrepancySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Main dashboard controller implementing problem statement Section 4 requirements
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final DiscrepancySearchService searchService;

    @GetMapping("/")
    public String dashboard(Model model) {
        try {
            // Get latest batch for dashboard display
            IngestionBatch latestBatch = dashboardService.getLatestCompletedBatch();
            
            if (latestBatch != null) {
                model.addAttribute("batch", latestBatch);
                model.addAttribute("summary", dashboardService.getDashboardSummary(latestBatch.getId()));
                model.addAttribute("tspWiseCount", dashboardService.getTspWiseCount(latestBatch.getId()));
                model.addAttribute("categoryWiseCount", dashboardService.getCategoryWiseCount(latestBatch.getId()));
                model.addAttribute("dateWiseTrend", dashboardService.getDateWiseTrend(30)); // Last 30 days
            } else {
                // No batches yet - set null to show "no data" message
                model.addAttribute("batch", null);
            }
            
            model.addAttribute("recentBatches", dashboardService.getRecentBatches(10));
        } catch (Exception e) {
            // Handle any errors gracefully
            model.addAttribute("batch", null);
            model.addAttribute("error", "Error loading dashboard: " + e.getMessage());
        }
        return "dashboard";
    }

    @GetMapping("/category/{type}")
    public String categoryDetails(@PathVariable DiscrepancyRecord.DiscrepancyType type,
                                   @RequestParam(required = false) Long batchId,
                                   Model model) {
        if (batchId == null) {
            IngestionBatch latest = dashboardService.getLatestCompletedBatch();
            if (latest != null) batchId = latest.getId();
        }
        
        List<DiscrepancyRecord> records = searchService.findByTypeAndBatch(type, batchId);
        model.addAttribute("discrepancyType", type);
        model.addAttribute("records", records);
        model.addAttribute("batchId", batchId);
        return "category-detail";
    }

    @GetMapping("/alert/{alertId}")
    public String alertDetails(@PathVariable String alertId, Model model) {
        List<DiscrepancyRecord> records = searchService.findByAlertId(alertId);
        model.addAttribute("alertId", alertId);
        model.addAttribute("records", records);
        
        // Group by TSP for drill-down
        Map<String, List<DiscrepancyRecord>> byTsp = dashboardService.groupByTsp(records);
        model.addAttribute("recordsByTsp", byTsp);
        return "alert-detail";
    }

    @GetMapping("/discrepancy/{id}")
    public String discrepancyDetail(@PathVariable Long id, Model model) {
        DiscrepancyRecord record = searchService.findById(id);
        model.addAttribute("record", record);
        return "discrepancy-detail";
    }
}
