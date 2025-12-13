package com.darkmusic.aiforgotthesecards.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Path;

@Component
public class PortableDumpRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PortableDumpRunner.class);

    private final Environment environment;
    private final DataSource dataSource;
    private final ConfigurableApplicationContext applicationContext;

    public PortableDumpRunner(Environment environment,
                             DataSource dataSource,
                             ConfigurableApplicationContext applicationContext
    ) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String command = environment.getProperty("aiforgot.portableDump.command");
        if (command == null || command.isBlank()) {
            return;
        }

        String file = environment.getProperty("aiforgot.portableDump.file");
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("Missing required property: aiforgot.portableDump.file");
        }

        PortableDumpService service = new PortableDumpService(dataSource);
        int exitCode = 0;

        try {
            PortableDumpService.Command cmd = PortableDumpService.Command.from(command);
            Path zipPath = Path.of(file).toAbsolutePath();

            switch (cmd) {
                case EXPORT -> {
                    log.info("Portable dump export -> {}", zipPath);
                    service.exportTo(zipPath);
                }
                case IMPORT -> {
                    String modeRaw = environment.getProperty("aiforgot.portableDump.mode", "fail-if-not-empty");
                    PortableDumpService.ImportMode mode = PortableDumpService.ImportMode.from(modeRaw);
                    log.info("Portable dump import <- {} (mode={})", zipPath, mode);
                    service.importFrom(zipPath, mode);
                }
                case VALIDATE -> {
                    log.info("Portable dump validate -> {}", zipPath);
                    service.validate(zipPath);
                }
            }

        } catch (Exception e) {
            log.error("Portable dump command failed", e);
            exitCode = 1;
        }

        final int finalExitCode = exitCode;

        // Only exit when explicitly invoked.
        SpringApplication.exit(applicationContext, () -> finalExitCode);
        System.exit(finalExitCode);
    }
}
