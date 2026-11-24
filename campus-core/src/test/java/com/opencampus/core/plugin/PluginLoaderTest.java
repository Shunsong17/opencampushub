package com.opencampus.core.plugin;

import com.opencampus.api.CampusService;
import com.opencampus.core.plugin.fixture.StubService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoaderTest {

    private static final String SPI_FILE = "META-INF/services/com.opencampus.api.CampusService";

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

    @Test
    void shouldDiscoverServiceFromRealJar() throws IOException {
        jar("stub-plugin.jar", Map.of(SPI_FILE, StubService.class.getName()));

        try (PluginLoader loader = new PluginLoader()) {
            List<CampusService> services = loader.loadFromDirectory(temporaryDirectory);

            assertEquals(1, services.size());
            assertEquals(StubService.SERVICE_ID, services.get(0).getMetadata().serviceId());
        }
    }

    @Test
    void shouldIsolateBrokenJarAndKeepLoadingOthers() throws IOException {
        jar("broken-plugin.jar", Map.of(
                SPI_FILE,
                "com.opencampus.core.plugin.fixture.DoesNotExist"
        ));
        jar("good-plugin.jar", Map.of(SPI_FILE, StubService.class.getName()));

        try (PluginLoader loader = new PluginLoader()) {
            PluginLoader.Report report = loader.loadWithDiagnostics(temporaryDirectory);

            assertEquals(1, report.services().size());
            assertEquals(StubService.SERVICE_ID, report.services().get(0).getMetadata().serviceId());
            assertEquals(1, report.failures().size());
            assertTrue(report.failures().get(0).contains("broken-plugin.jar"));
        }
    }

    @Test
    void shouldReportUnreadableJarWithoutThrowing() throws IOException {
        Files.write(temporaryDirectory.resolve("corrupt.jar"), "not a jar".getBytes(StandardCharsets.UTF_8));

        try (PluginLoader loader = new PluginLoader()) {
            PluginLoader.Report report = loader.loadWithDiagnostics(temporaryDirectory);

            assertTrue(report.services().isEmpty());
            assertEquals(1, report.failures().size());
            assertTrue(report.failures().get(0).contains("corrupt.jar"));
        }
    }

    @Test
    void shouldCloseRepeatedlyWithoutError() {
        PluginLoader loader = new PluginLoader();

        assertDoesNotThrow(loader::close);
        assertDoesNotThrow(loader::close);
    }

    private void jar(String fileName, Map<String, String> entries) throws IOException {
        Path jarPath = temporaryDirectory.resolve(fileName);

        try (OutputStream output = Files.newOutputStream(jarPath);
             JarOutputStream jar = new JarOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
    }
}
