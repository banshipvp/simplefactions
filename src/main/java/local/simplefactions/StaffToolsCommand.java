package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class StaffToolsCommand implements CommandExecutor, TabCompleter, Listener {

    private final PlayerRankManager rankManager;
    private final ModerationManager moderationManager;

    public StaffToolsCommand(PlayerRankManager rankManager, ModerationManager moderationManager) {
        this.rankManager = rankManager;
        this.moderationManager = moderationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        return switch (name) {
            case "staffhelp" -> handleStaffHelp(sender);
            case "rankset" -> handleRankSet(sender, args);
            case "v", "vanish" -> handleVanish(sender);
            case "tempmute" -> handleTempMute(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            case "mute" -> handleMute(sender, args);
            case "tempban" -> handleTempBan(sender, args);
            case "ban" -> handleBan(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "warn" -> handleWarn(sender, args);
            case "tpto" -> handleTpTo(sender, args);
            case "freeze" -> handleFreeze(sender, args);
            case "unfreeze" -> handleUnfreeze(sender, args);
            default -> false;
        };
    }

    private boolean handleStaffHelp(CommandSender sender) {
        if (!sender.hasPermission("simplefactions.staff.help")
                && !sender.hasPermission("simplefactions.staff.helper")
                && !sender.hasPermission("simplefactions.staff.mod")
                && !sender.hasPermission("simplefactions.rankset")
                && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "---- Staff Commands ----");
        boolean shownAny = false;
        boolean shownRank = false;
        boolean shownModeration = false;
        boolean shownUtility = false;

        if (canUse(sender, "simplefactions.rankset")) {
            if (!shownRank) {
                sender.sendMessage(ChatColor.AQUA + "[Rank]");
                shownRank = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/rankset <player> <rank>" + ChatColor.GRAY + " - Set server rank");
            shownAny = true;
        }

        if (canUse(sender, "simplefactions.staff.vanish")) {
            if (!shownUtility) {
                sender.sendMessage(ChatColor.AQUA + "[Utility]");
                shownUtility = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/v, /vanish" + ChatColor.GRAY + " - Toggle vanish");
            shownAny = true;
        }

        // Helper-tier moderation commands
        if (canUse(sender, "simplefactions.staff.tempmute")) {
            if (!shownModeration) {
                sender.sendMessage(ChatColor.AQUA + "[Moderation]");
                shownModeration = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/tempmute <player> <duration> [reason]" + ChatColor.GRAY + " - Temp mute");
            shownAny = true;
        }
        if (canUse(sender, "simplefactions.staff.unmute")) {
            if (!shownModeration) {
                sender.sendMessage(ChatColor.AQUA + "[Moderation]");
                shownModeration = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/unmute <player>" + ChatColor.GRAY + " - Remove mute");
            shownAny = true;
        }
        if (canUse(sender, "simplefactions.staff.tempban")) {
            if (!shownModeration) {
                sender.sendMessage(ChatColor.AQUA + "[Moderation]");
                shownModeration = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/tempban <player> <duration> [reason]" + ChatColor.GRAY + " - Temp ban");
            shownAny = true;
        }
        if (canUse(sender, "simplefactions.staff.unban")) {
            if (!shownModeration) {
                sender.sendMessage(ChatColor.AQUA + "[Moderation]");
                shownModeration = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/unban <player>" + ChatColor.GRAY + " - Remove ban");
            shownAny = true;
        }
        if (canUse(sender, "simplefactions.staff.warn")) {
            if (!shownModeration) {
                sender.sendMessage(ChatColor.AQUA + "[Moderation]");
                shownModeration = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/warn <player> <message>" + ChatColor.GRAY + " - Warn player");
            shownAny = true;
        }

        // Mod-tier additions
        if (canUse(sender, "simplefactions.staff.mute")) {
            if (!shownModeration) {
                sender.sendMessage(ChatColor.AQUA + "[Moderation]");
                shownModeration = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/mute <player>" + ChatColor.GRAY + " - Permanent mute");
            shownAny = true;
        }
        if (canUse(sender, "simplefactions.staff.ban")) {
            if (!shownModeration) {
                sender.sendMessage(ChatColor.AQUA + "[Moderation]");
                shownModeration = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/ban <player> [reason]" + ChatColor.GRAY + " - Permanent ban");
            shownAny = true;
        }
        if (canUse(sender, "simplefactions.staff.freeze")) {
            if (!shownModeration) {
                sender.sendMessage(ChatColor.AQUA + "[Moderation]");
                shownModeration = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/freeze <player>" + ChatColor.GRAY + " - Freeze toggle");
            shownAny = true;
        }
        if (canUse(sender, "simplefactions.staff.unfreeze")) {
            if (!shownModeration) {
                sender.sendMessage(ChatColor.AQUA + "[Moderation]");
                shownModeration = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/unfreeze <player>" + ChatColor.GRAY + " - Remove freeze");
            shownAny = true;
        }
        if (canUse(sender, "simplefactions.staff.tpto")) {
            if (!shownUtility) {
                sender.sendMessage(ChatColor.AQUA + "[Utility]");
                shownUtility = true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/tpto <player>" + ChatColor.GRAY + " - Teleport to player");
            shownAny = true;
        }

        if (!shownAny) {
            sender.sendMessage(ChatColor.GRAY + "No staff commands are currently available to you.");
        }

        return true;
    }

    private boolean canUse(CommandSender sender, String permission) {
        return sender.isOp() || sender.hasPermission(permission);
    }

    private boolean handleRankSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.rankset")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rankset <player> <rank>");
            sender.sendMessage(ChatColor.GRAY + "Ranks: " + Arrays.stream(PlayerRank.values())
                    .filter(rank -> rank != PlayerRank.DEFAULT)
                    .map(PlayerRank::getGroupId)
                    .collect(Collectors.joining(", ")));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null) {
            sender.sendMessage(ChatColor.RED + "Unknown player: " + args[0]);
            return true;
        }

        PlayerRank rank = PlayerRank.fromInput(args[1]);
        if (rank == PlayerRank.DEFAULT) {
            sender.sendMessage(ChatColor.RED + "Unknown rank: " + args[1]);
            return true;
        }

        rankManager.setRank(target.getUniqueId(), rank);
        sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + " to " + rank.getDisplayName() + ChatColor.GREEN + ".");

        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(ChatColor.GREEN + "Your rank is now " + rank.getDisplayName() + ChatColor.GREEN + ".");
        }
        return true;
    }

    private boolean handleVanish(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (!sender.hasPermission("simplefactions.staff.vanish")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        boolean enabled = moderationManager.toggleVanish(player);
        player.sendMessage(enabled
                ? ChatColor.GREEN + "Vanish enabled."
                : ChatColor.YELLOW + "Vanish disabled.");
        return true;
    }

    private boolean handleTempMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.staff.tempmute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tempmute <player> <duration> [reason]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online.");
            return true;
        }

        Long duration = ModerationManager.parseDurationMillis(args[1]);
        if (duration == null || duration <= 0L) {
            sender.sendMessage(ChatColor.RED + "Invalid duration. Use 30m, 2h, 1d, etc.");
            return true;
        }

        moderationManager.tempMute(target.getUniqueId(), duration);
        String reason = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "No reason provided";

        target.sendMessage(ChatColor.RED + "You were temporarily muted for "
                + ModerationManager.formatDuration(duration) + ". Reason: " + reason);
        sender.sendMessage(ChatColor.GREEN + "Muted " + target.getName() + " for "
                + ModerationManager.formatDuration(duration) + ".");
        return true;
    }

    private boolean handleMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.staff.mute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /mute <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online.");
            return true;
        }

        moderationManager.mute(target.getUniqueId());
        target.sendMessage(ChatColor.RED + "You were muted permanently.");
        sender.sendMessage(ChatColor.GREEN + "Muted " + target.getName() + " permanently.");
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.staff.unmute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unmute <player>");
            return true;
        }

        Player onlineTarget = Bukkit.getPlayerExact(args[0]);
        if (onlineTarget != null) {
            moderationManager.unmute(onlineTarget.getUniqueId());
            onlineTarget.sendMessage(ChatColor.GREEN + "You have been unmuted.");
            sender.sendMessage(ChatColor.GREEN + "Unmuted " + onlineTarget.getName() + ".");
            return true;
        }

        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(args[0]);
        if (offlineTarget.getUniqueId() == null) {
            sender.sendMessage(ChatColor.RED + "Unknown player: " + args[0]);
            return true;
        }

        moderationManager.unmute(offlineTarget.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "Unmuted " + (offlineTarget.getName() == null ? args[0] : offlineTarget.getName()) + ".");
        return true;
    }

    private boolean handleTempBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.staff.tempban")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tempban <player> <duration> [reason]");
            return true;
        }

        Long duration = ModerationManager.parseDurationMillis(args[1]);
        if (duration == null || duration <= 0L) {
            sender.sendMessage(ChatColor.RED + "Invalid duration. Use 30m, 2h, 1d, etc.");
            return true;
        }

        String reason = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "No reason provided";
        moderationManager.tempBan(args[0], duration, reason, sender.getName());

        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            online.kickPlayer("Temporarily banned for " + ModerationManager.formatDuration(duration) + "\nReason: " + reason);
        }

        sender.sendMessage(ChatColor.GREEN + "Tempbanned " + args[0] + " for "
                + ModerationManager.formatDuration(duration) + ".");
        return true;
    }

    private boolean handleBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.staff.ban")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /ban <player> [reason]");
            return true;
        }

        String reason = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "No reason provided";
        moderationManager.ban(args[0], reason, sender.getName());

        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            online.kickPlayer("Banned\nReason: " + reason);
        }

        sender.sendMessage(ChatColor.GREEN + "Banned " + args[0] + ".");
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.staff.unban")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unban <player>");
            return true;
        }

        moderationManager.unban(args[0]);
        sender.sendMessage(ChatColor.GREEN + "Unbanned " + args[0] + ".");
        return true;
    }

    private boolean handleWarn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.staff.warn")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /warn <player> <message>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online.");
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        target.sendMessage(ChatColor.RED + "[Warning] " + ChatColor.YELLOW + message);
        sender.sendMessage(ChatColor.GREEN + "Warned " + target.getName() + ".");
        return true;
    }

    private boolean handleTpTo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (!sender.hasPermission("simplefactions.staff.tpto")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /tpto <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online.");
            return true;
        }

        player.teleport(target.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName() + ".");
        return true;
    }

    private boolean handleFreeze(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.staff.freeze")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /freeze <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online.");
            return true;
        }

        boolean nowFrozen = moderationManager.toggleFreeze(target.getUniqueId());
        if (nowFrozen) {
            target.sendMessage(ChatColor.RED + "You have been frozen by staff.");
            sender.sendMessage(ChatColor.GREEN + "Froze " + target.getName() + ".");
        } else {
            target.sendMessage(ChatColor.GREEN + "You are no longer frozen.");
            sender.sendMessage(ChatColor.GREEN + "Unfroze " + target.getName() + ".");
        }
        return true;
    }

    private boolean handleUnfreeze(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.staff.unfreeze")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unfreeze <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online.");
            return true;
        }

        if (!moderationManager.isFrozen(target.getUniqueId())) {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " is not frozen.");
            return true;
        }

        moderationManager.toggleFreeze(target.getUniqueId());
        target.sendMessage(ChatColor.GREEN + "You are no longer frozen.");
        sender.sendMessage(ChatColor.GREEN + "Unfroze " + target.getName() + ".");
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        rankManager.applyPlayerState(event.getPlayer());
        moderationManager.applyVanishVisibilityForViewer(event.getPlayer());
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!event.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.ALLOWED)) {
            return;
        }

        String playerName = event.getName();
        var entry = Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntry(playerName);
        if (entry == null) return;

        if (entry.getExpiration() == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "You are banned. Reason: " + (entry.getReason() == null ? "No reason provided" : entry.getReason()));
            return;
        }

        if (entry.getExpiration().getTime() > System.currentTimeMillis()) {
            long remaining = entry.getExpiration().getTime() - System.currentTimeMillis();
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "You are temporarily banned for " + ModerationManager.formatDuration(remaining)
                            + ". Reason: " + (entry.getReason() == null ? "No reason provided" : entry.getReason()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!moderationManager.isFrozen(event.getPlayer().getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        boolean movedBlock = from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
        if (!movedBlock) return;

        event.setTo(from);
        event.getPlayer().sendActionBar(ChatColor.RED + "You are frozen and cannot move.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!moderationManager.isFrozen(event.getPlayer().getUniqueId())) return;

        String message = event.getMessage().toLowerCase(Locale.ROOT);
        if (message.startsWith("/freeze ")) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "You are frozen and cannot use commands.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (!canTabCompleteCommand(sender, cmd)) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(online.getName());
                }
            }
            return names;
        }

        if (cmd.equals("rankset") && args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Arrays.stream(PlayerRank.values())
                    .filter(rank -> rank != PlayerRank.DEFAULT)
                    .map(PlayerRank::getGroupId)
                    .filter(name -> name.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if ((cmd.equals("tempban") || cmd.equals("tempmute")) && args.length == 2) {
            return List.of("30m", "1h", "12h", "1d", "7d");
        }

        return List.of();
    }

    private boolean canTabCompleteCommand(CommandSender sender, String cmd) {
        if (sender.isOp()) return true;

        return switch (cmd) {
            case "staffhelp" -> sender.hasPermission("simplefactions.staff.help")
                    || sender.hasPermission("simplefactions.staff.helper")
                    || sender.hasPermission("simplefactions.staff.mod")
                    || sender.hasPermission("simplefactions.rankset");
            case "rankset" -> sender.hasPermission("simplefactions.rankset");
            case "v", "vanish" -> sender.hasPermission("simplefactions.staff.vanish");
            case "tempmute" -> sender.hasPermission("simplefactions.staff.tempmute");
            case "mute" -> sender.hasPermission("simplefactions.staff.mute");
            case "unmute" -> sender.hasPermission("simplefactions.staff.unmute");
            case "tempban" -> sender.hasPermission("simplefactions.staff.tempban");
            case "ban" -> sender.hasPermission("simplefactions.staff.ban");
            case "unban" -> sender.hasPermission("simplefactions.staff.unban");
            case "warn" -> sender.hasPermission("simplefactions.staff.warn");
            case "tpto" -> sender.hasPermission("simplefactions.staff.tpto");
            case "freeze" -> sender.hasPermission("simplefactions.staff.freeze");
            case "unfreeze" -> sender.hasPermission("simplefactions.staff.unfreeze");
            default -> false;
        };
    }
}
