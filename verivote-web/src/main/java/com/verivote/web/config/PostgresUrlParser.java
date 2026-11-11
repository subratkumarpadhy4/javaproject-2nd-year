package com.verivote.web.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility for parsing PostgreSQL database URLs from diverse cloud hosting environments
 * (e.g., Railway, Heroku, AWS) into standard JDBC format suitable for HikariCP.
 */
public final class PostgresUrlParser {

    private PostgresUrlParser() {}

    public record ParsedDbConfig(String jdbcUrl, String username, String password) {}

    public static ParsedDbConfig parse(String rawUrl, String defaultUser, String defaultPassword) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Database URL cannot be null or blank");
        }

        String trimmed = rawUrl.trim();

        // 1. Prohibit in-memory / H2 databases in production
        if (trimmed.toLowerCase().contains("jdbc:h2") || trimmed.toLowerCase().contains(":h2:")) {
            throw new IllegalStateException(
                "CRITICAL: Production profile 'prod' prohibits H2 / in-memory databases. " +
                "Detected invalid URL: " + trimmed
            );
        }

        // 2. Already formatted as JDBC URL
        if (trimmed.startsWith("jdbc:postgresql:")) {
            return new ParsedDbConfig(trimmed, defaultUser, defaultPassword);
        }

        // 3. Cloud provider URI: postgresql://... or postgres://...
        if (trimmed.startsWith("postgresql://") || trimmed.startsWith("postgres://")) {
            String normalizedUriString = trimmed;
            if (normalizedUriString.startsWith("postgres://")) {
                normalizedUriString = "postgresql://" + normalizedUriString.substring("postgres://".length());
            }

            try {
                URI uri = URI.create(normalizedUriString);
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath(); // e.g. "/railway"
                String query = uri.getQuery();

                String username = defaultUser;
                String password = defaultPassword;

                if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                    String[] userParts = uri.getUserInfo().split(":", 2);
                    username = URLDecoder.decode(userParts[0], StandardCharsets.UTF_8);
                    if (userParts.length > 1) {
                        password = URLDecoder.decode(userParts[1], StandardCharsets.UTF_8);
                    }
                }

                StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://");
                if (host == null || host.isBlank()) {
                    throw new IllegalArgumentException("Database URL does not contain a valid host: " + rawUrl);
                }
                jdbcUrl.append(host);
                if (port > 0) {
                    jdbcUrl.append(":").append(port);
                }
                if (path != null && !path.isBlank()) {
                    jdbcUrl.append(path);
                }
                if (query != null && !query.isBlank()) {
                    jdbcUrl.append("?").append(query);
                }

                return new ParsedDbConfig(jdbcUrl.toString(), username, password);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse PostgreSQL URL: " + rawUrl, e);
            }
        }

        throw new IllegalArgumentException(
            "Unrecognized database URL scheme (expected postgresql://, postgres://, or jdbc:postgresql://): " + rawUrl
        );
    }
}
