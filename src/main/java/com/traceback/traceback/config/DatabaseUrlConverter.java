package com.traceback.traceback.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Hosting providers (Neon, Vercel Postgres, Supabase, Render, Heroku...) usually give
 * the database as one URL:
 *
 *   postgres://user:password@host:5432/dbname?sslmode=require
 *
 * Java's JDBC driver needs it split into three parts instead:
 *
 *   TRACEBACK_DB_URL      = jdbc:postgresql://host:5432/dbname?sslmode=require
 *   TRACEBACK_DB_USERNAME = user
 *   TRACEBACK_DB_PASSWORD = password
 *
 * This class does that conversion before Spring starts. Values you set yourself
 * always win: nothing is overwritten.
 */
public final class DatabaseUrlConverter {

    public static final String SOURCE = "DATABASE_URL";
    public static final String URL = "TRACEBACK_DB_URL";
    public static final String USERNAME = "TRACEBACK_DB_USERNAME";
    public static final String PASSWORD = "TRACEBACK_DB_PASSWORD";

    private DatabaseUrlConverter() {
    }

    /**
     * Reads DATABASE_URL from the environment and, if it is a postgres:// URL,
     * stores the converted values as Java system properties (which Spring reads).
     */
    public static void applyFromEnvironment() {
        Map<String, String> result = convert(System::getenv);
        result.forEach((key, value) -> {
            if (System.getProperty(key) == null) {
                System.setProperty(key, value);
            }
        });
    }

    /**
     * Pure conversion logic (no side effects) so it can be unit-tested.
     *
     * @param lookup returns the value of an environment variable, or null
     * @return the variables to set; empty if there is nothing to convert
     */
    public static Map<String, String> convert(Function<String, String> lookup) {
        Map<String, String> result = new LinkedHashMap<>();

        String databaseUrl = lookup.apply(SOURCE);
        if (isBlank(databaseUrl)) {
            return result;
        }

        databaseUrl = databaseUrl.trim();
        if (!databaseUrl.startsWith("postgres://") && !databaseUrl.startsWith("postgresql://")) {
            return result;
        }

        URI uri = URI.create(databaseUrl);

        if (isBlank(uri.getHost())) {
            throw new IllegalStateException("DATABASE_URL has no host name");
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() != -1) {
            jdbc.append(':').append(uri.getPort());
        }
        String path = uri.getRawPath();
        jdbc.append(isBlank(path) ? "/postgres" : path);

        // Keep only sslmode: other provider-specific options are not needed by JDBC
        String sslMode = queryParameter(uri.getRawQuery(), "sslmode");
        if (sslMode != null) {
            jdbc.append("?sslmode=").append(sslMode);
        }

        String user = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (!isBlank(userInfo)) {
            int colon = userInfo.indexOf(':');
            user = decode(colon >= 0 ? userInfo.substring(0, colon) : userInfo);
            password = colon >= 0 ? decode(userInfo.substring(colon + 1)) : null;
        }

        putIfMissing(result, lookup, URL, jdbc.toString());
        putIfMissing(result, lookup, USERNAME, user);
        putIfMissing(result, lookup, PASSWORD, password);
        return result;
    }

    private static void putIfMissing(Map<String, String> result,
                                     Function<String, String> lookup,
                                     String key, String value) {
        if (value != null && isBlank(lookup.apply(key))) {
            result.put(key, value);
        }
    }

    private static String queryParameter(String rawQuery, String name) {
        if (isBlank(rawQuery)) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equalsIgnoreCase(name)) {
                return decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
