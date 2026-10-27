package com.jayadeep.jobradar.apply;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * @param outputDir             where each application's tailored resume and log are written
 * @param browserProfileDir     persistent Chromium profile, so sign-ins survive between runs
 * @param signInTimeoutMinutes  how long to wait for you to sign in (Workday) before giving up
 * @param headless              run the browser without a window; only useful for tests
 */
@ConfigurationProperties(prefix = "jobradar.apply")
public record ApplyProperties(Path outputDir, Path browserProfileDir, Integer signInTimeoutMinutes, Boolean headless) {

    public ApplyProperties {
        outputDir = outputDir == null ? Path.of("applications") : outputDir;
        browserProfileDir = browserProfileDir == null ? Path.of("data", "browser-profile") : browserProfileDir;
        signInTimeoutMinutes = signInTimeoutMinutes == null ? 10 : signInTimeoutMinutes;
        headless = headless != null && headless;
    }
}
