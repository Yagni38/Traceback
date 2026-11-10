package com.traceback.traceback.service;

import com.traceback.traceback.dto.ClaimRequest;
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

/** Business rules for ownership claims on FOUND items. */
@Service
public class ClaimService {

    private final ItemClaimRepository claimRepository;
    private final ItemReportRepository reportRepository;

    public ClaimService(ItemClaimRepository claimRepository,
                        ItemReportRepository reportRepository) {
        this.claimRepository = claimRepository;
        this.reportRepository = reportRepository;
    }

    @Transactional
    public ItemClaim submit(ClaimRequest request) {
        ItemReport report = reportRepository.findById(request.reportId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Report not found: " + request.reportId()));

        if (!"FOUND".equalsIgnoreCase(report.getReportType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only FOUND items can be claimed");
        }

        if (!"ACTIVE".equalsIgnoreCase(report.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This item has already been returned");
        }

        String email = request.claimantEmail().trim();

        // Same person cannot have two claims waiting on the same item
        if (claimRepository.existsByReportIdAndClaimantEmailIgnoreCaseAndStatus(
                report.getId(), email, "PENDING")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "You already have a pending claim for this item");
        }

        ItemClaim claim = new ItemClaim();
        claim.setReportId(report.getId());
        claim.setClaimantName(request.claimantName().trim());
        claim.setClaimantEmail(email);
        claim.setClaimantPhone(ReportService.trimToNull(request.claimantPhone()));
        claim.setClaimReason(request.claimReason().trim());
        claim.setStatus("PENDING");

        return claimRepository.save(claim);
    }

    /** Newest claims first. */
    @Transactional(readOnly = true)
    public List<ItemClaim> findAll() {
        return claimRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    /**
     * Admin approves or rejects a claim. All steps happen in one transaction:
     * if anything fails, nothing is saved. Approving marks the item RETURNED
     * and rejects every other pending claim for the same item.
     */
    @Transactional
    public ItemClaim review(Long claimId, String status) {
        String newStatus = status.trim().toUpperCase();

        if (!newStatus.equals("APPROVED") && !newStatus.equals("REJECTED")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Status must be APPROVED or REJECTED");
        }

        ItemClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Claim not found: " + claimId));

        if (!"PENDING".equalsIgnoreCase(claim.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This claim has already been reviewed");
        }

        if (newStatus.equals("APPROVED")) {
            ItemReport report = reportRepository.findById(claim.getReportId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Report not found: " + claim.getReportId()));

            if (!"ACTIVE".equalsIgnoreCase(report.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "This item is no longer active");
            }

            report.setStatus("RETURNED");
            reportRepository.save(report);

            for (ItemClaim other : claimRepository
                    .findByReportIdAndStatus(report.getId(), "PENDING")) {
                if (!other.getId().equals(claim.getId())) {
                    other.setStatus("REJECTED");
                    claimRepository.save(other);
                }
            }
        }

        claim.setStatus(newStatus);
        return claimRepository.save(claim);
    }
}
