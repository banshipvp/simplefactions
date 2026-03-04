package local.simplefactions;

import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /warzone command — admin-only management of warzone and safezone chunks.
 *
 * Usage:
 *   /warzone claim [warzone|safezone] [radius <n>]
 *   /warzone claim safezone selection    — uses //pos1 and //pos2
 *   /warzone unclaim [radius <n>]
 *   /warzone unclaim selection           — removes regions intersecting selection
 *   /warzone autoclaim [warzone|safezone]    — toggles auto-claim while walking
 *   /warzone showselection               — particle box for current selection
 */
public class WarzoneCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§6[Warzone] §r";

    private final FactionManager factionManager;
    private final WarzoneManager warzoneManager;
    private final JavaPlugin plugin;

    /** Players currently in auto-claim mode and the type they're claiming. */
    private final Map<UUID, WarzoneManager.WarzoneType> autoClaiming = new HashMap<>();

    public WarzoneCommand(JavaPlugin plugin, FactionManager factionManager, WarzoneManager warzoneManager) {
        this.plugin = plugin;
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
            case "showselection" -> handleShowSelection(player);
            default          -> sendHelp(player);
        }
        return true;
    }

    // ── /warzone claim [warzone|safezone] [radius <n>] ────────────────────────

    private void handleClaim(Player player, String[] args) {
        WarzoneManager.WarzoneType type = WarzoneManager.WarzoneType.WARZONE;
        int radius = 0;
        boolean selection = false;

        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.equals("safezone"))       type = WarzoneManager.WarzoneType.SAFEZONE;
            else if (a.equals("warzone"))   type = WarzoneManager.WarzoneType.WARZONE;
            else if (a.equals("selection") || a.equals("region")) selection = true;
            else if (a.equals("radius") && i + 1 < args.length) {
                try { radius = Math.min(10, Math.max(0, Integer.parseInt(args[++i]))); }
                catch (NumberFormatException ignored) {}
            }
        }

        if (selection) {
            if (type != WarzoneManager.WarzoneType.SAFEZONE && type != WarzoneManager.WarzoneType.WARZONE) {
                player.sendMessage(PREFIX + "§cSelection claim requires warzone or safezone.");
                return;
            }
            Region region = getSelectionRegion(player);
            if (region == null) return;
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            warzoneManager.addRegion(player.getWorld().getName(),
                    min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                    max.getBlockX(), max.getBlockY(), max.getBlockZ(),
                    type);
            String color = type == WarzoneManager.WarzoneType.SAFEZONE ? "§b" : "§c";
            player.sendMessage(PREFIX + color + type.name() + " §7— selection region saved.");
            return;
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
        boolean selection = false;

        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("radius") && i + 1 < args.length) {
                try { radius = Math.min(10, Math.max(0, Integer.parseInt(args[++i]))); }
                catch (NumberFormatException ignored) {}
            } else if (args[i].equalsIgnoreCase("selection") || args[i].equalsIgnoreCase("region")) {
                selection = true;
            }
        }

        if (selection) {
            Region region = getSelectionRegion(player);
            if (region == null) return;
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            int removed = warzoneManager.removeRegionsIntersecting(player.getWorld().getName(),
                    min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                    max.getBlockX(), max.getBlockY(), max.getBlockZ());
            player.sendMessage(PREFIX + "§aRemoved §f" + removed + " §aregion(s) intersecting selection.");
            return;
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
        player.sendMessage("§e/warzone claim §7[warzone|safezone] selection");
        player.sendMessage("§e/warzone unclaim §7[radius <n>]");
        player.sendMessage("§e/warzone unclaim §7selection");
        player.sendMessage("§e/warzone autoclaim §7[warzone|safezone] §8— toggle auto-claim while walking");
        player.sendMessage("§e/warzone showselection §8— show WorldEdit selection box");
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("simplefactions.admin")) return List.of();

        if (args.length == 1) {
            return List.of("claim", "unclaim", "autoclaim", "showselection").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "claim", "autoclaim" -> List.of("warzone", "safezone", "radius").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                case "unclaim" -> List.of("radius", "selection").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                default -> List.of();
            };
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            return List.of("selection", "region", "radius").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
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

    private void handleShowSelection(Player player) {
        showSelectionFor(player, true, false);
    }

    public void showSelectionFor(Player player, boolean notify, boolean silentIncomplete) {
        Region region = getSelectionRegion(player, silentIncomplete);
        if (region == null) return;
        drawSelectionBox(player, region, 100);
        if (notify) {
            player.sendMessage(PREFIX + "§aSelection box shown for 5s.");
        }
    }

    private void drawSelectionBox(Player player, Region region, int durationTicks) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        DustOptions dust = new DustOptions(Color.fromRGB(255, 80, 210), 1.8f);

        new BukkitRunnable() {
            int remaining = durationTicks;

            @Override
            public void run() {
                if (remaining <= 0 || !player.isOnline()) {
                    cancel();
                    return;
                }
                remaining -= 10;
                spawnOutline(player, min, max, dust, 0.5);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void spawnOutline(Player player, BlockVector3 min, BlockVector3 max, DustOptions dust, double step) {
        int minX = min.getBlockX();
        int minY = min.getBlockY();
        int minZ = min.getBlockZ();
        int maxX = max.getBlockX();
        int maxY = max.getBlockY();
        int maxZ = max.getBlockZ();

        // 12 edges of the box
        drawLine(player, minX, minY, minZ, maxX, minY, minZ, dust, step);
        drawLine(player, minX, minY, maxZ, maxX, minY, maxZ, dust, step);
        drawLine(player, minX, maxY, minZ, maxX, maxY, minZ, dust, step);
        drawLine(player, minX, maxY, maxZ, maxX, maxY, maxZ, dust, step);

        drawLine(player, minX, minY, minZ, minX, maxY, minZ, dust, step);
        drawLine(player, maxX, minY, minZ, maxX, maxY, minZ, dust, step);
        drawLine(player, minX, minY, maxZ, minX, maxY, maxZ, dust, step);
        drawLine(player, maxX, minY, maxZ, maxX, maxY, maxZ, dust, step);

        drawLine(player, minX, minY, minZ, minX, minY, maxZ, dust, step);
        drawLine(player, maxX, minY, minZ, maxX, minY, maxZ, dust, step);
        drawLine(player, minX, maxY, minZ, minX, maxY, maxZ, dust, step);
        drawLine(player, maxX, maxY, minZ, maxX, maxY, maxZ, dust, step);
    }

    private void drawLine(Player player, int x1, int y1, int z1, int x2, int y2, int z2, DustOptions dust, double step) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0) return;

        double steps = Math.max(1.0, Math.floor(length / step));
        double incX = dx / steps;
        double incY = dy / steps;
        double incZ = dz / steps;

        double x = x1;
        double y = y1;
        double z = z1;
        for (int i = 0; i <= steps; i++) {
            player.spawnParticle(Particle.DUST, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0, dust);
            x += incX;
            y += incY;
            z += incZ;
        }
    }

    private Region getSelectionRegion(Player player) {
        return getSelectionRegion(player, false);
    }

    private Region getSelectionRegion(Player player, boolean silentIncomplete) {
        Plugin we = player.getServer().getPluginManager().getPlugin("WorldEdit");
        Plugin fawe = player.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");
        if (we == null && fawe == null) {
            if (!silentIncomplete) {
                player.sendMessage(PREFIX + "§cWorldEdit/FAWE is required for selection zones.");
            }
            return null;
        }
        try {
            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
            Region region = session.getSelection(wePlayer.getWorld());
            if (!player.getWorld().getName().equalsIgnoreCase(region.getWorld().getName())) {
                if (!silentIncomplete) {
                    player.sendMessage(PREFIX + "§cSelection must be in your current world.");
                }
                return null;
            }
            return region;
        } catch (IncompleteRegionException ex) {
            if (!silentIncomplete) {
                player.sendMessage(PREFIX + "§cSet both //pos1 and //pos2 first.");
            }
            return null;
        }
    }
}
