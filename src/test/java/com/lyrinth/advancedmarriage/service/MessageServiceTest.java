package com.lyrinth.advancedmarriage.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRenderMiniMessageTemplateToLegacyString() throws IOException {
        writeMessagesFile("""
                chat:
                  partner_format: "<green>[Partner]</green> {name}: {message}"
                """);

        MessageService messageService = createMessageService("auto");

        String rendered = messageService.renderLegacy("chat.partner_format", Map.of(
                "name", "Alice",
                "message", "Hello"
        ));

        // Ensure MiniMessage tags are parsed before being used in String-only APIs.
        assertFalse(rendered.contains("<green>"));
        assertTrue(rendered.contains("Alice: Hello"));
    }

    @Test
    void shouldRenderRawTemplateForBroadcast() throws IOException {
        writeMessagesFile("""
                chat:
                  partner_format: "&dFallback"
                """);

        MessageService messageService = createMessageService("minimessage");

        String rendered = messageService.renderTemplateLegacy(
                "<gold>Congrats {player1} and {player2}</gold>",
                Map.of("player1", "Alice", "player2", "Bob")
        );

        assertFalse(rendered.contains("<gold>"));
        assertTrue(rendered.contains("Congrats Alice and Bob"));
    }

    private MessageService createMessageService(String textRenderMode) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = new YamlConfiguration();
        config.set("fallback.text_render", textRenderMode);
        config.set("fallback.minimessage_fallback_to_legacy", true);

        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getConfig()).thenReturn(config);

        return new MessageService(plugin);
    }

    private void writeMessagesFile(String content) throws IOException {
        Files.writeString(tempDir.resolve("messages.yml"), content);
    }
}

