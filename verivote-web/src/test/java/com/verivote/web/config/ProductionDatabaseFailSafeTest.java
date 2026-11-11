package com.verivote.web.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class ProductionDatabaseFailSafeTest {

    @Test
    @DisplayName("Production profile refuses to start when PostgreSQL environment variables are missing")
    void refusesToStartWithoutPostgresConfig() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ProductionDatabaseConfig.class)
                .profiles("prod")
                .properties(
                        "server.port=0",
                        "spring.main.web-application-type=none"
                );

        Exception ex = assertThrows(Exception.class, builder::run);
        // Find the root cause or our IllegalStateException
        Throwable cause = ex;
        boolean foundRefusalMessage = false;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("CRITICAL PRODUCTION CONFIGURATION ERROR")) {
                foundRefusalMessage = true;
                break;
            }
            cause = cause.getCause();
        }

        assertTrue(foundRefusalMessage, "Expected startup failure with CRITICAL PRODUCTION CONFIGURATION ERROR but was: " + ex.getMessage());
    }

    @Test
    @DisplayName("Production profile refuses to start if H2 URL is passed")
    void refusesToStartWithH2UrlInProd() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ProductionDatabaseConfig.class)
                .profiles("prod")
                .properties(
                        "server.port=0",
                        "spring.main.web-application-type=none",
                        "DATABASE_URL=jdbc:h2:mem:shouldfail"
                );

        Exception ex = assertThrows(Exception.class, builder::run);
        Throwable cause = ex;
        boolean foundProhibition = false;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("prohibits H2 / in-memory databases")) {
                foundProhibition = true;
                break;
            }
            cause = cause.getCause();
        }

        assertTrue(foundProhibition, "Expected failure prohibiting H2 in production, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Production profile refuses to fall back if PostgreSQL host is unreachable")
    void refusesToStartIfPostgresUnreachable() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ProductionDatabaseConfig.class)
                .profiles("prod")
                .properties(
                        "server.port=0",
                        "spring.main.web-application-type=none",
                        "DATABASE_URL=postgresql://user:pass@127.0.0.1:54329/nonexistentdb"
                );

        // HikariCP will try to initialize pool with 10s connection timeout and fail
        Exception ex = assertThrows(Exception.class, builder::run);
        assertNotNull(ex, "Application must abort when PostgreSQL target is unreachable");
    }
}
