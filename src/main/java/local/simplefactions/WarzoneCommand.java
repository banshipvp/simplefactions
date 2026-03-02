package local.simplefactions;

import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /warzone command — admin-only management of warzone and safezone chunks.
 *
 * Usage:
 *   /warzone claim [warzone|safezone] [radius <n>]
 *   /warzone unclaim [radius <n>]
 *   /warzone autoclaim [warzone|safezone]    — toggles auto-claim while walking
 */
public class WarzoneCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§6[Warzone] §r";

    private final FactionManager factionManager;
    private final WarzoneManager warzoneManager;

    /** Players currently in auto-claim mode and the type they're claiming. */
    private final Map<UUID, WarzoneManager.WarzoneType> autoClaiming = new HashMap<>();

    public WarzoneCommand(FactionManager factionManager, WarzoneManager warzoneManager) {
        this.factionManager = factionManager;
        this.warzoneManager = warzoneManager;
    }

    // Accessed by WarzoneListener
    public Map<UUID, WarzoneManager.WarzoneType> getAutoClaiming() {
        return autoClaiming;
    }

    // ── Command dispatch ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }
        if (!player.hasPermission("simplefactions.admin")) {
            player.sendMessage(PREFIX + "§cYou don't have permission.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "claim"     -> handleClaim(player, args);
            case "unclaim"   -> handleUnclaim(player, args);
            case "autoclaim" -> handleAutoClaim(player, args);
            default          -> sendHelp(player);
        }
        return true;
    }

    // ── /warzone claim [warzone|safezone] [radius <n>] ────────────────────────

    private void handleClaim(Player player, String[] args) {
        WarzoneManager.WarzoneType type = WarzoneManager.WarzoneType.WARZONE;
        int radius = 0;

        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.equals("safezone"))       type = WarzoneManager.WarzoneType.SAFEZONE;
            else if (a.equals("warzone"))   type = WarzoneManager.WarzoneType.WARZONE;
            else if (a.equals("radius") && i + 1 < args.length) {
                try { radius = Math.min(10, Math.max(0, Integer.parseInt(args[++i]))); }
                catch (NumberFormatException ignored) {}
            }
        }

        String world = player.getWorld().getName();
        Chunk center = player.getChunk();
        int claimed = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.getX() + dx;
                int cz = center.getZ() + dz;
                String key = WarzoneManager.chunkKey(world, cx, cz);

                String currentOwner = factionManager.getClaimOwner(world, cx, cz);
                if (currentOwner != null && !currentOwner.equalsIgnoreCase("warzone")) {
                    player.sendMessage(PREFIX + "§cChunk " + cx + "," + cz
                            + " is claimed by \"" + currentOwner + "\", skipping.");
                    continue;
                }
                if (currentOwner == null) {
                    factionManager.claimChunkWarzone(world, cx, cz);
                }
                warzoneManager.setChunkType(key, type);
                claimed++;
            }
        }

        String color = type == WarzoneManager.WarzoneType.SAFEZONE ? "§b" : "§c";
        player.sendMessage(PREFIX + color + type.name() + " §7— claimed §f" + claimed + " §7chunk(s).");
    }

    // ── /warzone unclaim [radius <n>] ─────────────────────────────────────────

    private void handleUnclaim(Player player, String[] args) {
        int radius = 0;

        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("radius") && i + 1 < args.length) {
                try { radius = Math.min(10, Math.max(0, Integer.parseInt(args[++i]))); }
                catch (NumberFormatException ignored) {}
            }
        }

        String world = player.getWorld().getName();
        Chunk center = player.getChunk();
        int unclaimed = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.getX() + dx;
                int cz = center.getZ() + dz;
                String key = WarzoneManager.chunkKey(world, cx, cz);
                if (factionManager.unclaimChunkWarzone(world, cx, cz)) {
                    warzoneManager.removeChunkType(key);
                    unclaimed++;
                }
            }
        }

        player.sendMessage(PREFIX + "§aUnclaimed §f" + unclaimed + " §awarzone/safezone chunk(s).");
    }

    // ── /warzone autoclaim [warzone|safezone] ─────────────────────────────────

    private void handleAutoClaim(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        if (autoClaiming.containsKey(uuid)) {
            autoClaiming.remove(uuid);
            player.sendMessage(PREFIX + "§eAutoClaim §cdisabled§e.");
            return;
        }

        WarzoneManager.WarzoneType type = WarzoneManager.WarzoneType.WARZONE;
        if (args.length >= 2 && args[1].equalsIgnoreCase("safezone")) {
            type = WarzoneManager.WarzoneType.SAFEZONE;
        }
        autoClaiming.put(uuid, type);

        String color = type == WarzoneManager.WarzoneType.SAFEZONE ? "§b" : "§c";
        player.sendMessage(PREFIX + "§eAutoClaim §aenabled §7(" + color + type.name()
                + "§7). Walk to claim chunks. Run §f/warzone autoclaim §7again to stop.");
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage("§6§l— Warzone Admin Commands —");
        player.sendMessage("§e/warzone claim §7[warzone|safezone] [radius <n>]");
        player.sendMessage("§e/warzone unclaim §7[radius <n>]");
        player.sendMessage("§e/warzone autoclaim §7[warzone|safezone] §8— toggle auto-claim while walking");
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("simplefactions.admin")) return List.of();

        if (args.length == 1) {
            return List.of("claim", "unclaim", "autoclaim").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "claim", "autoclaim" -> List.of("warzone", "safezone", "radius").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                case "unclaim" -> List.of("radius").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("claim")
                && (args[1].equalsIgnoreCase("warzone") || args[1].equalsIgnoreCase("safezone"))) {
            return List.of("radius");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("radius")) {
            return List.of("1", "3", "5", "10");
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("radius")) {
            return List.of("1", "3", "5", "10");
        }
        return List.of();
    }
}
