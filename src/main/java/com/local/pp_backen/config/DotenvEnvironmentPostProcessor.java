package com.local.pp_backen.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a {@code .env} file from the project directory into the Spring environment, so the
 * {@code ${VAR}} placeholders in {@code application.properties} resolve from it.
 *
 * <p>Registered through {@code META-INF/spring.factories}. Written by hand rather than
 * pulled in as a library because the usual one registers against
 * {@code org.springframework.boot.env.EnvironmentPostProcessor}, which Spring Boot 4 moved
 * to {@code org.springframework.boot.EnvironmentPostProcessor} — so it silently does nothing.
 *
 * <p>The file is added <em>last</em>, which means a real operating-system environment
 * variable always wins. Production keeps using proper secret management; {@code .env} is
 * only a convenience for local runs.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "dotenv";

    /** Looked up in order, so the file is found whether the app is run from the
     *  module directory or the repository root. */
    private static final List<String> CANDIDATES = List.of(".env", "pp_backen/.env");

    private final Log log;

    public DotenvEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DotenvEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(SOURCE_NAME)) {
            return;
        }

        for (String candidate : CANDIDATES) {
            Path file = Paths.get(candidate).toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) continue;

            Map<String, Object> values = read(file);
            if (values.isEmpty()) {
                log.info("Found " + file + " but it set no values.");
                return;
            }

            environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, values));
            log.info("Loaded " + values.size() + " value(s) from " + file
                     + " (real environment variables still take precedence).");
            return;
        }
    }

    private Map<String, Object> read(Path file) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();

                int equals = line.indexOf('=');
                if (equals <= 0) continue;

                String key = line.substring(0, equals).trim();
                String value = line.substring(equals + 1).trim();

                // Strip a matching pair of surrounding quotes, if present
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }

                if (!key.isEmpty()) values.put(key, value);
            }
        } catch (IOException e) {
            log.warn("Could not read " + file + ": " + e.getMessage());
        }
        return values;
    }
}
