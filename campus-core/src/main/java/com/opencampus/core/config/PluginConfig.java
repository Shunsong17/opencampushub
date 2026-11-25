package com.opencampus.core.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public record PluginConfig(Path directory, Set<String> disabledServices) {
    public PluginConfig {
        disabledServices = Set.copyOf(disabledServices);
    }

    public static PluginConfig load(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Set<String> disabled = new HashSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.endsWith(".enabled")) continue;
            String value = properties.getProperty(key).strip();
            if (!value.equals("true") && !value.equals("false")) {
                throw new IOException(key + " 必须为 true 或 false");
            }
            if (value.equals("false")) disabled.add(key.substring(0, key.length() - ".enabled".length()));
        }
        String directory = properties.getProperty("plugins.directory", "plugins").strip();
        if (directory.isEmpty()) throw new IOException("插件目录不能为空");
        return new PluginConfig(file.toAbsolutePath().getParent().resolve(directory).normalize(), disabled);
    }
}
