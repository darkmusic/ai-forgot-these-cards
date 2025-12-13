package com.darkmusic.aiforgotthesecards.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.springframework.util.StringUtils.hasText;

public class DatabaseVendorEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "aiForgotTheseCardsDatabaseVendor";

    private static final String DB_VENDOR = "DB_VENDOR";
    private static final String SQLITE_DB_PATH = "SQLITE_DB_PATH";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String vendor = environment.getProperty(DB_VENDOR);
        if (!hasText(vendor)) {
            return;
        }

        vendor = vendor.trim().toLowerCase(Locale.ROOT);
        if (!"sqlite".equals(vendor)) {
            return;
        }

        String sqlitePath = environment.getProperty(SQLITE_DB_PATH);
        if (!hasText(sqlitePath)) {
            sqlitePath = "./db/cards.db";
        }

        sqlitePath = sqlitePath.trim();
        if (!":memory:".equals(sqlitePath)) {
            Path dbPath = Path.of(sqlitePath).toAbsolutePath();
            Path parent = dbPath.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to create parent directory for SQLITE_DB_PATH: " + dbPath, e);
                }
            }
        }

        // Recommended for a single-file deployment.
        // - foreign_keys=on: enforce FK constraints
        // - busy_timeout: reduce "database is locked" flakiness
        // - journal_mode=WAL: better concurrency for reads
        // Xerial supports SQLite config via URL query params.
        String jdbcUrl = "jdbc:sqlite:" + sqlitePath + "?foreign_keys=on&busy_timeout=5000&journal_mode=WAL";

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("spring.datasource.url", jdbcUrl);
        overrides.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
        overrides.put("spring.datasource.username", "");
        overrides.put("spring.datasource.password", "");

        // Hibernate 6: dialect is provided by hibernate-community-dialects.
        overrides.put("spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");

        // SQLite is single-writer; keep pool small to avoid lock contention.
        // Using 2 avoids startup deadlocks between schema generation and init runners.
        overrides.put("spring.datasource.hikari.maximum-pool-size", "2");

        // Some drivers ignore URL params for pragmas; connection init SQL helps.
        overrides.put("spring.datasource.hikari.connection-init-sql",
                "PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000; PRAGMA journal_mode = WAL;");

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
