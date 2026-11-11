package com.verivote.web.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * Production database configuration.
 * Active strictly when the 'prod' profile is enabled.
 * 
 * Strict Guarantees:
 * 1. Requires valid PostgreSQL configuration.
 * 2. Refuses to start if database URL or host is missing or invalid.
 * 3. Never silently falls back to in-memory H2.
 * 4. Supports Railway DATABASE_URL, DATABASE_PRIVATE_URL, DATABASE_PUBLIC_URL,
 *    and standard Spring Boot datasource properties.
 */
@Configuration
@Profile("prod")
public class ProductionDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductionDatabaseConfig.class);

    @Autowired(required = false)
    private Environment environment;

    @Value("${spring.datasource.url:#{null}}")
    private String directSpringDatasourceUrl;

    @Value("${SPRING_DATASOURCE_URL:#{null}}")
    private String springDatasourceUrl;

    @Value("${DATABASE_URL:#{null}}")
    private String databaseUrl;

    @Value("${DATABASE_PRIVATE_URL:#{null}}")
    private String databasePrivateUrl;

    @Value("${DATABASE_PUBLIC_URL:#{null}}")
    private String databasePublicUrl;

    @Value("${PGHOST:#{null}}")
    private String pgHost;

    @Value("${PGPORT:5432}")
    private String pgPort;

    @Value("${PGDATABASE:railway}")
    private String pgDatabase;

    @Value("${SPRING_DATASOURCE_USERNAME:#{null}}")
    private String springUsername;

    @Value("${PGUSER:#{null}}")
    private String pgUser;

    @Value("${SPRING_DATASOURCE_PASSWORD:#{null}}")
    private String springPassword;

    @Value("${PGPASSWORD:#{null}}")
    private String pgPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("Initializing VeriVote production DataSource (Profile: prod)...");

        // 1. Identify raw candidate URL or synthesize from PGHOST
        String rawUrl = resolveRawDatabaseUrl();

        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalStateException(
                "CRITICAL PRODUCTION CONFIGURATION ERROR: The 'prod' profile is active, but no PostgreSQL database " +
                "configuration was detected. Checked: SPRING_DATASOURCE_URL, DATABASE_URL, DATABASE_PRIVATE_URL, " +
                "DATABASE_PUBLIC_URL, and PGHOST. VeriVote refuses to start to prevent data loss or silent fallback to in-memory H2."
            );
        }

        // 2. Resolve default user and password
        String defaultUser = resolveUsername();
        String defaultPassword = resolvePassword();

        // 3. Parse into JDBC URL and credentials
        PostgresUrlParser.ParsedDbConfig parsed = PostgresUrlParser.parse(rawUrl, defaultUser, defaultPassword);

        // 4. Configure HikariCP with robust production pooling parameters
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setJdbcUrl(parsed.jdbcUrl());

        if (parsed.username() != null && !parsed.username().isBlank()) {
            hikariConfig.setUsername(parsed.username());
        }
        if (parsed.password() != null && !parsed.password().isBlank()) {
            hikariConfig.setPassword(parsed.password());
        }

        hikariConfig.setPoolName("VeriVote-PostgreSQL-Pool");
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(10000); // 10 seconds fail-fast
        hikariConfig.setValidationTimeout(5000);   // 5 seconds
        hikariConfig.setIdleTimeout(300000);       // 5 minutes
        hikariConfig.setMaxLifetime(1800000);     // 30 minutes

        log.info("Production PostgreSQL pool configured. JDBC Target: {}", sanitizeJdbcUrl(parsed.jdbcUrl()));

        return new HikariDataSource(hikariConfig);
    }

    private String resolveRawDatabaseUrl() {
        // 1. Injected fields via @Value
        if (directSpringDatasourceUrl != null && !directSpringDatasourceUrl.isBlank() && !directSpringDatasourceUrl.contains(":h2:")) {
            return directSpringDatasourceUrl;
        }
        if (springDatasourceUrl != null && !springDatasourceUrl.isBlank()) {
            return springDatasourceUrl;
        }
        if (databasePrivateUrl != null && !databasePrivateUrl.isBlank()) {
            return databasePrivateUrl;
        }
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            return databaseUrl;
        }
        if (databasePublicUrl != null && !databasePublicUrl.isBlank()) {
            return databasePublicUrl;
        }

        // 2. Fallback to Environment bean property queries (handles lowercase dot notation)
        if (environment != null) {
            String envDsUrl = environment.getProperty("spring.datasource.url");
            if (envDsUrl != null && !envDsUrl.isBlank() && !envDsUrl.contains(":h2:")) {
                return envDsUrl;
            }
            String envDbUrl = environment.getProperty("DATABASE_URL");
            if (envDbUrl != null && !envDbUrl.isBlank()) {
                return envDbUrl;
            }
            String envPrivUrl = environment.getProperty("DATABASE_PRIVATE_URL");
            if (envPrivUrl != null && !envPrivUrl.isBlank()) {
                return envPrivUrl;
            }
            String envPubUrl = environment.getProperty("DATABASE_PUBLIC_URL");
            if (envPubUrl != null && !envPubUrl.isBlank()) {
                return envPubUrl;
            }
        }

        // 3. Fallback to direct OS environment variable queries
        String osDatabaseUrl = System.getenv("DATABASE_URL");
        if (osDatabaseUrl != null && !osDatabaseUrl.isBlank()) {
            return osDatabaseUrl;
        }
        String osDatabasePrivateUrl = System.getenv("DATABASE_PRIVATE_URL");
        if (osDatabasePrivateUrl != null && !osDatabasePrivateUrl.isBlank()) {
            return osDatabasePrivateUrl;
        }
        String osDatabasePublicUrl = System.getenv("DATABASE_PUBLIC_URL");
        if (osDatabasePublicUrl != null && !osDatabasePublicUrl.isBlank()) {
            return osDatabasePublicUrl;
        }
        String osSpringDsUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (osSpringDsUrl != null && !osSpringDsUrl.isBlank()) {
            return osSpringDsUrl;
        }

        // 4. Synthesize from individual PG host/port/database parameters
        String host = (pgHost != null && !pgHost.isBlank()) ? pgHost : System.getenv("PGHOST");
        if (host != null && !host.isBlank()) {
            String port = (pgPort != null && !pgPort.isBlank()) ? pgPort : (System.getenv("PGPORT") != null ? System.getenv("PGPORT") : "5432");
            String db = (pgDatabase != null && !pgDatabase.isBlank()) ? pgDatabase : (System.getenv("PGDATABASE") != null ? System.getenv("PGDATABASE") : "railway");
            return "jdbc:postgresql://" + host.trim() + ":" + port.trim() + "/" + db.trim();
        }

        return null;
    }

    private String resolveUsername() {
        if (springUsername != null && !springUsername.isBlank()) return springUsername;
        if (pgUser != null && !pgUser.isBlank()) return pgUser;
        if (environment != null) {
            String u = environment.getProperty("spring.datasource.username");
            if (u != null && !u.isBlank()) return u;
            u = environment.getProperty("PGUSER");
            if (u != null && !u.isBlank()) return u;
        }
        String envUser = System.getenv("PGUSER");
        if (envUser != null && !envUser.isBlank()) return envUser;
        return System.getenv("SPRING_DATASOURCE_USERNAME");
    }

    private String resolvePassword() {
        if (springPassword != null && !springPassword.isBlank()) return springPassword;
        if (pgPassword != null && !pgPassword.isBlank()) return pgPassword;
        if (environment != null) {
            String p = environment.getProperty("spring.datasource.password");
            if (p != null && !p.isBlank()) return p;
            p = environment.getProperty("PGPASSWORD");
            if (p != null && !p.isBlank()) return p;
        }
        String envPass = System.getenv("PGPASSWORD");
        if (envPass != null && !envPass.isBlank()) return envPass;
        return System.getenv("SPRING_DATASOURCE_PASSWORD");
    }

    private static String sanitizeJdbcUrl(String url) {
        if (url == null) return "null";
        // Strip sensitive query strings if any
        int qIdx = url.indexOf('?');
        return (qIdx > 0) ? url.substring(0, qIdx) + "?[parameters-hidden]" : url;
    }
}
