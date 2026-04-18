package org.owl.flux.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class ConfigFileInstaller {
    private static final List<String> DEFAULT_FILES = List.of(
            "config.yml",
            "messages.yml",
            "templates.yml",
            "discord.yml",
            "mutedcmds.yml"
    );

    private ConfigFileInstaller() {
    }

    public static void installDefaults(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);

        ClassLoader classLoader = ConfigFileInstaller.class.getClassLoader();
        for (String fileName : DEFAULT_FILES) {
            Path target = dataDirectory.resolve(fileName);
            if (Files.exists(target)) {
                continue;
            }

            try (InputStream stream = classLoader.getResourceAsStream(fileName)) {
                if (stream == null) {
                    throw new IOException("Default resource missing from plugin JAR: " + fileName);
                }

                Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
