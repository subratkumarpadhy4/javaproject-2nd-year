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
        // Verify it failed at Hikari pool connection attempt, NOT at line 74 missing configuration
        assertFalse(findRootMessage(ex).contains("CRITICAL PRODUCTION CONFIGURATION ERROR"));
    }

    @Test
    @DisplayName("Production profile resolves DATABASE_PRIVATE_URL without failing on line 74")
    void resolvesDatabasePrivateUrl() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ProductionDatabaseConfig.class)
                .profiles("prod")
                .properties(
                        "server.port=0",
                        "spring.main.web-application-type=none",
                        "DATABASE_PRIVATE_URL=postgresql://user:pass@127.0.0.1:54329/railway"
                );

        Exception ex = assertThrows(Exception.class, builder::run);
        // Should reach connection attempt (port 54329 refused), not line 74 missing config
        assertFalse(findRootMessage(ex).contains("CRITICAL PRODUCTION CONFIGURATION ERROR"));
    }

    @Test
    @DisplayName("Production profile resolves SPRING_DATASOURCE_URL without failing on line 74")
    void resolvesSpringDatasourceUrl() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ProductionDatabaseConfig.class)
                .profiles("prod")
                .properties(
                        "server.port=0",
                        "spring.main.web-application-type=none",
                        "SPRING_DATASOURCE_URL=postgresql://user:pass@127.0.0.1:54329/railway"
                );

        Exception ex = assertThrows(Exception.class, builder::run);
        assertFalse(findRootMessage(ex).contains("CRITICAL PRODUCTION CONFIGURATION ERROR"));
    }

    @Test
    @DisplayName("Production profile synthesizes from PGHOST and PGPORT without failing on line 74")
    void resolvesPgHostAndPort() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ProductionDatabaseConfig.class)
                .profiles("prod")
                .properties(
                        "server.port=0",
                        "spring.main.web-application-type=none",
                        "PGHOST=127.0.0.1",
                        "PGPORT=54329",
                        "PGDATABASE=railway"
                );

        Exception ex = assertThrows(Exception.class, builder::run);
        assertFalse(findRootMessage(ex).contains("CRITICAL PRODUCTION CONFIGURATION ERROR"));
    }

    private static String findRootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable curr = t;
        while (curr != null) {
            if (curr.getMessage() != null) {
                sb.append(curr.getMessage()).append(" ");
            }
            curr = curr.getCause();
        }
        return sb.toString();
    }
}
