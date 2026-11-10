package com.traceback.traceback;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tests for /api/reports: creating, validation, privacy, status changes, matching. */
class ReportApiTests extends ApiTestSupport {

    @Test
    void createReport_withValidData_returns201AndActiveStatus() throws Exception {
        mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson("lost", "Black Wallet", "ravi@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.reportType").value("LOST"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reporterEmail").value("ravi@example.com"));
    }

    @Test
    void createReport_withInvalidFields_returns400WithFieldErrors() throws Exception {
        String bad = """
            {
              "reportType": "STOLEN",
              "title": "",
              "category": "Wallet",
              "location": "Library",
              "reporterName": "Ravi",
              "reporterEmail": "not-an-email",
              "reporterPhone": "123"
            }
            """;

        mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.reportType").exists())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.reporterEmail").exists())
                .andExpect(jsonPath("$.fieldErrors.reporterPhone").exists());
    }

    @Test
    void createReport_withFutureDate_returns400() throws Exception {
        String json = reportJson("FOUND", "Phone", "a@example.com")
                .replace("2026-11-01", "2999-01-01");

        mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.eventDate").exists());
    }

    @Test
    void createReport_withBrokenJson_returns400() throws Exception {
        mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createReport_cannotSetStatusOrIdFromRequest() throws Exception {
        String json = reportJson("FOUND", "Phone", "a@example.com")
                .replace("{", "{ \"id\": 999, \"status\": \"RETURNED\",");

        mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").value(not(999)));
    }

    @Test
    void publicReportList_masksContactDetails() throws Exception {
        createReport("FOUND", "Blue Umbrella");

        mvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reporterName").value("Ravi K."))
                .andExpect(jsonPath("$[0].reporterEmail").value("r***@example.com"))
                .andExpect(jsonPath("$[0].reporterPhone").value("******3210"))
                .andExpect(jsonPath("$[0].contactMasked").value(true));
    }

    @Test
    void adminReportList_showsFullContactDetails() throws Exception {
        createReport("FOUND", "Blue Umbrella");

        mvc.perform(get("/api/reports").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reporterEmail").value("ravi.kumar@example.com"))
                .andExpect(jsonPath("$[0].reporterPhone").value("9876543210"))
                .andExpect(jsonPath("$[0].contactMasked").value(false));
    }

    @Test
    void reportList_isNewestFirst() throws Exception {
        createReport("LOST", "First");
        createReport("LOST", "Second");

        mvc.perform(get("/api/reports"))
                .andExpect(jsonPath("$[0].title").value("Second"))
                .andExpect(jsonPath("$[1].title").value("First"));
    }

    @Test
    void getReport_unknownId_returns404() throws Exception {
        mvc.perform(get("/api/reports/123456"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Report not found")));
    }

    @Test
    void getReport_nonNumericId_returns400() throws Exception {
        mvc.perform(get("/api/reports/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_withoutLogin_returns401() throws Exception {
        long id = createReport("FOUND", "Keys");

        mvc.perform(patch("/api/reports/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RETURNED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStatus_withWrongPassword_returns401() throws Exception {
        long id = createReport("FOUND", "Keys");

        mvc.perform(patch("/api/reports/" + id + "/status")
                        .with(httpBasic(ADMIN_USER, "wrong-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RETURNED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStatus_asAdmin_marksReturned() throws Exception {
        long id = createReport("FOUND", "Keys");

        mvc.perform(patch("/api/reports/" + id + "/status")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"returned\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));
    }

    @Test
    void updateStatus_withInvalidStatus_returns400() throws Exception {
        long id = createReport("FOUND", "Keys");

        mvc.perform(patch("/api/reports/" + id + "/status")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DESTROYED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matches_findOppositeTypeWithScoreAndMaskedContact() throws Exception {
        long lostId = createReport("LOST", "Black Wallet");
        createReport("FOUND", "Black Wallet");
        createReport("LOST", "Another lost wallet"); // same type: must not match

        mvc.perform(get("/api/reports/" + lostId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].matchedItem.reportType").value("FOUND"))
                .andExpect(jsonPath("$[0].matchPercentage").value(greaterThanOrEqualTo(90)))
                .andExpect(jsonPath("$[0].reasons", hasItem("Same item category")))
                .andExpect(jsonPath("$[0].matchedItem.reporterEmail").value("r***@example.com"));
    }

    @Test
    void matches_ignoreReturnedItems() throws Exception {
        long lostId = createReport("LOST", "Black Wallet");
        long foundId = createReport("FOUND", "Black Wallet");

        mvc.perform(patch("/api/reports/" + foundId + "/status")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RETURNED\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/reports/" + lostId + "/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
