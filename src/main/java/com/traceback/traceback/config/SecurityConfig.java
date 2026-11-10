package com.traceback.traceback.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Decides who may call which URL.
 *
 * Public (no login):  the main website, submitting reports and claims,
 *                     browsing reports (contact details masked), matching.
 * Admin only:         admin page, reviewing claims, changing item status,
 *                     full contact details, H2 database console.
 *
 * The admin logs in with HTTP Basic authentication; the browser shows
 * its own login box when /admin.html is opened.
 */
@Configuration
public class SecurityConfig {

    public static final String ADMIN = "ADMIN";

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Admin page, its files and the database console
                .requestMatchers("/admin.html", "/admin.js", "/admin.css").hasRole(ADMIN)
                .requestMatchers("/h2-console", "/h2-console/**").hasRole(ADMIN)

                // Claims: anyone can submit, only admin can list or review
                .requestMatchers(HttpMethod.POST, "/api/claims").permitAll()
                .requestMatchers("/api/claims", "/api/claims/**").hasRole(ADMIN)

                // Reports: anyone can submit, browse and match; only admin changes status
                .requestMatchers(HttpMethod.PATCH, "/api/reports/*/status").hasRole(ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/reports", "/api/reports/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/reports").permitAll()

                // Public website files and Spring's error page
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/app.js",
                        "/style.css", "/favicon.ico").permitAll()
                .requestMatchers("/error").permitAll()

                // Anything not listed above needs an admin login
                .anyRequest().hasRole(ADMIN)
            )
            .httpBasic(Customizer.withDefaults())
            // The API only accepts JSON bodies sent by our own pages, and the admin
            // actions use PATCH, which a cross-site HTML form cannot send.
            .csrf(AbstractHttpConfigurer::disable)
            // The H2 console runs inside frames from the same site
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Creates the single admin account from environment variables.
     * No password is stored in the source code.
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${traceback.admin.username:admin}") String username,
            @Value("${traceback.admin.password:}") String password,
            PasswordEncoder encoder) {

        String finalPassword = password;

        if (finalPassword == null || finalPassword.isBlank()) {
            finalPassword = generatePassword();
            log.warn("""

                    ============================================================
                     TRACEBACK_ADMIN_PASSWORD is not set.
                     Temporary admin login for this run only:
                       username: {}
                       password: {}
                     Set TRACEBACK_ADMIN_PASSWORD to choose your own password.
                    ============================================================
                    """, username, finalPassword);
        }

        UserDetails admin = User.withUsername(username)
                .password(encoder.encode(finalPassword))
                .roles(ADMIN)
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

    private static String generatePassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
