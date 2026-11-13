package com.traceback.traceback.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests for turning postgres:// URLs into JDBC settings. */
class DatabaseUrlConverterTests {

    @Test
    void convertsNeonStyleUrl() {
        Map<String, String> env = Map.of("DATABASE_URL",
                "postgres://traceback_user:s3cr%40t%2Bpw@ep-cool-1234.ap-southeast-1.aws.neon.tech/tracebackdb"
                        + "?sslmode=require&channel_binding=require");

        Map<String, String> result = DatabaseUrlConverter.convert(env::get);

        assertEquals("jdbc:postgresql://ep-cool-1234.ap-southeast-1.aws.neon.tech/tracebackdb?sslmode=require",
                result.get("TRACEBACK_DB_URL"));
        assertEquals("traceback_user", result.get("TRACEBACK_DB_USERNAME"));
        assertEquals("s3cr@t+pw", result.get("TRACEBACK_DB_PASSWORD"));
    }

    @Test
    void keepsPortAndAcceptsPostgresqlScheme() {
        Map<String, String> env = Map.of("DATABASE_URL",
                "postgresql://admin:pw@db.example.com:6543/app");

        Map<String, String> result = DatabaseUrlConverter.convert(env::get);

        assertEquals("jdbc:postgresql://db.example.com:6543/app", result.get("TRACEBACK_DB_URL"));
        assertEquals("admin", result.get("TRACEBACK_DB_USERNAME"));
        assertEquals("pw", result.get("TRACEBACK_DB_PASSWORD"));
    }

    @Test
    void neverOverwritesValuesTheUserSet() {
        Map<String, String> env = Map.of(
                "DATABASE_URL", "postgres://u:p@host/db",
                "TRACEBACK_DB_URL", "jdbc:postgresql://other/db");

        Map<String, String> result = DatabaseUrlConverter.convert(env::get);

        assertFalse(result.containsKey("TRACEBACK_DB_URL"));
        assertEquals("u", result.get("TRACEBACK_DB_USERNAME"));
    }

    @Test
    void ignoresMissingOrNonPostgresUrls() {
        assertTrue(DatabaseUrlConverter.convert(Map.<String, String>of()::get).isEmpty());
        assertTrue(DatabaseUrlConverter.convert(
                Map.of("DATABASE_URL", "mysql://u:p@host/db")::get).isEmpty());
    }
}
