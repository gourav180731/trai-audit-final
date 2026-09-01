package com.audit.webapp.controller;

import com.audit.webapp.entity.DiscrepancyRecord;
import com.audit.webapp.service.DiscrepancySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for search and filtering functionality
 */
@Controller
@RequiredArgsConstructor
public class SearchController {

    private final DiscrepancySearchService searchService;

    @GetMapping("/search")
    public String searchPage(Model model) {
        // Add enums for dropdown options
        model.addAttribute("discrepancyTypes", DiscrepancyRecord.DiscrepancyType.values());
        model.addAttribute("statuses", DiscrepancyRecord.DiscrepancyStatus.values());
        model.addAttribute("tsps", new String[]{"Airtel", "BSNL", "MTNL", "Reliance Jio", "Vodafone Idea"});
        model.addAttribute("searchPerformed", false);
        model.addAttribute("results", List.of());
        model.addAttribute("resultCount", 0);
        return "search";
    }

    @PostMapping("/search")
    public String search(
            @RequestParam(required = false) String alertId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String tsp,
            @RequestParam(required = false) DiscrepancyRecord.DiscrepancyType discrepancyType,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) DiscrepancyRecord.DiscrepancyStatus status,
            @RequestParam(required = false) Long minCellCount,
            @RequestParam(required = false) Long maxCellCount,
            @RequestParam(required = false) Long minSubscriberCount,
            @RequestParam(required = false) Long maxSubscriberCount,
            Model model) {

        DiscrepancySearchService.DiscrepancySearchCriteria criteria = new DiscrepancySearchService.DiscrepancySearchCriteria();
        criteria.setAlertId(alertId);
        criteria.setTsp(tsp);
        criteria.setDiscrepancyType(discrepancyType);
        criteria.setState(state);
        criteria.setStatus(status);
        criteria.setMinCellCount(minCellCount);
        criteria.setMaxCellCount(maxCellCount);
        criteria.setMinSubscriberCount(minSubscriberCount);
        criteria.setMaxSubscriberCount(maxSubscriberCount);

        // Parse dates
        if (startDate != null && !startDate.trim().isEmpty()) {
            criteria.setStartDate(LocalDateTime.parse(startDate + "T00:00:00"));
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            criteria.setEndDate(LocalDateTime.parse(endDate + "T23:59:59"));
        }

        List<DiscrepancyRecord> results = searchService.search(criteria);

        model.addAttribute("results", results);
        model.addAttribute("resultCount", results.size());
        model.addAttribute("searchPerformed", true);
        
        // Keep search form populated
        model.addAttribute("criteria", criteria);
        model.addAttribute("discrepancyTypes", DiscrepancyRecord.DiscrepancyType.values());
        model.addAttribute("statuses", DiscrepancyRecord.DiscrepancyStatus.values());
        model.addAttribute("tsps", new String[]{"Airtel", "BSNL", "MTNL", "Reliance Jio", "Vodafone Idea"});

        return "search";
    }
}
