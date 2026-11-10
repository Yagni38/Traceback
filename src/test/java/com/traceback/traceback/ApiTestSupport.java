package com.traceback.traceback;

import com.jayway.jsonpath.JsonPath;
import com.traceback.traceback.repository.ItemClaimRepository;
import com.traceback.traceback.repository.ItemReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for API tests: a MockMvc client with Spring Security
 * switched on, an empty database before each test, and small helpers.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    protected static final String ADMIN_USER = "testadmin";
    protected static final String ADMIN_PASSWORD = "test-only-password";

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected ItemReportRepository reportRepository;

    @Autowired
    protected ItemClaimRepository claimRepository;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvcAndCleanDatabase() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        claimRepository.deleteAll();
        reportRepository.deleteAll();
    }

    protected RequestPostProcessor admin() {
        return httpBasic(ADMIN_USER, ADMIN_PASSWORD);
    }

    protected static String reportJson(String type, String title, String email) {
        return """
            {
              "reportType": "%s",
              "title": "%s",
              "category": "Wallet",
              "color": "Black",
              "location": "Central Library",
              "description": "Black leather wallet with college ID card",
              "eventDate": "2026-11-01",
              "reporterName": "Ravi Kumar",
              "reporterEmail": "%s",
              "reporterPhone": "9876543210"
            }
            """.formatted(type, title, email);
    }

    protected static String claimJson(long reportId, String email) {
        return """
            {
              "reportId": %d,
              "claimantName": "Asha Rao",
              "claimantEmail": "%s",
              "claimantPhone": "9123456780",
              "claimReason": "It has my college ID card with my name inside"
            }
            """.formatted(reportId, email);
    }

    /** Creates a report through the API and returns its new id. */
    protected long createReport(String type, String title) throws Exception {
        String body = mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson(type, title, "ravi.kumar@example.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Submits a claim through the API and returns its new id. */
    protected long submitClaim(long reportId, String email) throws Exception {
        String body = mvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson(reportId, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
