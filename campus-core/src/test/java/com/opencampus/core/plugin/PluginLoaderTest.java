package com.opencampus.core.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldReturnEmptyWhenPluginDirectoryHasNoJar() {
        PluginLoader loader = new PluginLoader();

        assertTrue(loader.loadFromDirectory(temporaryDirectory).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenPluginDirectoryDoesNotExist() {
        PluginLoader loader = new PluginLoader();

        Path missingDirectory = temporaryDirectory.resolve("missing");

        assertTrue(loader.loadFromDirectory(missingDirectory).isEmpty());
    }
}