package com.opencampus.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldReadEnabledFlagAndRelativePluginDirectory() throws IOException {
        PluginConfig config = PluginConfig.load(write("plugins.directory=plugins\ncourse_query.enabled=true\n"));

        assertTrue(config.isEnabled("course_query"));
        assertFalse(config.isEnabled("announcement_query"));
        assertEquals(
                temporaryDirectory.resolve("plugins").toAbsolutePath().normalize(),
                config.pluginsDirectory()
        );
        assertTrue(config.warnings().isEmpty());
    }

    @Test
    void shouldTreatMissingFlagAsDisabled() throws IOException {
        PluginConfig config = PluginConfig.load(write("plugins.directory=plugins\n"));

        assertFalse(config.isEnabled("course_query"));
        assertFalse(config.isEnabled(null));
        assertFalse(config.isEnabled("  "));
    }

    @Test
    void shouldWarnOnInvalidBooleanAndTreatItAsDisabled() throws IOException {
        PluginConfig config = PluginConfig.load(write("course_query.enabled=maybe\n"));

        assertFalse(config.isEnabled("course_query"));
        assertEquals(1, config.warnings().size());
        assertTrue(config.warnings().get(0).contains("course_query.enabled"));
    }

    @Test
    void shouldAcceptAnnouncementAliasUntilServiceIdIsRenamed() throws IOException {
        PluginConfig alias = PluginConfig.load(write("announcement.enabled=true\n"));
        PluginConfig explicit = PluginConfig.load(write("announcement.enabled=true\nannouncement_query.enabled=false\n"));

        assertTrue(alias.isEnabled("announcement_query"));
        assertFalse(explicit.isEnabled("announcement_query"));
    }

    @Test
    void shouldKeepAbsolutePluginDirectory() throws IOException {
        Path absolute = temporaryDirectory.resolve("elsewhere").toAbsolutePath();

        // properties 会把反斜杠当转义符，Windows 路径用正斜杠写才不会变形。
        PluginConfig config = PluginConfig.load(
                write("plugins.directory=" + absolute.toString().replace('\\', '/') + "\n")
        );

        assertEquals(absolute.normalize(), config.pluginsDirectory());
    }

    @Test
    void shouldWarnWhenPathLooksLikeUnescapedWindowsPath() throws IOException {
        PluginConfig config = PluginConfig.load(write("plugins.directory=C:\\temp\\plugins\n"));

        assertEquals(1, config.warnings().size());
        assertTrue(config.warnings().get(0).contains("正斜杠"));
    }

    @Test
    void shouldFallBackToWorkingDirectoryDefaults() {
        PluginConfig config = PluginConfig.defaults();

        assertFalse(config.isEnabled("course_query"));
        assertTrue(config.source().isEmpty());
        assertEquals(Path.of("plugins").toAbsolutePath().normalize(), config.pluginsDirectory());
    }

    private Path write(String content) throws IOException {
        Path file = temporaryDirectory.resolve(PluginConfig.FILE_NAME);
        Files.writeString(file, content, StandardCharsets.UTF_8);

        return file;
    }
}
