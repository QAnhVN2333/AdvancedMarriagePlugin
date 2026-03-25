package com.lyrinth.advancedmarriage.service;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlVersionMergeServiceTest {

    @Test
    void mergeMissingKeysShouldPreserveUserValuesAndAddMissingDefaults() throws Exception {
        Path tempDir = Files.createTempDirectory("advancedmarriage-yaml-merge-");
        File userFile = tempDir.resolve("messages.yml").toFile();

        String userYaml = """
                version: 1
                usage:
                  root: "custom root"
                custom_section:
                  hello: world
                """;
        String defaultYaml = """
                version: 2
                usage:
                  root: "default root"
                  marry: "default marry usage"
                error:
                  database: "db error"
                """;

        Files.writeString(userFile.toPath(), userYaml);

        YamlVersionMergeService service = new YamlVersionMergeService();
        boolean changed = service.mergeMissingKeys(userFile, new StringReader(defaultYaml));

        assertTrue(changed);

        String merged = Files.readString(userFile.toPath());
        assertTrue(merged.contains("version: 2"));
        assertTrue(merged.contains("root: custom root"));
        assertTrue(merged.contains("marry: default marry usage"));
        assertTrue(merged.contains("database: db error"));
        assertTrue(merged.contains("custom_section:"));
        assertTrue(merged.contains("hello: world"));
    }

    @Test
    void mergeMissingKeysShouldDoNothingWhenAlreadyUpToDate() throws Exception {
        Path tempDir = Files.createTempDirectory("advancedmarriage-yaml-merge-");
        File userFile = tempDir.resolve("config.yml").toFile();

        String yaml = """
                version: 2
                defaults:
                  chest_size: 36
                """;

        Files.writeString(userFile.toPath(), yaml);

        YamlVersionMergeService service = new YamlVersionMergeService();
        boolean changed = service.mergeMissingKeys(userFile, new StringReader(yaml));

        assertFalse(changed);
    }

    @Test
    void mergeMissingKeysShouldNotDowngradeVersionWhenUserHasNewerVersion() throws Exception {
        Path tempDir = Files.createTempDirectory("advancedmarriage-yaml-merge-");
        File userFile = tempDir.resolve("messages.yml").toFile();

        String userYaml = """
                version: 5
                usage:
                  root: "custom"
                """;
        String defaultYaml = """
                version: 2
                usage:
                  root: "default"
                """;

        Files.writeString(userFile.toPath(), userYaml);

        YamlVersionMergeService service = new YamlVersionMergeService();
        boolean changed = service.mergeMissingKeys(userFile, new StringReader(defaultYaml));

        assertFalse(changed);
        String merged = Files.readString(userFile.toPath());
        assertTrue(merged.contains("version: 5"));
    }
    @Test
    void mergeMissingKeysShouldAddNewSoundKeysWithoutOverwritingCustomSoundValues() throws Exception {

        Path tempDir = Files.createTempDirectory("advancedmarriage-yaml-merge-");
        File userFile = tempDir.resolve("config.yml").toFile();

        String userYaml = """
            version: 2
            sounds:
              teleport: ENTITY_ENDERMAN_TELEPORT
              countdown: none
            """;

        String defaultYaml = """
            version: 3
            sounds:
              teleport: ENTITY_ENDERMAN_TELEPORT
              countdown: BLOCK_NOTE_BLOCK_CHIME
              gui_click: UI_BUTTON_CLICK
              blocked: BLOCK_NOTE_BLOCK_BASS
            """;

        Files.writeString(userFile.toPath(), userYaml);

        YamlVersionMergeService service = new YamlVersionMergeService();
        boolean changed = service.mergeMissingKeys(userFile, new StringReader(defaultYaml));

        assertTrue(changed);

        String merged = Files.readString(userFile.toPath());
        assertTrue(merged.contains("blocked: BLOCK_NOTE_BLOCK_BASS"));
        assertTrue(merged.contains("gui_click: UI_BUTTON_CLICK"));
        assertTrue(merged.contains("countdown: none"));
        assertTrue(merged.contains("version: 3"));
    }
}
