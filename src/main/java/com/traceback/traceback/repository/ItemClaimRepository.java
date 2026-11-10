
package com.traceback.traceback.repository;

import com.traceback.traceback.entity.ItemClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemClaimRepository
        extends JpaRepository<ItemClaim, Long> {

    List<ItemClaim> findByReportIdAndStatus(Long reportId, String status);

    boolean existsByReportIdAndClaimantEmailIgnoreCaseAndStatus(
            Long reportId, String claimantEmail, String status);
}
