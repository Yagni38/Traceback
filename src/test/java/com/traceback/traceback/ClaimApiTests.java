package com.traceback.traceback;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tests for /api/claims: submitting, duplicates, admin-only review, consistency. */
class ClaimApiTests extends ApiTestSupport {

    private static final String APPROVE = "{\"status\":\"APPROVED\"}";
    private static final String REJECT = "{\"status\":\"REJECTED\"}";

    @Test
    void submitClaim_onActiveFoundItem_returns201Pending() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");

        mvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson(foundId, "asha@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reportId").value(foundId));
    }

    @Test
    void submitClaim_duplicateFromSameEmail_returns409() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");
        submitClaim(foundId, "asha@example.com");

        mvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson(foundId, "ASHA@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already have a pending claim")));
    }

    @Test
    void submitClaim_onLostItem_returns400() throws Exception {
        long lostId = createReport("LOST", "Black Wallet");

        mvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson(lostId, "asha@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitClaim_unknownReport_returns404() throws Exception {
        mvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson(987654, "asha@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitClaim_withShortReasonAndBadEmail_returns400() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");
        String json = """
            {"reportId": %d, "claimantName": "Asha",
             "claimantEmail": "nope", "claimReason": "mine"}
            """.formatted(foundId);

        mvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.claimantEmail").exists())
                .andExpect(jsonPath("$.fieldErrors.claimReason").exists());
    }

    @Test
    void listClaims_withoutLogin_returns401() throws Exception {
        mvc.perform(get("/api/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listClaims_asAdmin_returnsClaims() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");
        submitClaim(foundId, "asha@example.com");

        mvc.perform(get("/api/claims").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].claimantEmail").value("asha@example.com"));
    }

    @Test
    void reviewClaim_withoutLogin_returns401_andNothingChanges() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");
        long claimId = submitClaim(foundId, "asha@example.com");

        mvc.perform(patch("/api/claims/" + claimId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPROVE))
                .andExpect(status().isUnauthorized());

        assertEquals("PENDING", claimRepository.findById(claimId).orElseThrow().getStatus());
        assertEquals("ACTIVE", reportRepository.findById(foundId).orElseThrow().getStatus());
    }

    @Test
    void approveClaim_marksItemReturned_andRejectsOtherPendingClaims() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");
        long winner = submitClaim(foundId, "asha@example.com");
        long other = submitClaim(foundId, "someone.else@example.com");

        mvc.perform(patch("/api/claims/" + winner + "/status")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPROVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertEquals("RETURNED", reportRepository.findById(foundId).orElseThrow().getStatus());
        assertEquals("REJECTED", claimRepository.findById(other).orElseThrow().getStatus());
    }

    @Test
    void rejectClaim_keepsItemActive() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");
        long claimId = submitClaim(foundId, "asha@example.com");

        mvc.perform(patch("/api/claims/" + claimId + "/status")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REJECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertEquals("ACTIVE", reportRepository.findById(foundId).orElseThrow().getStatus());
    }

    @Test
    void reviewClaim_twice_returns409() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");
        long claimId = submitClaim(foundId, "asha@example.com");

        mvc.perform(patch("/api/claims/" + claimId + "/status")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(REJECT))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/claims/" + claimId + "/status")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(APPROVE))
                .andExpect(status().isConflict());
    }

    @Test
    void reviewClaim_withInvalidStatus_returns400() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");
        long claimId = submitClaim(foundId, "asha@example.com");

        mvc.perform(patch("/api/claims/" + claimId + "/status")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitClaim_afterItemReturned_returns409() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");
        long claimId = submitClaim(foundId, "asha@example.com");

        mvc.perform(patch("/api/claims/" + claimId + "/status")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON).content(APPROVE))
                .andExpect(status().isOk());

        mvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson(foundId, "late@example.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void markingItemReturnedDirectly_rejectsItsPendingClaims() throws Exception {
        long foundId = createReport("FOUND", "Black Wallet");
        long claimId = submitClaim(foundId, "asha@example.com");

        mvc.perform(patch("/api/reports/" + foundId + "/status")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RETURNED\"}"))
                .andExpect(status().isOk());

        assertEquals("REJECTED", claimRepository.findById(claimId).orElseThrow().getStatus());
    }
}
