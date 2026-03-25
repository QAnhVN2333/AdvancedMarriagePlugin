package com.lyrinth.advancedmarriage.command;

import com.lyrinth.advancedmarriage.gui.CoupleListGui;
import com.lyrinth.advancedmarriage.model.HomeLocation;
import com.lyrinth.advancedmarriage.model.Marriage;
import com.lyrinth.advancedmarriage.model.MarriageRequest;
import com.lyrinth.advancedmarriage.service.ChestBusyException;
import com.lyrinth.advancedmarriage.service.ChestService;
import com.lyrinth.advancedmarriage.service.ChestSessionService;
import com.lyrinth.advancedmarriage.service.ConfigService;
import com.lyrinth.advancedmarriage.service.HomeService;
import com.lyrinth.advancedmarriage.service.MarriageService;
import com.lyrinth.advancedmarriage.service.MarryCostService;
import com.lyrinth.advancedmarriage.service.MessageService;
import com.lyrinth.advancedmarriage.service.PreferenceService;
import com.lyrinth.advancedmarriage.service.SoundService;
import com.lyrinth.advancedmarriage.service.TeleportService;
import com.lyrinth.advancedmarriage.service.YamlVersionMergeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class MarriageCommand implements CommandExecutor, TabCompleter {
    private final MarriageService marriageService;
    private final HomeService homeService;
    private final ChestService chestService;
    private final ChestSessionService chestSessionService;
    private final PreferenceService preferenceService;
    private final MessageService messageService;
    private final ConfigService configService;
    private final TeleportService teleportService;
    private final MarryCostService marryCostService;
    private final CoupleListGui coupleListGui;
    private final SoundService soundService;
    private final YamlVersionMergeService yamlVersionMergeService;
    private final JavaPlugin plugin;

    public MarriageCommand(
            MarriageService marriageService,
            HomeService homeService,
            ChestService chestService,
            ChestSessionService chestSessionService,
            PreferenceService preferenceService,
            MessageService messageService,
            ConfigService configService,
            TeleportService teleportService,
            MarryCostService marryCostService,
            CoupleListGui coupleListGui,
            SoundService soundService,
            YamlVersionMergeService yamlVersionMergeService,
            JavaPlugin plugin
    ) {
        this.marriageService = marriageService;
        this.homeService = homeService;
        this.chestService = chestService;
        this.chestSessionService = chestSessionService;
        this.preferenceService = preferenceService;
        this.messageService = messageService;
        this.configService = configService;
        this.teleportService = teleportService;
        this.marryCostService = marryCostService;
        this.coupleListGui = coupleListGui;
        this.soundService = soundService;
        this.yamlVersionMergeService = yamlVersionMergeService;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (isAllCommandsBlocked(sender)) {
            return true;
        }

        if (args.length == 0) {
            messageService.send(sender, "usage.root");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        try {
            return switch (subCommand) {
                case "list" -> handleList(sender);
                case "display" -> handleDisplay(sender, args);
                case "marry" -> handleMarry(sender, args);
                case "accept" -> handleAccept(sender, args);
                case "deny" -> handleDeny(sender, args);
                case "cancel" -> handleCancel(sender);
                case "divorce" -> handleDivorce(sender, args);
                case "chat" -> handleChat(sender);
                case "home" -> handleHome(sender);
                case "sethome" -> handleSetHome(sender);
                case "delhome" -> handleDelHome(sender);
                case "tp" -> handleTp(sender);
                case "tptoggle" -> handleTpToggle(sender);
                case "pvptoggle" -> handlePvpToggle(sender);
                case "chest" -> handleChest(sender);
                case "seen" -> handleSeen(sender);
                case "reload" -> handleReload(sender);
                default -> {
                    messageService.send(sender, "usage.root");
                    yield true;
                }
            };
        } catch (SQLException | IOException ex) {
            messageService.send(sender, "error.database");
            return true;
        }
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }
        coupleListGui.open(player, 0);
        return true;
    }

    private boolean handleDisplay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }
        if (args.length < 2) {
            messageService.send(player, "usage.display");
            return true;
        }

        Optional<Marriage> marriageOptional = marriageService.getMarriage(player.getUniqueId());
        if (marriageOptional.isEmpty()) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        OfflinePlayer target = resolvePlayerByName(args[1]);
        if (target == null) {
            messageService.send(player, "marry.target_offline");
            return true;
        }

        Marriage marriage = marriageOptional.get();
        if (!marriage.includes(target.getUniqueId())) {
            messageService.send(player, "display.must_be_partner");
            return true;
        }

        // Keep GUI behavior consistent by storing the chosen avatar for both partners.
        preferenceService.setDisplayUuid(marriage.getPlayerA(), target.getUniqueId());
        preferenceService.setDisplayUuid(marriage.getPlayerB(), target.getUniqueId());
        messageService.send(player, "display.updated");
        return true;
    }

    private boolean handleMarry(CommandSender sender, String[] args) throws SQLException {
        if (args.length == 3) {
            return handlePriestMarry(sender, args[1], args[2]);
        }

        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        if (args.length < 2) {
            messageService.send(player, "usage.marry");
            return true;
        }

        if (!marryCostService.isAvailable()) {
            messageService.send(player, "marry.cost_provider_unavailable");
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }

        if (!marryCostService.canAfford(player.getUniqueId())) {
            messageService.send(player, "marry.insufficient_funds", Map.of(
                    "amount", marryCostService.getFormattedAmount(),
                    "currency", marryCostService.getCurrencyName()
            ));
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messageService.send(player, "marry.target_offline");
            return true;
        }

        if (player.getUniqueId().equals(target.getUniqueId())) {
            messageService.send(player, "marry.self");
            return true;
        }

        if (marriageService.isMarried(player.getUniqueId()) || marriageService.isMarried(target.getUniqueId())) {
            messageService.send(player, "marriage.already_married");
            return true;
        }

        if (marriageService.isInDivorceCooldown(player.getUniqueId())) {
            messageService.send(player, "divorce.cooldown");
            return true;
        }

        Optional<MarriageRequest> outgoingRequestOptional = marriageService.getRequestManager().getPendingOutgoing(player.getUniqueId());
        if (outgoingRequestOptional.isPresent()) {
            String pendingTargetName = Bukkit.getOfflinePlayer(outgoingRequestOptional.get().getReceiver()).getName();
            if (pendingTargetName == null) {
                pendingTargetName = outgoingRequestOptional.get().getReceiver().toString();
            }
            messageService.send(player, "marry.request_already_pending", Map.of("target", pendingTargetName));
            return true;
        }

        marriageService.getRequestManager().create(player.getUniqueId(), target.getUniqueId(), configService.getMarryRequestTimeoutSeconds());
        messageService.send(player, "marry.request_sent", Map.of("target", target.getName()));
        messageService.sendMarriageRequest(target, player.getName());
        soundService.play(player, SoundService.REQUEST_SENT);
        soundService.play(target, SoundService.REQUEST_RECEIVED);
        return true;
    }

    private boolean handlePriestMarry(CommandSender sender, String nameA, String nameB) throws SQLException {
        if (!sender.hasPermission("advancedmarriage.priest")) {
            messageService.send(sender, "error.no_permission");
            return true;
        }

        OfflinePlayer playerA = resolvePlayerByName(nameA);
        OfflinePlayer playerB = resolvePlayerByName(nameB);
        if (playerA == null || playerB == null) {
            messageService.send(sender, "marry.target_offline");
            return true;
        }

        if (marriageService.isMarried(playerA.getUniqueId()) || marriageService.isMarried(playerB.getUniqueId())) {
            messageService.send(sender, "marriage.already_married");
            return true;
        }

        marriageService.createMarriage(playerA.getUniqueId(), playerB.getUniqueId(), configService.getServerId());
        messageService.send(sender, "marriage.success_admin", Map.of("player1", nameA, "player2", nameB));

        String playerAName = playerA.getName() == null ? nameA : playerA.getName();
        String playerBName = playerB.getName() == null ? nameB : playerB.getName();
        broadcastMarriage(playerAName, playerBName);
        return true;
    }

    private boolean handleAccept(CommandSender sender, String[] args) throws SQLException {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        Optional<MarriageRequest> requestOptional = resolveIncomingRequest(player, args, "usage.accept");
        if (requestOptional.isEmpty()) {
            return true;
        }

        MarriageRequest request = requestOptional.get();
        if (marriageService.isMarried(player.getUniqueId()) || marriageService.isMarried(request.getSender())) {
            messageService.send(player, "marriage.already_married");
            marriageService.getRequestManager().remove(player.getUniqueId(), request.getSender());
            return true;
        }

        if (!marryCostService.isAvailable()) {
            messageService.send(player, "marry.cost_provider_unavailable");
            Player requester = Bukkit.getPlayer(request.getSender());
            if (requester != null) {
                messageService.send(requester, "marry.cost_provider_unavailable");
                soundService.play(requester, SoundService.BLOCKED);
            }
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }

        if (!marryCostService.charge(request.getSender())) {
            String senderName = Bukkit.getOfflinePlayer(request.getSender()).getName();
            if (senderName == null) {
                senderName = request.getSender().toString();
            }

            messageService.send(player, "marry.sender_insufficient_funds", Map.of("sender", senderName));
            Player requester = Bukkit.getPlayer(request.getSender());
            if (requester != null) {
                messageService.send(requester, "marry.insufficient_funds", Map.of(
                        "amount", marryCostService.getFormattedAmount(),
                        "currency", marryCostService.getCurrencyName()
                ));
                soundService.play(requester, SoundService.BLOCKED);
            }
            marriageService.getRequestManager().remove(player.getUniqueId(), request.getSender());
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }

        marriageService.createMarriage(request.getSender(), player.getUniqueId(), configService.getServerId());

        // Clear stale requests related to both players because they are now married.
        marriageService.getRequestManager().clearRequestsForPlayer(player.getUniqueId());
        marriageService.getRequestManager().clearRequestsForPlayer(request.getSender());

        Player senderPlayer = Bukkit.getPlayer(request.getSender());
        if (senderPlayer != null) {
            messageService.send(senderPlayer, "marriage.success", Map.of("partner", player.getName()));
            soundService.play(senderPlayer, SoundService.MARRY_ACCEPTED);
        }

        String senderName = Bukkit.getOfflinePlayer(request.getSender()).getName();
        if (senderName == null) {
            senderName = request.getSender().toString();
        }
        messageService.send(player, "marriage.success", Map.of("partner", senderName));
        soundService.play(player, SoundService.MARRY_ACCEPTED);

        broadcastMarriage(senderName, player.getName());
        return true;
    }

    private boolean handleDeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        Optional<MarriageRequest> requestOptional = resolveIncomingRequest(player, args, "usage.deny");
        if (requestOptional.isEmpty()) {
            return true;
        }

        MarriageRequest request = requestOptional.get();
        marriageService.getRequestManager().remove(player.getUniqueId(), request.getSender());

        String senderName = Bukkit.getOfflinePlayer(request.getSender()).getName();
        if (senderName == null) {
            senderName = request.getSender().toString();
        }
        messageService.send(player, "marry.request_denied", Map.of("sender", senderName));
        soundService.play(player, SoundService.MARRY_REJECTED);

        Player senderPlayer = Bukkit.getPlayer(request.getSender());
        if (senderPlayer != null) {
            messageService.send(senderPlayer, "marry.request_denied_by_target", Map.of("target", player.getName()));
            soundService.play(senderPlayer, SoundService.MARRY_REJECTED);
        }

        return true;
    }

    private boolean handleCancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        Optional<MarriageRequest> outgoingRequestOptional = marriageService.getRequestManager().getPendingOutgoing(player.getUniqueId());
        if (outgoingRequestOptional.isEmpty()) {
            messageService.send(player, "marry.no_outgoing_request");
            return true;
        }

        MarriageRequest request = outgoingRequestOptional.get();
        marriageService.getRequestManager().cancelOutgoing(player.getUniqueId());

        String targetName = Bukkit.getOfflinePlayer(request.getReceiver()).getName();
        if (targetName == null) {
            targetName = request.getReceiver().toString();
        }
        messageService.send(player, "marry.request_cancelled", Map.of("target", targetName));
        return true;
    }

    private boolean handleDivorce(CommandSender sender, String[] args) throws SQLException, IOException {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        if (args.length < 2 || !"confirm".equalsIgnoreCase(args[1])) {
            messageService.send(player, "usage.divorce_confirm");
            return true;
        }

        Optional<Marriage> marriageOptional = marriageService.getMarriage(player.getUniqueId());
        if (marriageOptional.isEmpty()) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        Marriage marriage = marriageOptional.get();
        if (configService.isCrossServerChestEnabled()) {
            Optional<ChestService.ChestLease> leaseOptional = chestService.tryAcquireLease(
                    marriage.getId(),
                    configService.getServerId(),
                    configService.getChestSize(),
                    configService.getChestLockTtlSeconds() * 1000L,
                    false
            );
            if (leaseOptional.isEmpty()) {
                messageService.send(player, "chest.in_use");
                soundService.play(player, SoundService.BLOCKED);
                return true;
            }

            ChestService.ChestLease lease = leaseOptional.get();
            try {
                if (!chestService.isEmptyUnderLease(marriage.getId(), lease.token(), configService.getChestSize())) {
                    messageService.send(player, "divorce.chest_not_empty");
                    return true;
                }
            } finally {
                chestService.releaseLease(marriage.getId(), lease.token());
            }
        } else {
            if (!chestService.isEmpty(marriage.getId())) {
                messageService.send(player, "divorce.chest_not_empty");
                return true;
            }
        }

        UUID partnerUuid = marriage.getPartner(player.getUniqueId());
        if (marriageService.divorce(player.getUniqueId())) {
            marriageService.addDivorceCooldown(player.getUniqueId(), configService.getDivorceCooldownMinutes());
            if (partnerUuid != null) {
                marriageService.addDivorceCooldown(partnerUuid, configService.getDivorceCooldownMinutes());
                Player partner = Bukkit.getPlayer(partnerUuid);
                if (partner != null) {
                    messageService.send(partner, "divorce.success");
                }
            }
            messageService.send(player, "divorce.success");
        }

        return true;
    }

    private boolean handleChat(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        if (!marriageService.isMarried(player.getUniqueId())) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        boolean current = preferenceService.isPartnerChatEnabled(player.getUniqueId());
        preferenceService.setPartnerChatEnabled(player.getUniqueId(), !current);
        messageService.send(player, !current ? "chat.enabled" : "chat.disabled");
        soundService.play(player, SoundService.FEATURE_TOGGLED);
        return true;
    }

    private boolean handleHome(CommandSender sender) throws SQLException {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        if (!configService.isFeatureEnabled("home") || !player.hasPermission("advancedmarriage.home")) {
            messageService.send(player, "error.feature_disabled");
            return true;
        }

        if (!configService.getHomeFilter().isAllowed(player.getWorld().getName())) {
            messageService.send(player, "home.world_blocked");
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }

        Optional<Marriage> marriageOptional = marriageService.getMarriage(player.getUniqueId());
        if (marriageOptional.isEmpty()) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        Optional<HomeLocation> homeOptional = homeService.getHome(marriageOptional.get().getId());
        if (homeOptional.isEmpty()) {
            messageService.send(player, "home.not_set");
            return true;
        }

        HomeLocation home = homeOptional.get();
        if (!configService.getServerId().equalsIgnoreCase(home.getServerId())) {
            messageService.send(player, "home.wrong_server");
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }

        if (!configService.getHomeFilter().isAllowed(home.getWorldName())) {
            messageService.send(player, "home.world_blocked");
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }

        World world = Bukkit.getWorld(home.getWorldName());
        if (world == null) {
            messageService.send(player, "home.world_missing");
            return true;
        }

        Location target = new Location(world, home.getX(), home.getY(), home.getZ(), home.getYaw(), home.getPitch());
        teleportService.teleportWithCountdown(player, target, () -> messageService.send(player, "home.teleported"));
        return true;
    }

    private boolean handleSetHome(CommandSender sender) throws SQLException {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        if (!configService.isFeatureEnabled("home") || !player.hasPermission("advancedmarriage.home")) {
            messageService.send(player, "error.feature_disabled");
            return true;
        }

        if (!configService.getHomeFilter().isAllowed(player.getWorld().getName())) {
            messageService.send(player, "home.world_blocked");
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }

        Optional<Marriage> marriageOptional = marriageService.getMarriage(player.getUniqueId());
        if (marriageOptional.isEmpty()) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        homeService.setHome(marriageOptional.get().getId(), configService.getServerId(), player.getLocation());
        messageService.send(player, "home.set");
        return true;
    }

    private boolean handleDelHome(CommandSender sender) throws SQLException {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        if (!configService.isFeatureEnabled("home") || !player.hasPermission("advancedmarriage.home")) {
            messageService.send(player, "error.feature_disabled");
            return true;
        }

        Optional<Marriage> marriageOptional = marriageService.getMarriage(player.getUniqueId());
        if (marriageOptional.isEmpty()) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        if (homeService.deleteHome(marriageOptional.get().getId())) {
            messageService.send(player, "home.deleted");
        } else {
            messageService.send(player, "home.not_set");
        }
        return true;
    }

    private boolean handleTp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        if (!configService.isFeatureEnabled("tp") || !player.hasPermission("advancedmarriage.tp")) {
            messageService.send(player, "error.feature_disabled");
            return true;
        }

        Optional<Marriage> marriageOptional = marriageService.getMarriage(player.getUniqueId());
        if (marriageOptional.isEmpty()) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        Optional<UUID> partnerUuidOptional = marriageService.getPartner(player.getUniqueId());
        if (partnerUuidOptional.isEmpty()) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        Player partner = Bukkit.getPlayer(partnerUuidOptional.get());
        if (partner == null) {
            messageService.send(player, "seen.partner_offline");
            return true;
        }

        if (!preferenceService.isTpAllowPartner(partner.getUniqueId())) {
            messageService.send(player, "tp.partner_blocked");
            return true;
        }

        String worldName = player.getWorld().getName();
        String partnerWorldName = partner.getWorld().getName();
        if (!configService.getTpFilter().isAllowed(worldName) || !configService.getTpFilter().isAllowed(partnerWorldName)) {
            messageService.send(player, "tp.world_blocked");
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }

        teleportService.teleportWithCountdown(player, partner.getLocation(), () -> messageService.send(player, "tp.success"));
        return true;
    }

    private boolean handleTpToggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        if (!configService.isFeatureEnabled("tp")) {
            messageService.send(player, "error.feature_disabled");
            return true;
        }

        if (!marriageService.isMarried(player.getUniqueId())) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        boolean current = preferenceService.isTpAllowPartner(player.getUniqueId());
        preferenceService.setTpAllowPartner(player.getUniqueId(), !current);
        messageService.send(player, !current ? "tp.enabled" : "tp.disabled");
        soundService.play(player, SoundService.FEATURE_TOGGLED);
        return true;
    }

    private boolean handlePvpToggle(CommandSender sender) throws SQLException {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        if (!configService.isFeatureEnabled("pvp_toggle")) {
            messageService.send(player, "error.feature_disabled");
            return true;
        }

        Optional<Marriage> marriageOptional = marriageService.getMarriage(player.getUniqueId());
        if (marriageOptional.isEmpty()) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        Marriage marriage = marriageOptional.get();
        marriageService.setPvpEnabled(marriage, !marriage.isPvpEnabled());
        messageService.send(player, marriage.isPvpEnabled() ? "pvp.enabled" : "pvp.disabled");
        return true;
    }

    private boolean handleChest(CommandSender sender) throws SQLException, IOException {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        if (!configService.isFeatureEnabled("chest") || !player.hasPermission("advancedmarriage.chest")) {
            messageService.send(player, "error.feature_disabled");
            return true;
        }

        Optional<Marriage> marriageOptional = marriageService.getMarriage(player.getUniqueId());
        if (marriageOptional.isEmpty()) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        Marriage marriage = marriageOptional.get();
        if (!configService.isCrossServerChestEnabled()) {
            String existingServerId = chestService.getChestServerId(marriage.getId());
            if (existingServerId != null && !existingServerId.equalsIgnoreCase(configService.getServerId())) {
                messageService.send(player, "chest.wrong_server");
                soundService.play(player, SoundService.BLOCKED);
                return true;
            }
        }

        if (!configService.getChestFilter().isAllowed(player.getWorld().getName())) {
            messageService.send(player, "chest.world_blocked");
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }

        try {
            player.openInventory(chestSessionService.openInventory(
                    marriage.getId(),
                    messageService.renderLegacy("chest.title"),
                    configService.getChestSize()
            ));
        } catch (ChestBusyException ex) {
            messageService.send(player, "chest.busy");
            soundService.play(player, SoundService.BLOCKED);
        }
        return true;
    }

    private boolean handleSeen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error.player_only");
            return true;
        }

        Optional<UUID> partnerUuidOptional = marriageService.getPartner(player.getUniqueId());
        if (partnerUuidOptional.isEmpty()) {
            messageService.send(player, "marriage.not_married");
            return true;
        }

        UUID partnerUuid = partnerUuidOptional.get();
        Player onlinePartner = Bukkit.getPlayer(partnerUuid);
        if (onlinePartner != null) {
            messageService.send(player, "seen.online", Map.of("partner", onlinePartner.getName()));
            return true;
        }

        OfflinePlayer offlinePartner = Bukkit.getOfflinePlayer(partnerUuid);
        long lastPlayed = offlinePartner.getLastPlayed();
        long minutes = Duration.between(Instant.ofEpochMilli(lastPlayed), Instant.now()).toMinutes();
        String name = offlinePartner.getName() == null ? partnerUuid.toString() : offlinePartner.getName();
        messageService.send(player, "seen.offline", Map.of("partner", name, "minutes", String.valueOf(minutes)));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("advancedmarriage.admin")) {
            messageService.send(sender, "error.no_permission");
            return true;
        }

        try {
            // Reload command also upgrades missing keys from the latest bundled YAML files.
            yamlVersionMergeService.mergeFromPluginResource(plugin, "config.yml");
            yamlVersionMergeService.mergeFromPluginResource(plugin, "messages.yml");
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to merge YAML defaults on reload: " + ex.getMessage());
            messageService.send(sender, "error.database");
            return true;
        }

        plugin.reloadConfig();
        messageService.reload();
        messageService.send(sender, "admin.reloaded");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("list", "display", "marry", "accept", "deny", "cancel", "divorce", "chat", "home", "sethome", "delhome", "tp", "tptoggle", "pvptoggle", "chest", "seen", "reload")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && ("marry".equalsIgnoreCase(args[0]) || "display".equalsIgnoreCase(args[0]))) {
            List<String> result = new ArrayList<>();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    result.add(onlinePlayer.getName());
                }
            }
            return result;
        }

        if (args.length == 2 && ("accept".equalsIgnoreCase(args[0]) || "deny".equalsIgnoreCase(args[0])) && sender instanceof Player player) {
            return marriageService.getRequestManager().getIncoming(player.getUniqueId())
                    .stream()
                    .map(MarriageRequest::getSender)
                    .map(senderUuid -> Bukkit.getOfflinePlayer(senderUuid).getName())
                    .filter(name -> name != null && name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    private void broadcastMarriage(String playerOneName, String playerTwoName) {
        if (!configService.isMarryBroadcastEnabled()) {
            return;
        }

        List<String> broadcastMessages = configService.getMarryBroadcastMessages();
        if (broadcastMessages.isEmpty()) {
            return;
        }

        String template = broadcastMessages.get(ThreadLocalRandom.current().nextInt(broadcastMessages.size()));
        String renderedMessage = messageService.renderTemplateLegacy(template, Map.of(
                "player1", playerOneName,
                "player2", playerTwoName
        ));
        Bukkit.broadcastMessage(renderedMessage);
    }

    private Optional<MarriageRequest> resolveIncomingRequest(Player receiver, String[] args, String usagePath) {
        if (args.length > 2) {
            messageService.send(receiver, usagePath);
            return Optional.empty();
        }

        List<MarriageRequest> incomingRequests = marriageService.getRequestManager().getIncoming(receiver.getUniqueId());
        if (incomingRequests.isEmpty()) {
            messageService.send(receiver, "marry.no_request");
            return Optional.empty();
        }

        if (args.length == 2) {
            OfflinePlayer senderPlayer = resolvePlayerByName(args[1]);
            if (senderPlayer == null) {
                messageService.send(receiver, "marry.request_player_not_found");
                return Optional.empty();
            }

            Optional<MarriageRequest> request = marriageService.getRequestManager().getIncoming(receiver.getUniqueId(), senderPlayer.getUniqueId());
            if (request.isEmpty()) {
                messageService.send(receiver, "marry.no_request_from", Map.of("sender", args[1]));
                return Optional.empty();
            }
            return request;
        }

        if (incomingRequests.size() > 1) {
            String senders = incomingRequests.stream()
                    .map(MarriageRequest::getSender)
                    .map(senderUuid -> Bukkit.getOfflinePlayer(senderUuid).getName())
                    .filter(name -> name != null && !name.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(", "));

            if (senders.isEmpty()) {
                senders = String.valueOf(incomingRequests.size());
            }

            messageService.send(receiver, "marry.multiple_requests", Map.of("senders", senders));
            return Optional.empty();
        }

        return Optional.of(incomingRequests.get(0));
    }

    private OfflinePlayer resolvePlayerByName(String name) {
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(name)) {
                return offlinePlayer;
            }
        }

        return null;
    }

    private boolean isAllCommandsBlocked(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!configService.getAllCommandsFilter().isAllowed(player.getWorld().getName())) {
            messageService.send(player, "error.commands_blocked_world");
            soundService.play(player, SoundService.BLOCKED);
            return true;
        }
        return false;
    }
}
