package com.traceback.traceback.service;

import com.traceback.traceback.dto.ReportRequest;
import com.traceback.traceback.entity.ItemClaim;
import com.traceback.traceback.entity.ItemReport;
import com.traceback.traceback.repository.ItemClaimRepository;
import com.traceback.traceback.repository.ItemReportRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/** Business rules for lost/found reports. */
@Service
public class ReportService {

    public static final Set<String> REPORT_STATUSES = Set.of("ACTIVE", "RETURNED");

    private final ItemReportRepository reportRepository;
    private final ItemClaimRepository claimRepository;

    public ReportService(ItemReportRepository reportRepository,
                         ItemClaimRepository claimRepository) {
        this.reportRepository = reportRepository;
        this.claimRepository = claimRepository;
    }

    @Transactional
    public ItemReport create(ReportRequest request) {
        ItemReport report = new ItemReport();
        report.setReportType(request.reportType().trim().toUpperCase());
        report.setTitle(request.title().trim());
        report.setCategory(request.category().trim());
        report.setColor(trimToNull(request.color()));
        report.setDescription(trimToNull(request.description()));
        report.setLocation(request.location().trim());
        report.setEventDate(request.eventDate());
        report.setReporterName(request.reporterName().trim());
        report.setReporterEmail(request.reporterEmail().trim());
        report.setReporterPhone(trimToNull(request.reporterPhone()));
        report.setStatus("ACTIVE");
        return reportRepository.save(report);
    }

    /** Newest reports first. */
    @Transactional(readOnly = true)
    public List<ItemReport> findAll() {
        return reportRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Transactional(readOnly = true)
    public ItemReport findById(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Report not found: " + id));
    }

    /**
     * Admin changes an item's status. When an item is marked RETURNED,
     * any claims still waiting on it are rejected so nothing is left pending.
     */
    @Transactional
    public ItemReport updateStatus(Long id, String status) {
        String newStatus = status.trim().toUpperCase();

        if (!REPORT_STATUSES.contains(newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Status must be ACTIVE or RETURNED");
        }

        ItemReport report = findById(id);
        report.setStatus(newStatus);

        if ("RETURNED".equals(newStatus)) {
            for (ItemClaim claim : claimRepository.findByReportIdAndStatus(id, "PENDING")) {
                claim.setStatus("REJECTED");
                claimRepository.save(claim);
            }
        }

        return reportRepository.save(report);
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
