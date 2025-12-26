package com.darkmusic.aiforgotthesecards.testsupport;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class DockerJavaApiVersionInitializer implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        // docker-java uses `api.version` to select a fixed Docker API prefix.
        // Its default is old enough to be rejected by newer Docker daemons (e.g. min API 1.44).
        // When Docker CLI is available, we set `api.version` to the daemon's advertised API.
        if (System.getProperty("api.version") != null) {
            return;
        }

        try {
            Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.APIVersion}}")
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }

            boolean finished = process.waitFor(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return;
            }

            if (process.exitValue() != 0 || output == null) {
                return;
            }

            String apiVersion = output.trim();
            if (!apiVersion.matches("\\d+\\.\\d+")) {
                return;
            }

            System.setProperty("api.version", apiVersion);
        } catch (Exception ignored) {
            // If Docker isn't installed/available, tests using disabledWithoutDocker will skip.
        }
    }
}
