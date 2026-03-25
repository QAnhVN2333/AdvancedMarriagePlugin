package com.lyrinth.advancedmarriage.service;

import com.lyrinth.advancedmarriage.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class MessageService {
    private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final JavaPlugin plugin;
    private YamlConfiguration messages;
    private MiniMessage miniMessage;
    private boolean miniMessageFallbackToLegacy;
    private TextRenderMode textRenderMode;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        initAdventure();
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        miniMessageFallbackToLegacy = plugin.getConfig().getBoolean("fallback.minimessage_fallback_to_legacy", true);
        String modeRaw = plugin.getConfig().getString("fallback.text_render", "auto");
        textRenderMode = TextRenderMode.fromConfig(modeRaw);
    }

    public String get(String path) {
        return ColorUtil.colorize(getRaw(path));
    }

    public String format(String path, Map<String, String> placeholders) {
        String result = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public String renderLegacy(String path) {
        return renderLegacy(path, Collections.emptyMap());
    }

    public String renderLegacy(String path, Map<String, String> placeholders) {
        String rawMessage = getRaw(path);
        return renderTemplateLegacy(rawMessage, placeholders, path);
    }

    public String renderTemplateLegacy(String template, Map<String, String> placeholders) {
        return renderTemplateLegacy(template, placeholders, null);
    }

    public List<String> formatList(String path, Map<String, String> placeholders) {
        List<String> rawLines = messages.getStringList(path);
        if (rawLines.isEmpty()) {
            return List.of();
        }

        List<String> formatted = new ArrayList<>(rawLines.size());
        for (String rawLine : rawLines) {
            formatted.add(ColorUtil.colorize(applyPlaceholders(rawLine, placeholders)));
        }
        return formatted;
    }

    public void send(CommandSender sender, String path) {
        sendRawMessage(sender, path, getRaw(path), Collections.emptyMap());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sendRawMessage(sender, path, getRaw(path), placeholders);
    }

    public void sendMarriageRequest(Player target, String senderName) {
        Map<String, String> placeholders = Map.of("sender", senderName);

        send(target, "marry.request_received", placeholders);

        // Keep rich click buttons on a separate line for better readability.
        if (messages.contains("marry.request_received_hint")) {
            send(target, "marry.request_received_hint", placeholders);
            return;
        }

        if (messages.contains("marry.request_received_rich")) {
            send(target, "marry.request_received_rich", placeholders);
        }
    }

    private void initAdventure() {
        try {
            this.miniMessage = MiniMessage.miniMessage();
        } catch (Throwable ex) {
            this.miniMessage = null;
        }
    }

    private String getRaw(String path) {
        return messages.getString(path, "&cMissing message: " + path);
    }

    private void sendRawMessage(CommandSender sender, String path, String rawMessage, Map<String, String> placeholders) {
        String resolved = applyPlaceholders(rawMessage, placeholders);

        if (shouldUseMiniMessage(path, resolved)) {
            sendMiniMessage(sender, resolved);
            return;
        }

        sender.sendMessage(ColorUtil.colorize(resolved));
    }

    private void sendMiniMessage(CommandSender sender, String message) {
        if (miniMessage == null) {
            sender.sendMessage(stripMiniTags(message));
            return;
        }

        Component component = miniMessage.deserialize(message);

        // Paper and newer runtimes can expose CommandSender#sendMessage(Component).
        if (sendAdventureComponent(sender, component)) {
            return;
        }

        if (miniMessageFallbackToLegacy) {
            sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
            return;
        }

        sender.sendMessage(stripMiniTags(message));
    }

    private boolean sendAdventureComponent(CommandSender sender, Component component) {
        try {
            Method sendMethod = sender.getClass().getMethod("sendMessage", Component.class);
            sendMethod.invoke(sender, component);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean shouldUseMiniMessage(String path, String message) {
        if (path != null && path.startsWith("usage.")) {
            return false;
        }
        if (miniMessage == null || textRenderMode == TextRenderMode.LEGACY) {
            return false;
        }
        if (textRenderMode == TextRenderMode.MINIMESSAGE) {
            return true;
        }
        return MINI_TAG_PATTERN.matcher(message).find();
    }

    private String stripMiniTags(String message) {
        return MINI_TAG_PATTERN.matcher(message).replaceAll("");
    }

    private String renderTemplateLegacy(String template, Map<String, String> placeholders, String path) {
        if (template == null) {
            return "";
        }

        boolean useMiniMessage = shouldUseMiniMessage(path, template);
        String resolved = applyPlaceholders(template, placeholders, useMiniMessage);

        if (!useMiniMessage || miniMessage == null) {
            return ColorUtil.colorize(resolved);
        }

        try {
            Component component = miniMessage.deserialize(resolved);
            return LegacyComponentSerializer.legacySection().serialize(component);
        } catch (Exception ignored) {
            return ColorUtil.colorize(stripMiniTags(resolved));
        }
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        return applyPlaceholders(input, placeholders, false);
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders, boolean escapeMiniMessageTags) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (escapeMiniMessageTags && miniMessage != null) {
                // Escape user-provided values to avoid injecting MiniMessage tags.
                value = miniMessage.escapeTags(value);
            }
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }

    private enum TextRenderMode {
        AUTO,
        MINIMESSAGE,
        LEGACY;

        private static TextRenderMode fromConfig(String rawMode) {
            if (rawMode == null) {
                return AUTO;
            }
            return switch (rawMode.trim().toLowerCase()) {
                case "minimessage" -> MINIMESSAGE;
                case "legacy" -> LEGACY;
                default -> AUTO;
            };
        }
    }
}
