package com.traceback.traceback.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Plain unit tests (no Spring) for hiding personal contact details. */
class ReportResponseMaskingTests {

    @Test
    void masksEmail() {
        assertEquals("r***@example.com", ReportResponse.maskEmail("ravi.kumar@example.com"));
        assertEquals("***", ReportResponse.maskEmail("broken-email"));
        assertNull(ReportResponse.maskEmail(null));
    }

    @Test
    void masksPhone() {
        assertEquals("******3210", ReportResponse.maskPhone("9876543210"));
        assertEquals("****", ReportResponse.maskPhone("123"));
        assertNull(ReportResponse.maskPhone(""));
    }

    @Test
    void masksName() {
        assertEquals("Ravi K.", ReportResponse.maskName("Ravi Kumar"));
        assertEquals("Ravi S.", ReportResponse.maskName("  Ravi   Shankar  Sharma "));
        assertEquals("Ravi", ReportResponse.maskName("Ravi"));
    }
}
