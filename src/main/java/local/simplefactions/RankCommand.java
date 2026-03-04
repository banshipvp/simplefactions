package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * /rank – admin rank management and player rank info.
 *
 * /rank                          – show your own rank info (same as /rankinfo)
 * /rank <player>                 – show target's rank info (op only)
 * /rank set <player> <tier>      – set rank (permission: simplefactions.rank)
 *
 * /rankinfo                      – show your own rank perks
 */
public class RankCommand implements CommandExecutor {

    private final PlayerRankManager rankManager;

    public RankCommand(PlayerRankManager rankManager) {
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isRankInfo = command.getName().equalsIgnoreCase("rankinfo");

        // /rankinfo — always shows own rank info
        if (isRankInfo || args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Player-only command.");
                return true;
            }
            showRankInfo(sender, player);
            return true;
        }

        // /rank set <player> <tier>
        if (args[0].equalsIgnoreCase("set")) {
            handleSetRank(sender, args);
            return true;
        }

        // /rank <player>  — view another player's rank
        if (sender instanceof Player && !sender.hasPermission("simplefactions.rank")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view other players' ranks.");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player §7" + args[0] + " §cis not online.");
            return true;
        }

        showRankInfo(sender, target);
        return true;
    }

    // ── /rank set <player> <tier> ─────────────────────────────────────────────

    private void handleSetRank(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplefactions.rank")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to set ranks.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank set <player> <tier>");
            sender.sendMessage(ChatColor.GRAY + "Tiers: " + Arrays.stream(PlayerRank.values())
                    .filter(r -> r != PlayerRank.DEFAULT)
                    .map(PlayerRank::getGroupId)
                    .collect(Collectors.joining(", ")));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player §7" + args[1] + " §cis not online.");
            return;
        }

        PlayerRank newRank = PlayerRank.fromGroupId(args[2].toLowerCase());
        if (newRank == null) {
            sender.sendMessage(ChatColor.RED + "Unknown rank tier: §7" + args[2]);
            sender.sendMessage(ChatColor.GRAY + "Valid tiers: " + Arrays.stream(PlayerRank.values())
                    .filter(r -> r != PlayerRank.DEFAULT)
                    .map(PlayerRank::getGroupId)
                    .collect(Collectors.joining(", ")));
            return;
        }

        rankManager.setRank(target, newRank);
        sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s rank to "
                + newRank.getDisplayName() + ChatColor.GREEN + ".");
        target.sendMessage(ChatColor.GREEN + "Your rank has been set to " + newRank.getDisplayName()
                + ChatColor.GREEN + " by an admin.");
    }

    // ── Rank info display ─────────────────────────────────────────────────────

    private void showRankInfo(CommandSender viewer, Player target) {
        PlayerRank rank = rankManager.getRank(target);
        boolean isSelf  = viewer instanceof Player p && p.getUniqueId().equals(target.getUniqueId());
        String  subject = isSelf ? "Your" : target.getName() + "'s";

        viewer.sendMessage(ChatColor.GOLD + "─── " + subject + " Rank Info ───");
        viewer.sendMessage(ChatColor.YELLOW + "  Rank:      " + rank.getDisplayName());
        viewer.sendMessage(ChatColor.YELLOW + "  Homes:     §f" + rank.getMaxHomes());
        viewer.sendMessage(ChatColor.YELLOW + "  Vaults:    §f" + rank.getMaxVaults());

        if (rank.hasXpExhaust()) {
            long remaining = rankManager.getRemainingXpCooldownSeconds(target);
            String cooldown = remaining > 0
                    ? "§e" + remaining + "s remaining"
                    : "§aReady";
            viewer.sendMessage(ChatColor.YELLOW + "  XP Bottle: §f" + dfmt(rank.getXpExhaustMinutes())
                    + " min cooldown §7(" + cooldown + "§7)");
        } else {
            viewer.sendMessage(ChatColor.YELLOW + "  XP Bottle: §ano cooldown");
        }

        viewer.sendMessage(ChatColor.YELLOW + "  /fly:      " + (rank.canFly() ? "§aEnabled" : "§cDisabled"));
        viewer.sendMessage(ChatColor.YELLOW + "  Queue priority: §f" + rank.getQueuePriority());
    }

    /** Format a double – remove trailing .0 for clean output */
    private static String dfmt(double d) {
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format("%.1f", d);
    }
}
