package com.verivote.web.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostgresUrlParserTest {

    @Test
    @DisplayName("Parses standard Railway postgresql:// URL with credentials and port")
    void parseStandardRailwayUrl() {
        String railwayUrl = "postgresql://postgres:secretpassword123@roundhouse.proxy.rlwy.net:54321/railway";
        PostgresUrlParser.ParsedDbConfig config = PostgresUrlParser.parse(railwayUrl, null, null);

        assertEquals("jdbc:postgresql://roundhouse.proxy.rlwy.net:54321/railway", config.jdbcUrl());
        assertEquals("postgres", config.username());
        assertEquals("secretpassword123", config.password());
    }

    @Test
    @DisplayName("Parses postgres:// alias URL with URL-encoded characters in password")
    void parsePostgresAliasWithEncodedPassword() {
        String url = "postgres://app_user:p%40ss%3Aword@pg.example.com:5432/votedb";
        PostgresUrlParser.ParsedDbConfig config = PostgresUrlParser.parse(url, null, null);

        assertEquals("jdbc:postgresql://pg.example.com:5432/votedb", config.jdbcUrl());
        assertEquals("app_user", config.username());
        assertEquals("p@ss:word", config.password());
    }

    @Test
    @DisplayName("Accepts pre-formatted jdbc:postgresql: URL and uses provided defaults")
    void parsePreFormattedJdbcUrl() {
        String jdbc = "jdbc:postgresql://localhost:5432/verivotedb?sslmode=disable";
        PostgresUrlParser.ParsedDbConfig config = PostgresUrlParser.parse(jdbc, "dbadmin", "adminpass");

        assertEquals(jdbc, config.jdbcUrl());
        assertEquals("dbadmin", config.username());
        assertEquals("adminpass", config.password());
    }

    @Test
    @DisplayName("Preserves query parameters like sslmode in Railway private URL")
    void parseUrlWithQueryParams() {
        String url = "postgresql://usr:pwd@postgres.railway.internal:5432/railway?sslmode=require";
        PostgresUrlParser.ParsedDbConfig config = PostgresUrlParser.parse(url, null, null);

        assertEquals("jdbc:postgresql://postgres.railway.internal:5432/railway?sslmode=require", config.jdbcUrl());
        assertEquals("usr", config.username());
        assertEquals("pwd", config.password());
    }

    @Test
    @DisplayName("Strictly rejects H2 in-memory or file URLs to prevent silent fallback")
    void rejectsH2Urls() {
        assertThrows(IllegalStateException.class, () ->
            PostgresUrlParser.parse("jdbc:h2:mem:testdb", null, null)
        );

        assertThrows(IllegalStateException.class, () ->
            PostgresUrlParser.parse("jdbc:h2:file:./testdb", null, null)
        );
    }

    @Test
    @DisplayName("Rejects null, blank, or unsupported schemes")
    void rejectsInvalidSchemes() {
        assertThrows(IllegalArgumentException.class, () -> PostgresUrlParser.parse(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> PostgresUrlParser.parse("   ", null, null));
        assertThrows(IllegalArgumentException.class, () -> PostgresUrlParser.parse("mysql://localhost:3306/db", null, null));
    }
}
