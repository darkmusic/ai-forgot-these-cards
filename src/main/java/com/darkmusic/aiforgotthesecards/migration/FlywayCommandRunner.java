package com.darkmusic.aiforgotthesecards.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FlywayCommandRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayCommandRunner.class);

    private final Environment environment;
    private final DataSource dataSource;
    private final ConfigurableApplicationContext applicationContext;

    public FlywayCommandRunner(Environment environment,
                               DataSource dataSource,
                               ConfigurableApplicationContext applicationContext) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        String command = environment.getProperty("aiforgot.flyway.command");
        if (command == null || command.isBlank()) {
            return;
        }

        int exitCode = 0;
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(migrationLocations())
                    .cleanDisabled(true)
                    .baselineVersion(environment.getProperty("spring.flyway.baseline-version", "1"))
                    .baselineDescription(environment.getProperty("spring.flyway.baseline-description", "Existing Hibernate-managed schema"))
                    .load();

            switch (command.trim().toLowerCase(Locale.ROOT)) {
                case "migrate" -> {
                    var result = flyway.migrate();
                    log.info("Flyway migrate complete: migrationsExecuted={}, targetSchemaVersion={}",
                            result.migrationsExecuted, result.targetSchemaVersion);
                }
                case "validate" -> {
                    flyway.validate();
                    log.info("Flyway validate complete.");
                }
                case "info" -> logInfo(flyway);
                case "baseline" -> {
                    flyway.baseline();
                    log.info("Flyway baseline complete.");
                }
                default -> throw new IllegalArgumentException("Unknown Flyway command: " + command);
            }
        } catch (Exception e) {
            log.error("Flyway command failed", e);
            exitCode = 1;
        }

        final int finalExitCode = exitCode;
        SpringApplication.exit(applicationContext, () -> finalExitCode);
        System.exit(finalExitCode);
    }

    private String[] migrationLocations() {
        String configured = environment.getProperty("spring.flyway.locations");
        if (configured != null && !configured.isBlank()) {
            return Arrays.stream(configured.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toArray(String[]::new);
        }

        String vendor = environment.getProperty("DB_VENDOR", "postgres").trim().toLowerCase(Locale.ROOT);
        if ("sqlite".equals(vendor)) {
            return new String[]{"classpath:db/migration/sqlite"};
        }
        return new String[]{"classpath:db/migration/postgresql"};
    }

    private void logInfo(Flyway flyway) {
        for (MigrationInfo info : flyway.info().all()) {
            log.info("Flyway migration: version={}, description={}, state={}",
                    info.getVersion(), info.getDescription(), info.getState());
        }
    }
}
