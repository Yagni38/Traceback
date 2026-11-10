package com.traceback.traceback;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public pages are open; the admin page and database console need the admin login. */
class PageSecurityTests extends ApiTestSupport {

    @Test
    void publicPages_areOpenToEveryone() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/style.css")).andExpect(status().isOk());
    }

    @Test
    void adminPage_withoutLogin_returns401() throws Exception {
        mvc.perform(get("/admin.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin.js")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminPage_withAdminLogin_returns200() throws Exception {
        mvc.perform(get("/admin.html").with(admin())).andExpect(status().isOk());
    }

    @Test
    void h2Console_withoutLogin_returns401() throws Exception {
        mvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());
    }
}
