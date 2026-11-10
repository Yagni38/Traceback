
package com.traceback.traceback.controller;

import com.traceback.traceback.dto.ClaimRequest;
import com.traceback.traceback.dto.StatusUpdateRequest;
import com.traceback.traceback.entity.ItemClaim;
import com.traceback.traceback.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ItemClaimController {

    private final ClaimService claimService;

    public ItemClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    // Public: submit a claim for an active FOUND item
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemClaim submitClaim(@Valid @RequestBody ClaimRequest request) {
        return claimService.submit(request);
    }

    // Admin only: view all claims
    @GetMapping
    public List<ItemClaim> getAllClaims() {
        return claimService.findAll();
    }

    // Admin only: approve or reject a claim
    @PatchMapping("/{id}/status")
    public ItemClaim updateClaimStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {

        return claimService.review(id, request.status());
    }
}
