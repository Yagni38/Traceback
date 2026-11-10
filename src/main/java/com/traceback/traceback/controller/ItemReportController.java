
package com.traceback.traceback.controller;

import com.traceback.traceback.config.SecurityConfig;
import com.traceback.traceback.dto.MatchResult;
import com.traceback.traceback.dto.ReportRequest;
import com.traceback.traceback.dto.ReportResponse;
import com.traceback.traceback.dto.StatusUpdateRequest;
import com.traceback.traceback.service.ItemMatchingService;
import com.traceback.traceback.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ItemReportController {

    private final ReportService reportService;
    private final ItemMatchingService matchingService;

    public ItemReportController(
            ReportService reportService,
            ItemMatchingService matchingService) {

        this.reportService = reportService;
        this.matchingService = matchingService;
    }

    // Public: report a lost or found item. The reporter gets their own details back.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse createReport(@Valid @RequestBody ReportRequest request) {
        return ReportResponse.from(reportService.create(request), true);
    }

    // Public: contact details are masked unless an admin is logged in
    @GetMapping
    public List<ReportResponse> getAllReports(HttpServletRequest http) {
        boolean admin = isAdmin(http);
        return reportService.findAll().stream()
                .map(report -> ReportResponse.from(report, admin))
                .toList();
    }

    @GetMapping("/{id}")
    public ReportResponse getReport(@PathVariable Long id, HttpServletRequest http) {
        return ReportResponse.from(reportService.findById(id), isAdmin(http));
    }

    // Smart matching API
    @GetMapping("/{id}/matches")
    public List<MatchResult> getMatches(@PathVariable Long id, HttpServletRequest http) {
        return matchingService.findMatches(id, isAdmin(http));
    }

    // Admin only (see SecurityConfig)
    @PatchMapping("/{id}/status")
    public ReportResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {

        return ReportResponse.from(
                reportService.updateStatus(id, request.status()), true);
    }

    private boolean isAdmin(HttpServletRequest http) {
        return http.isUserInRole(SecurityConfig.ADMIN);
    }
}
