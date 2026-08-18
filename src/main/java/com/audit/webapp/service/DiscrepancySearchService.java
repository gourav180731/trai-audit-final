package com.audit.webapp.service;

import com.audit.webapp.entity.DiscrepancyRecord;
import com.audit.webapp.entity.DiscrepancyRecord.DiscrepancyStatus;
import com.audit.webapp.entity.DiscrepancyRecord.DiscrepancyType;
import com.audit.webapp.repository.DiscrepancyRecordRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for searching and filtering discrepancy records (Problem Statement Section 6)
 */
@Service
@RequiredArgsConstructor
public class DiscrepancySearchService {

    private final DiscrepancyRecordRepository repository;

    public DiscrepancyRecord findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Discrepancy record not found: " + id));
    }

    public List<DiscrepancyRecord> findByAlertId(String alertId) {
        return repository.findByAlertId(alertId);
    }

    public List<DiscrepancyRecord> findByTypeAndBatch(DiscrepancyType type, Long batchId) {
        Specification<DiscrepancyRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("discrepancyType"), type));
            if (batchId != null) {
                predicates.add(cb.equal(root.get("ingestionBatchId"), batchId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec);
    }

    public List<DiscrepancyRecord> search(DiscrepancySearchCriteria criteria) {
        Specification<DiscrepancyRecord> spec = buildSpecification(criteria);
        return repository.findAll(spec);
    }

    private Specification<DiscrepancyRecord> buildSpecification(DiscrepancySearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getAlertId() != null && !criteria.getAlertId().trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("alertId")), 
                        "%" + criteria.getAlertId().toLowerCase() + "%"));
            }

            if (criteria.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("detectionTime"), criteria.getStartDate()));
            }

            if (criteria.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("detectionTime"), criteria.getEndDate()));
            }

            if (criteria.getTsp() != null && !criteria.getTsp().trim().isEmpty()) {
                predicates.add(cb.equal(root.get("tsp"), criteria.getTsp()));
            }

            if (criteria.getDiscrepancyType() != null) {
                predicates.add(cb.equal(root.get("discrepancyType"), criteria.getDiscrepancyType()));
            }

            if (criteria.getState() != null && !criteria.getState().trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("state")), 
                        "%" + criteria.getState().toLowerCase() + "%"));
            }

            if (criteria.getDistrict() != null && !criteria.getDistrict().trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("district")), 
                        "%" + criteria.getDistrict().toLowerCase() + "%"));
            }

            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }

            if (criteria.getMinCellCount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("cellCount"), criteria.getMinCellCount()));
            }

            if (criteria.getMaxCellCount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("cellCount"), criteria.getMaxCellCount()));
            }

            if (criteria.getMinSubscriberCount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("subscriberCount"), criteria.getMinSubscriberCount()));
            }

            if (criteria.getMaxSubscriberCount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("subscriberCount"), criteria.getMaxSubscriberCount()));
            }

            if (criteria.getBatchId() != null) {
                predicates.add(cb.equal(root.get("ingestionBatchId"), criteria.getBatchId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Criteria object for combined filtering (Problem Statement Section 6)
     */
    public static class DiscrepancySearchCriteria {
        private String alertId;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String tsp;
        private DiscrepancyType discrepancyType;
        private String state;
        private String district;
        private String alertAuthorizingAgency;
        private DiscrepancyStatus status;
        private Long minCellCount;
        private Long maxCellCount;
        private Long minSubscriberCount;
        private Long maxSubscriberCount;
        private Long batchId;

        // Getters and setters
        public String getAlertId() { return alertId; }
        public void setAlertId(String alertId) { this.alertId = alertId; }

        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }

        public String getTsp() { return tsp; }
        public void setTsp(String tsp) { this.tsp = tsp; }

        public DiscrepancyType getDiscrepancyType() { return discrepancyType; }
        public void setDiscrepancyType(DiscrepancyType discrepancyType) { this.discrepancyType = discrepancyType; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }

        public String getAlertAuthorizingAgency() { return alertAuthorizingAgency; }
        public void setAlertAuthorizingAgency(String alertAuthorizingAgency) { 
            this.alertAuthorizingAgency = alertAuthorizingAgency; 
        }

        public DiscrepancyStatus getStatus() { return status; }
        public void setStatus(DiscrepancyStatus status) { this.status = status; }

        public Long getMinCellCount() { return minCellCount; }
        public void setMinCellCount(Long minCellCount) { this.minCellCount = minCellCount; }

        public Long getMaxCellCount() { return maxCellCount; }
        public void setMaxCellCount(Long maxCellCount) { this.maxCellCount = maxCellCount; }

        public Long getMinSubscriberCount() { return minSubscriberCount; }
        public void setMinSubscriberCount(Long minSubscriberCount) { this.minSubscriberCount = minSubscriberCount; }

        public Long getMaxSubscriberCount() { return maxSubscriberCount; }
        public void setMaxSubscriberCount(Long maxSubscriberCount) { this.maxSubscriberCount = maxSubscriberCount; }

        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
    }
}
