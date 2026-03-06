package local.simplefactions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class FCommand implements CommandExecutor, TabCompleter {

    private final FactionManager manager;
    private final UpgradeGUI upgradeGUI;
    private final FactionAccessGui accessGui;
    private final EconomyManager economyManager;
    private WarzoneManager warzoneManager;
    private final Map<UUID, PendingClaimConfirmation> pendingClaimConfirmations = new HashMap<>();
    private static final long CLAIM_CONFIRM_WINDOW_MS = 20_000L;

    private static class PendingClaimConfirmation {
        final String key;
        final long cost;
        final long expiresAt;

        PendingClaimConfirmation(String key, long cost, long expiresAt) {
            this.key = key;
            this.cost = cost;
            this.expiresAt = expiresAt;
        }
    }

    public FCommand(FactionManager manager, UpgradeGUI upgradeGUI, FactionAccessGui accessGui, EconomyManager economyManager) {
        this.manager = manager;
        this.upgradeGUI = upgradeGUI;
        this.accessGui = accessGui;
        this.economyManager = economyManager;
    }

    /** Called by SimpleFactionsPlugin after both FCommand and WarzoneManager are ready. */
    public void setWarzoneManager(WarzoneManager warzoneManager) {
        this.warzoneManager = warzoneManager;
    }

    /**
     * Returns the claiming cost (in dollars) based on proximity to warzone chunks:
     *   1 chunk away (adjacent): $2,000,000
     *   2 chunks away          : $1,000,000
     *   further away           : $0 (free)
     */
    private long warzoneBorderCost(String world, int cx, int cz) {
        if (warzoneManager == null) return 0;
        int dist = warzoneManager.nearestWarzoneDistance(world, cx, cz, 2);
        if (dist == 1) return 2_000_000L;
        if (dist == 2) return 1_000_000L;
        return 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // Console-safe wand-give command: /f givewand <player> <sell|tnt>
        if (args.length >= 1 && args[0].equalsIgnoreCase("givewand")) {
            if (!(sender instanceof Player) && !(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage("Unknown sender.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("Usage: /f givewand <player> <sell|tnt>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("Player '" + args[1] + "' is not online.");
                return true;
            }
            switch (args[2].toLowerCase()) {
                case "sell" -> {
                    Map<Integer, ItemStack> lr = target.getInventory().addItem(WandItems.createSellWand());
                    if (!lr.isEmpty()) { sender.sendMessage("Inventory full."); }
                    else { target.sendMessage("\u00a7aYou received a \u00a76Sell Wand\u00a7a."); }
                }
                case "tnt" -> {
                    Map<Integer, ItemStack> lr = target.getInventory().addItem(WandItems.createTntWand());
                    if (!lr.isEmpty()) { sender.sendMessage("Inventory full."); }
                    else { target.sendMessage("\u00a7aYou received a \u00a7cTNT Wand\u00a7a."); }
                }
                default -> sender.sendMessage("Usage: /f givewand <player> <sell|tnt>");
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "create" -> create(player, args);
            case "invite" -> invite(player, args);

            case "join" -> join(player, args);

            case "leave" -> leave(player);
            case "disband" -> disband(player);

            case "chest" -> openChest(player);
            case "tnt" -> tntCommand(player, args);
            case "sellwand" -> giveSellWand(player);
            case "wand" -> wandCommand(player, args);

            case "f", "info" -> sendInfo(player);
            case "who" -> who(player, args);

            case "desc" -> setDescription(player, args);

            case "map" -> mapCommand(player, args);
            case "mapgui" -> openClaimMap(player);

            case "claim" -> handleClaim(player, args);
            case "unclaim" -> handleUnclaim(player, args);
            case "unclaimall" -> unclaimAll(player);
            case "access" -> accessCommand(player, args);
            case "perm", "permission", "permissions" -> permCommand(player, args);

            case "kick" -> kick(player, args);
            case "promote" -> promote(player, args);
            case "demote" -> demote(player, args);

            case "sethome" -> sethome(player);
            case "home" -> home(player);
            case "setwarp" -> setwarp(player, args);
            case "warp" -> warp(player, args);
            case "warps" -> listWarps(player);
            case "delwarp" -> delwarp(player, args);
            case "upgrade" -> upgradeCommand(player, args);
            case "title" -> titleCommand(player, args);
            case "c" -> chatCommand(player, args);
            case "top" -> topFactions(player, args);

            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§7---- §6Factions Help §7----");
        p.sendMessage("§6/f create <name>");
        p.sendMessage("§6/f invite <player>");
        p.sendMessage("§6/f join [faction]");
        p.sendMessage("§6/f leave");
        p.sendMessage("§6/f disband");
        p.sendMessage("§6/f chest");
        p.sendMessage("§6/f tnt <deposit|withdraw|bal|fill|siphon|wand|set>");
        p.sendMessage("§8(Admin) §6/f tnt set <amount>");
        p.sendMessage("§8(Admin) §6/f tnt set <faction> <amount>");
        p.sendMessage("§6/f sellwand");
        p.sendMessage("§8(Admin) §6/f wand <tnt|sell|both>");
        p.sendMessage("§6/f info");
        p.sendMessage("§6/f who <name>");
        p.sendMessage("§6/f desc <text>");
        p.sendMessage("§6/f map");
        p.sendMessage("§6/f map on|off");
        p.sendMessage("§6/f mapgui");
        p.sendMessage("§6/f claim");
        p.sendMessage("§6/f claim radius <1-5>");
        p.sendMessage("§6/f access p <player> [all|radius <chunks>]");
        p.sendMessage("§6/f perm [p <player>]");
        p.sendMessage("§6/f unclaim");
        p.sendMessage("§6/f unclaim radius <1-5>");
        p.sendMessage("§6/f unclaimall");
        p.sendMessage("§6/f kick <player>");
        p.sendMessage("§6/f promote <player>");
        p.sendMessage("§6/f demote <player>");
        p.sendMessage("§6/f sethome");
        p.sendMessage("§6/f home");
        p.sendMessage("§6/f setwarp <name>");
        p.sendMessage("§6/f warp <name>");
        p.sendMessage("§6/f warps");
        p.sendMessage("§6/f delwarp <name>");
        p.sendMessage("§6/f upgrade");
        p.sendMessage("§6/f title <text>");
        p.sendMessage("§6/f c <p|f>");
        p.sendMessage("§6/f top [page]");
    }

    /* ================= CREATE ================= */

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f create <name>");
            return;
        }

        boolean success = manager.createFaction(args[1], player.getUniqueId());

        player.sendMessage(success
                ? "§aFaction created."
                : "§cCannot create faction.");
    }

    /* ================= INVITE ================= */

    private void invite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f invite <player>");
            return;
        }

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.INVITE)) {
            player.sendMessage("§cYou do not have permission to invite.");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return;
        }

        if (manager.getFaction(target.getUniqueId()) != null) {
            player.sendMessage("§cThat player is already in a faction.");
            return;
        }

        manager.invite(player.getUniqueId(), target.getUniqueId());

        player.sendMessage("§aInvited §f" + target.getName() + "§a.");
        target.sendMessage("§aYou were invited to §f" + faction.getName() + "§a. Use §f/f join " + faction.getName() + "§a.");
        target.sendMessage("§7Invite expires in 5 minutes.");
    }

    private void join(Player player, String[] args) {
        String optionalFaction = (args.length >= 2) ? args[1] : null;

        boolean success = manager.join(player.getUniqueId(), optionalFaction);

        if (success) {
            player.sendMessage("§aJoined faction.");
            return;
        }

        if (!manager.hasValidInvite(player.getUniqueId())) {
            player.sendMessage("§cNo valid invite found.");
            return;
        }

        String invName = manager.getInviteFactionName(player.getUniqueId());
        player.sendMessage("§cInvite exists, but command didn't match.");
        if (invName != null) {
            player.sendMessage("§7Try: §f/f join " + invName);
        }
    }

    private void leave(Player player) {
        boolean success = manager.leave(player.getUniqueId());
        player.sendMessage(success
                ? "§aLeft faction."
                : "§cLeader cannot leave.");
    }

    private void disband(Player player) {
        boolean success = manager.disband(player.getUniqueId());
        player.sendMessage(success
                ? "§aFaction disbanded."
                : "§cOnly leader can disband.");
    }

    private void openChest(Player player) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.OPEN_CHEST)) {
            player.sendMessage("§cYou do not have permission to open the chest.");
            return;
        }

        player.openInventory(faction.getChest());
    }

    /* ================= INFO / WHO ================= */

    private void sendInfo(Player player) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        SpawnerStackManager ssm = manager.getSpawnerStackManager();
        int spawnerCount = (ssm != null) ? ssm.getTotalCountForFaction(faction.getName()) : faction.getTotalSpawnerCount();
        double spawnerValue = manager.getTrackedSpawnerValue(faction.getName());
        double memberBalance = getMemberTotalBalance(faction);
        double totalWealth = faction.getBalance() + memberBalance + spawnerValue + faction.getPower() * 10.0;

        int onlineCount = countOnlineMembers(faction);
        int totalCount = faction.getMembers().size();

        player.sendMessage(" ");
        player.sendMessage("§e§l" + faction.getName() + " §8(#" + totalCount + ")");

        // Home location
        Location home = faction.getHome();
        String homeStr = (home == null) ? "§7N/A" : "§f" + home.getBlockX() + "x §7" + home.getBlockZ() + "z";
        player.sendMessage("§7Home: " + homeStr);

        player.sendMessage("§8" + repeat("-", 40));

        // Stats line
        player.sendMessage("§7Power: §f" + formatNumber((long)faction.getPower()) + "§7/§f" + formatNumber(totalCount * 10000L) + "  §7Balance: §f$" + formatNumber((long)faction.getBalance()));

        // Spawner + Wealth line with hover breakdown
        Component spawnerLine = Component.text(
                "§7Spawners: §f" + spawnerCount + " §8(§f$" + formatNumber((long)spawnerValue) + "§8)  §7Wealth: §f$" + formatNumber((long)totalWealth)
        ).hoverEvent(HoverEvent.showText(buildSpawnerBreakdownHover(faction.getName(), memberBalance, spawnerValue)));
        player.sendMessage(spawnerLine);

        player.sendMessage(" ");
        player.sendMessage(buildMemberLineComponent(faction, true, onlineCount, totalCount));
        player.sendMessage(buildMemberLineComponent(faction, false, onlineCount, totalCount));

        player.sendMessage(" ");
        player.sendMessage("§7Claims: §f" + faction.getClaims().size() + "§8/§f" + faction.maxClaims());
        player.sendMessage(" ");
    }
    
    private String centerText(String text, int width) {
        int totalPadding = width - text.length();
        int leftPadding = totalPadding / 2;
        int rightPadding = totalPadding - leftPadding;
        return " ".repeat(Math.max(0, leftPadding)) + text + " ".repeat(Math.max(0, rightPadding));
    }
    
    private String formatNumber(long num) {
        if (num < 1000) return String.valueOf(num);
        if (num < 1_000_000) return (num / 1000) + "K";
        return (num / 1_000_000) + "M";
    }
    
    private String padRight(String s, int length) {
        while (s.length() < length) s += " ";
        return s.substring(0, Math.min(s.length(), length));
    }
    
    private String formatMemberList(FactionManager.Faction faction, boolean online) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (UUID member : faction.getMembers()) {
            org.bukkit.entity.Player p = Bukkit.getPlayer(member);
            boolean isOnline = p != null && p.isOnline();
            if (isOnline == online) {
                if (count > 0) sb.append(" §7");
                String role = faction.getRole(member).toString();
                String asterisks = role.equals("LEADER") ? "***" : role.equals("OFFICER") ? "**" : role.equals("MOD") ? "*" : "";
                String color = isOnline ? "§a" : "§c";
                String name = p != null ? p.getName() : "Unknown";
                sb.append(asterisks).append(color).append(name);
                count++;
            }
        }
        if (count == 0) {
            sb.append("§7None");
        }
        // Pad to fit nicely
        while (sb.length() < 28) sb.append(" ");
        return sb.toString();
    }

    private void who(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f who <name>");
            return;
        }

        FactionManager.Faction faction = manager.getFactionByName(args[1]);
        if (faction == null) {
            player.sendMessage("§cFaction not found.");
            return;
        }

        // ── System factions (Warzone) — show simplified description only ──
        if (manager.isSystemFaction(faction)) {
            player.sendMessage(" ");
            player.sendMessage("§6§l" + centerText(faction.getName(), 40) + "§r");
            player.sendMessage("§7" + faction.getDescription());
            player.sendMessage(" ");
            player.sendMessage("§7Claims: §f" + faction.getClaims().size());
            player.sendMessage("§8This is a system-managed faction — it has no members.");
            player.sendMessage(" ");
            return;
        }

        org.bukkit.OfflinePlayer leaderOffline = Bukkit.getOfflinePlayer(faction.getLeader());
        String leaderName = leaderOffline.getName() != null ? leaderOffline.getName() : "Unknown";

        player.sendMessage(" ");
        player.sendMessage("§6§l" + centerText(faction.getName(), 40) + "§r");
        player.sendMessage(" ");
        player.sendMessage("§7Power: §f" + formatNumber((long)faction.getPower()) + "§7/§f" + formatNumber(faction.getMembers().size() * 10000L) + "  §7Balance: §f$" + formatNumber((long)faction.getBalance()));
        SpawnerStackManager _ssm = manager.getSpawnerStackManager();
        int _spawnerCount = (_ssm != null) ? _ssm.getTotalCountForFaction(faction.getName()) : faction.getTotalSpawnerCount();
        double _spawnerValue = manager.getTrackedSpawnerValue(faction.getName());
        double _memberBal = getMemberTotalBalance(faction);
        double _wealth = faction.getBalance() + _memberBal + _spawnerValue + faction.getPower() * 10.0;
        player.sendMessage("§7Spawners: §f" + _spawnerCount + " §7(Worth: $" + formatNumber((long)_spawnerValue) + ")  §7Wealth: §f$" + formatNumber((long)_wealth));
        player.sendMessage(" ");
        player.sendMessage("§7Claims: §f" + faction.getClaims().size() + "§7/§f" + faction.maxClaims() + "  §7Members: §f" + faction.getMembers().size() + "§7/§f" + faction.getMaxMembers());
        player.sendMessage("§7Warps: §f" + faction.getWarps().size() + "§7/§f" + faction.getMaxWarps() + "  §7Chest: §f" + faction.getChestSlots() + "§7 slots");
        player.sendMessage("§7Leader: §f" + leaderName);
        player.sendMessage(" ");
        player.sendMessage("§aOnline: " + formatMemberList(faction, true));
        player.sendMessage("§cOffline: " + formatMemberList(faction, false));
        player.sendMessage(" ");
    }

    /* ================= DESCRIPTION ================= */

    private void setDescription(Player player, String[] args) {

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.SET_DESC)) {
            player.sendMessage("§cYou do not have permission to change description.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /f desc <text>");
            return;
        }

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        faction.setDescription(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        player.sendMessage("§aDescription updated.");
    }

    /* ================= MAPGUI ================= */

    private void openClaimMap(Player player) {
        ClaimMapGui.open(player, manager);
    }

    /* ================= MAP ================= */

    private void mapCommand(Player player, String[] args) {
        if (args.length >= 2) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (mode.equals("on")) {
                manager.setAutoMap(player.getUniqueId(), true);
                player.sendMessage("§aAuto map enabled. Faction map will update each chunk you enter.");
                sendMap(player);
                return;
            }
            if (mode.equals("off")) {
                manager.setAutoMap(player.getUniqueId(), false);
                player.sendMessage("§cAuto map disabled.");
                return;
            }
            player.sendMessage("§cUsage: /f map [on|off]");
            return;
        }

        sendMap(player);
    }

    public void sendMap(Player player) {
        final int cols = 21;
        final int rows = 10;
        final int halfCols = cols / 2;
        final int halfRows = rows / 2;

        World world = player.getWorld();
        Chunk center = player.getLocation().getChunk();
        float yaw = player.getLocation().getYaw();

        FactionManager.Faction playerFaction = manager.getFaction(player.getUniqueId());
        FactionManager.Faction hereFaction = manager.getFactionByChunk(world.getName(), center.getX(), center.getZ());
        String hereOwner = (hereFaction == null) ? "Wilderness" : hereFaction.getName();

        int currentChunkX = center.getX();
        int currentChunkZ = center.getZ();
        int currentBlockX = currentChunkX << 4;
        int currentBlockZ = currentChunkZ << 4;

        String ownerColour;
        if (hereFaction == null) ownerColour = "§7";
        else if (playerFaction != null && hereFaction.getName().equalsIgnoreCase(playerFaction.getName())) ownerColour = "§a";
        else ownerColour = "§d";

        player.sendMessage(ownerColour + hereOwner + " §8[" + "§f" + world.getName() + "§8] "
                + "§b" + currentBlockX + "x§7, §b" + currentBlockZ + "z "
                + "§8(" + currentChunkX + ", " + currentChunkZ + ")");

        player.sendMessage("§7" + repeat("-", 41));

        for (int row = 0; row < rows; row++) {

            int zOffset = row - halfRows;
            int cellChunkZ = center.getZ() + zOffset;

            Component line = Component.empty();

            for (int col = 0; col < cols; col++) {

                int xOffset = col - halfCols;
                int cellChunkX = center.getX() + xOffset;

                int cellBlockX = cellChunkX << 4;
                int cellBlockZ = cellChunkZ << 4;

                boolean isPlayerCell = (xOffset == 0 && zOffset == 0);
                boolean isCompassCell = (row <= 2 && col <= 2);

                Component cell;

                if (isCompassCell) {
                    String ch = " ";
                    String baseColor = "§7";
                    
                    // Determine player facing direction
                    String facingDir = getFacingDirection(yaw);
                    
                    if (row == 0 && col == 0) ch = "§7\\";
                    if (row == 0 && col == 1) { ch = "N"; if (facingDir.equals("N")) baseColor = "§a"; }
                    if (row == 0 && col == 2) ch = "§7/";
                    if (row == 1 && col == 0) { ch = "W"; if (facingDir.equals("W")) baseColor = "§a"; }
                    if (row == 1 && col == 1) ch = "§a+";
                    if (row == 1 && col == 2) { ch = "E"; if (facingDir.equals("E")) baseColor = "§a"; }
                    if (row == 2 && col == 0) ch = "§7/";
                    if (row == 2 && col == 1) { ch = "S"; if (facingDir.equals("S")) baseColor = "§a"; }
                    if (row == 2 && col == 2) ch = "§7\\";
                    
                    if (!ch.equals(" ") && !ch.startsWith("§")) {
                        ch = baseColor + ch;
                    }
                    cell = Component.text(ch);
                } else {
                    FactionManager.Faction cellFaction =
                            manager.getFactionByChunk(world.getName(), cellChunkX, cellChunkZ);

                    if (isPlayerCell) {
                        cell = Component.text("§a+")
                                .hoverEvent(HoverEvent.showText(
                                        buildYouHereHover(world.getName(), cellBlockX, cellBlockZ, cellChunkX, cellChunkZ)
                                ));
                    } else if (cellFaction == null) {
                        cell = Component.text("§7-")
                                .hoverEvent(HoverEvent.showText(
                                        buildWildernessHover(world.getName(), cellBlockX, cellBlockZ, cellChunkX, cellChunkZ)
                                ));
                    } else {
                        // Check if this is a core chunk
                        String chunkKey = world.getName() + ":" + cellChunkX + ":" + cellChunkZ;
                        CoreChunk coreChunk = manager.getCoreChunkManager().getCoreChunk(cellFaction.getName());
                        boolean isCoreChunk = coreChunk != null && chunkKey.equals(coreChunk.getChunkKey());
                        
                        if (isCoreChunk) {
                            // Core chunk - yellow with special symbol
                            cell = Component.text("§e■")
                                    .hoverEvent(HoverEvent.showText(
                                            buildCoreChunkHover(coreChunk, cellFaction, world.getName(), cellBlockX, cellBlockZ, cellChunkX, cellChunkZ)
                                    ));
                        } else {
                            boolean isOwn = playerFaction != null
                                    && cellFaction.getName().equalsIgnoreCase(playerFaction.getName());

                            String symbol = isOwn ? "§a/" : "§c/";
                            Component hover = buildFactionHover(cellFaction, world.getName(), cellBlockX, cellBlockZ, cellChunkX, cellChunkZ);
                            cell = Component.text(symbol).hoverEvent(HoverEvent.showText(hover));
                        }
                    }
                }

                line = line.append(cell);
                if (col < cols - 1) line = line.append(Component.text(" "));
            }

            player.sendMessage(line);
        }

        player.sendMessage("§7" + repeat("-", 41));
    }

    private String getFacingDirection(float yaw) {
        // Normalize yaw to 0-360
        yaw = ((yaw % 360) + 360) % 360;
        
        // Determine cardinal direction (inverted from standard due to Minecraft coordinate system)
        if (yaw < 45 || yaw >= 315) return "S";
        if (yaw < 135) return "W";
        if (yaw < 225) return "N";
        return "E";
    }

    private int countOnlineMembers(FactionManager.Faction faction) {
        int count = 0;
        for (UUID member : faction.getMembers()) {
            org.bukkit.entity.Player p = Bukkit.getPlayer(member);
            if (p != null && p.isOnline()) count++;
        }
        return count;
    }

    private String repeat(String s, int times) {
        if (times <= 0) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) builder.append(s);
        return builder.toString();
    }

    private Component buildYouHereHover(String world, int blockX, int blockZ, int chunkX, int chunkZ) {
        return Component.text("")
                .append(Component.text("§aYou are here\n"))
                .append(Component.text("§bLocation: §f" + blockX + "x §7" + blockZ + "z §8(" + chunkX + ", " + chunkZ + ") §7[" + world + "]\n"))
                .append(Component.text("§7"));
    }

    private Component buildWildernessHover(String world, int blockX, int blockZ, int chunkX, int chunkZ) {
        return Component.text("")
                .append(Component.text("§7Wilderness\n"))
                .append(Component.text("§bLocation: §f" + blockX + "x §7" + blockZ + "z §8(" + chunkX + ", " + chunkZ + ") §7[" + world + "]\n"))
                .append(Component.text("§8"));
    }

    private Component buildFactionHover(FactionManager.Faction faction, String world, int blockX, int blockZ, int chunkX, int chunkZ) {
        int members = faction.getMembers().size();
        int claims = faction.getClaims().size();

        return Component.text("")
                .append(Component.text("§d§l" + faction.getName() + " BASE CLAIM\n"))
                .append(Component.text("§bLocation: §f" + blockX + "x §7" + blockZ + "z §8(" + chunkX + ", " + chunkZ + ") §7[" + world + "]\n"))
                .append(Component.text("§dMembers: §f" + members + "\n"))
                .append(Component.text("§dClaims: §f" + claims + "§7/§f" + faction.maxClaims() + "\n"))
                .append(Component.text("§7" + faction.getDescription() + "\n"));
    }

    private Component buildCoreChunkHover(CoreChunk core, FactionManager.Faction faction, String world, int blockX, int blockZ, int chunkX, int chunkZ) {
        int members = faction.getMembers().size();
        int claims = faction.getClaims().size();
        double healthPercent = core.getHealthPercentage();
        String healthColor = healthPercent > 75 ? "§a" : healthPercent > 50 ? "§e" : healthPercent > 25 ? "§6" : "§c";

        return Component.text("")
                .append(Component.text("§e§l⬛ CORE CHUNK ⬛\n"))
                .append(Component.text("§6Faction: §f" + faction.getName() + "\n"))
                .append(Component.text("§bLocation: §f" + blockX + "x §7" + blockZ + "z §8(" + chunkX + ", " + chunkZ + ") §7[" + world + "]\n"))
                .append(Component.text("\n"))
                .append(Component.text(healthColor + "❤ Health: " + String.format("%.1f", core.getCurrentPoints()) + "§7/§f" + String.format("%.0f", core.getMaxPoints()) + " §7(" + String.format("%.1f", healthPercent) + "%)\n"))
                .append(Component.text("§d⚔ PVP Points: §f" + String.format("%.0f", core.getPvpPoints()) + "\n"))
                .append(Component.text("§b⚡ Spawner Points: §f" + String.format("%.0f", core.getSpawnerPoints()) + "\n"))
                .append(Component.text("§e★ Challenge Points: §f" + String.format("%.0f", core.getChallengePoints()) + "\n"))
                .append(Component.text("§a$ Notes: §f" + String.format("%.0f", core.getCoreChunkNotes()) + "\n"))
                .append(Component.text("\n"))
                .append(Component.text("§dMembers: §f" + members + "\n"))
                .append(Component.text("§dClaims: §f" + claims + "§7/§f" + faction.maxClaims() + "\n"))
                .append(Component.text("§7" + faction.getDescription() + "\n"));
    }

    /* ================= CLAIM / UNCLAIM ================= */

    private void handleClaim(Player player, String[] args) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.CLAIM)) {
            player.sendMessage("§cYou do not have permission to claim.");
            return;
        }

        Chunk center = player.getLocation().getChunk();
        World world = player.getWorld();

        if (args.length >= 2 && args[1].equalsIgnoreCase("radius")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /f claim radius <1-5>");
                return;
            }

            int radius;
            try {
                radius = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid number.");
                return;
            }

            radius = Math.min(radius, 5);
            boolean firstClaimForFaction = faction.getClaims().isEmpty();

            if (firstClaimForFaction) {
                if (isWarzoneChunk(world.getName(), center.getX(), center.getZ())) {
                    player.sendMessage("§cYou cannot claim inside the warzone.");
                    return;
                }
                FactionManager.Faction centerOwner = manager.getFactionByChunk(world.getName(), center.getX(), center.getZ());
                if (centerOwner != null) {
                    player.sendMessage("§cCannot claim radius here because your core chunk must be your current chunk, and it is already claimed.");
                    return;
                }
            }

            List<int[]> claimTargets = new ArrayList<>();
            int blockedWarzone = 0;

            if (firstClaimForFaction) {
                claimTargets.add(new int[]{center.getX(), center.getZ()});
            }

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (firstClaimForFaction && x == 0 && z == 0) continue;
                    int cx = center.getX() + x;
                    int cz = center.getZ() + z;
                    if (isWarzoneChunk(world.getName(), cx, cz)) {
                        blockedWarzone++;
                        continue;
                    }
                    if (manager.getFactionByChunk(world.getName(), cx, cz) != null) continue;
                    claimTargets.add(new int[]{cx, cz});
                }
            }

            int availableClaimSlots = Math.max(0, faction.maxClaims() - faction.getClaims().size());
            if (availableClaimSlots <= 0) {
                player.sendMessage("§cYour faction has reached max claims.");
                return;
            }

            if (claimTargets.size() > availableClaimSlots) {
                claimTargets = new ArrayList<>(claimTargets.subList(0, availableClaimSlots));
            }

            if (claimTargets.isEmpty()) {
                if (blockedWarzone > 0) {
                    player.sendMessage("§cYou cannot claim inside warzone chunks.");
                } else {
                    player.sendMessage("§cNo claimable chunks found in that radius.");
                }
                return;
            }

            long totalCost = 0L;
            Map<String, Long> borderCostByChunk = new HashMap<>();
            for (int[] target : claimTargets) {
                long chunkCost = warzoneBorderCost(world.getName(), target[0], target[1]);
                totalCost += chunkCost;
                borderCostByChunk.put(target[0] + ":" + target[1], chunkCost);
            }

            if (totalCost > 0) {
                String confirmKey = "radius:" + world.getName() + ":" + center.getX() + ":" + center.getZ() + ":" + radius + ":" + claimTargets.size();
                if (!isClaimConfirmed(player, confirmKey, totalCost)) {
                    pendingClaimConfirmations.put(player.getUniqueId(),
                            new PendingClaimConfirmation(confirmKey, totalCost, System.currentTimeMillis() + CLAIM_CONFIRM_WINDOW_MS));
                    player.sendMessage("§6⚠ Claim confirmation required.");
                    player.sendMessage("§eThis radius claim costs §6$" + formatMoney(totalCost) + "§e due to warzone-border pricing.");
                    player.sendMessage("§eRun §f/f claim radius " + radius + " §eagain within 20 seconds to confirm.");
                    return;
                }

                if (!economyManager.isEnabled()) {
                    player.sendMessage("§cSome chunks in this radius border the warzone and require $" + formatMoney(totalCost) + " but economy is not available.");
                    return;
                }
                if (!economyManager.has(player, totalCost)) {
                    player.sendMessage("§cClaiming these chunks near the warzone costs §e$" + formatMoney(totalCost) + "§c. You don't have enough money.");
                    return;
                }
                economyManager.withdrawPlayer(player, totalCost);
                player.sendMessage("§6⚠ §ePaid §6$" + formatMoney(totalCost) + " §efor warzone-border chunks.");
            }

            int claimed = 0;
            long usedCost = 0L;

            for (int[] target : claimTargets) {
                if (manager.claimChunk(faction, world.getName(), target[0], target[1])) {
                    claimed++;
                    usedCost += borderCostByChunk.getOrDefault(target[0] + ":" + target[1], 0L);
                }
            }

            if (totalCost > usedCost) {
                long refund = totalCost - usedCost;
                if (refund > 0) {
                    economyManager.depositPlayer(player, refund);
                    player.sendMessage("§eRefunded §6$" + formatMoney(refund) + " §efor chunks that could not be claimed.");
                }
            }

            player.sendMessage("§aClaimed §f" + claimed + "§a chunks.");
            if (blockedWarzone > 0) {
                player.sendMessage("§eSkipped §f" + blockedWarzone + "§e warzone chunks (cannot be claimed).");
            }
            return;
        }

        // ── Single chunk claim ────────────────────────────────────────────────
        if (isWarzoneChunk(world.getName(), center.getX(), center.getZ())) {
            player.sendMessage("§cYou cannot claim inside the warzone.");
            return;
        }

        if (manager.getFactionByChunk(world.getName(), center.getX(), center.getZ()) != null) {
            player.sendMessage("§cCannot claim here.");
            return;
        }

        if (faction.getClaims().size() >= faction.maxClaims()) {
            player.sendMessage("§cYour faction has reached max claims.");
            return;
        }

        long cost = warzoneBorderCost(world.getName(), center.getX(), center.getZ());
        if (cost > 0) {
            String confirmKey = "single:" + world.getName() + ":" + center.getX() + ":" + center.getZ();
            if (!isClaimConfirmed(player, confirmKey, cost)) {
                pendingClaimConfirmations.put(player.getUniqueId(),
                        new PendingClaimConfirmation(confirmKey, cost, System.currentTimeMillis() + CLAIM_CONFIRM_WINDOW_MS));
                player.sendMessage("§6⚠ Claim confirmation required.");
                player.sendMessage("§eThis chunk borders warzone and costs §6$" + formatMoney(cost) + "§e.");
                player.sendMessage("§eRun §f/f claim §eagain within 20 seconds to confirm.");
                return;
            }

            if (!economyManager.isEnabled()) {
                player.sendMessage("§cThis chunk borders the warzone and costs §e$" + formatMoney(cost) + "§c to claim, but economy is unavailable.");
                return;
            }
            if (!economyManager.has(player, cost)) {
                player.sendMessage("§cThis chunk borders the warzone and costs §e$" + formatMoney(cost) + "§c to claim. You don't have enough money.");
                return;
            }
            economyManager.withdrawPlayer(player, cost);
            player.sendMessage("§6⚠ §ePaid §6$" + formatMoney(cost) + " §efor this warzone-border chunk.");
        }

        if (manager.claimChunk(faction, world.getName(), center.getX(), center.getZ())) {
            player.sendMessage("§aChunk claimed.");
        } else {
            if (cost > 0) {
                economyManager.depositPlayer(player, cost);
                player.sendMessage("§eClaim failed, refunded §6$" + formatMoney(cost) + "§e.");
            }
            player.sendMessage("§cCannot claim here.");
        }
    }

    private boolean isClaimConfirmed(Player player, String key, long cost) {
        PendingClaimConfirmation pending = pendingClaimConfirmations.get(player.getUniqueId());
        if (pending == null) return false;
        if (System.currentTimeMillis() > pending.expiresAt) {
            pendingClaimConfirmations.remove(player.getUniqueId());
            return false;
        }
        if (!pending.key.equals(key) || pending.cost != cost) return false;
        pendingClaimConfirmations.remove(player.getUniqueId());
        return true;
    }

    private boolean isWarzoneChunk(String worldName, int chunkX, int chunkZ) {
        return warzoneManager != null && warzoneManager.isWarzoneChunk(worldName, chunkX, chunkZ);
    }

    private static String formatMoney(long amount) {
        if (amount >= 1_000_000_000) return String.format("%.1fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000)     return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000)         return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }

    private void handleUnclaim(Player player, String[] args) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.UNCLAIM)) {
            player.sendMessage("§cYou do not have permission to unclaim.");
            return;
        }

        Chunk center = player.getLocation().getChunk();
        World world = player.getWorld();

        if (args.length >= 2 && args[1].equalsIgnoreCase("radius")) {

            if (args.length < 3) {
                player.sendMessage("§cUsage: /f unclaim radius <1-5>");
                return;
            }

            int radius;
            try {
                radius = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid number.");
                return;
            }

            radius = Math.min(radius, 5);

            int removed = 0;
            int blockedCore = 0;

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (manager.isCoreChunk(faction, world.getName(), center.getX() + x, center.getZ() + z)) {
                        blockedCore++;
                        continue;
                    }
                    if (manager.unclaimChunk(faction, world.getName(), center.getX() + x, center.getZ() + z)) {
                        removed++;
                    }
                }
            }

            player.sendMessage("§aUnclaimed §f" + removed + "§a chunks.");
            if (blockedCore > 0) {
                player.sendMessage("§cUnable to unclaim corechunk. You must use /f unclaimall.");
            }
            return;
        }

        if (manager.isCoreChunk(faction, world.getName(), center.getX(), center.getZ())) {
            player.sendMessage("§cUnable to unclaim corechunk. You must use /f unclaimall.");
            return;
        }

        if (manager.unclaimChunk(faction, world.getName(), center.getX(), center.getZ())) {
            player.sendMessage("§aChunk unclaimed.");
        } else {
            player.sendMessage("§cCannot unclaim here.");
        }
    }

    private void unclaimAll(Player player) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.UNCLAIM_ALL)) {
            player.sendMessage("§cYou do not have permission to unclaim all.");
            return;
        }

        int count = faction.getClaims().size();
        manager.unclaimAll(faction);
        player.sendMessage("§aUnclaimed §f" + count + "§a chunks.");
    }

    private void accessCommand(Player player, String[] args) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.FLAG)) {
            player.sendMessage("§cYou do not have permission to manage access.");
            return;
        }

        if (args.length < 3 || !args[1].equalsIgnoreCase("p")) {
            player.sendMessage("§cUsage: /f access p <player> [all|radius <chunks>]");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            player.sendMessage("§cPlayer not found online.");
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cYou already have access to your own claims.");
            return;
        }

        if (faction.isMember(target.getUniqueId())) {
            player.sendMessage("§eThat player is already in your faction.");
            return;
        }

        if (args.length == 3) {
            var chunk = player.getLocation().getChunk();
            boolean success = manager.grantAccessChunk(player.getUniqueId(), target.getUniqueId(), player.getWorld().getName(), chunk.getX(), chunk.getZ());
            if (!success) {
                player.sendMessage("§cCould not grant chunk access.");
                return;
            }

            player.sendMessage("§aGranted §f" + target.getName() + "§a access for this claim chunk.");
            target.sendMessage("§aYou now have access to a claim chunk in faction §f" + faction.getName() + "§a.");
            return;
        }

        if (args[3].equalsIgnoreCase("all")) {
            boolean success = manager.grantAccessAll(player.getUniqueId(), target.getUniqueId());
            if (!success) {
                player.sendMessage("§cCould not grant all-claims access.");
                return;
            }

            player.sendMessage("§aGranted §f" + target.getName() + "§a access to all faction claims.");
            target.sendMessage("§aYou now have access to all claims in faction §f" + faction.getName() + "§a.");
            return;
        }

        if (args[3].equalsIgnoreCase("radius")) {
            if (args.length < 5) {
                player.sendMessage("§cUsage: /f access p <player> radius <chunks>");
                return;
            }

            int radius;
            try {
                radius = Integer.parseInt(args[4]);
            } catch (NumberFormatException ex) {
                player.sendMessage("§cRadius must be a number.");
                return;
            }

            if (radius < 1) {
                player.sendMessage("§cRadius must be at least 1.");
                return;
            }

            radius = Math.min(radius, 10);
            var chunk = player.getLocation().getChunk();
            int granted = manager.grantAccessRadius(
                    player.getUniqueId(),
                    target.getUniqueId(),
                    player.getWorld().getName(),
                    chunk.getX(),
                    chunk.getZ(),
                    radius
            );

            player.sendMessage("§aGranted §f" + target.getName() + "§a access to §f" + granted + "§a claim chunks.");
            target.sendMessage("§aYou now have radius-based access in faction §f" + faction.getName() + "§a claims.");
            return;
        }

        player.sendMessage("§cUsage: /f access p <player> [all|radius <chunks>]");
    }

    private void permCommand(Player player, String[] args) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.FLAG)) {
            player.sendMessage("§cYou do not have permission to manage claim permissions.");
            return;
        }

        if (args.length == 1) {
            accessGui.openPlayerSelector(player);
            return;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("p")) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                player.sendMessage("§cPlayer not found online.");
                return;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage("§cYou cannot edit your own claim access here.");
                return;
            }

            if (faction.isMember(target.getUniqueId())) {
                player.sendMessage("§eThat player is in your faction and already has faction-member access.");
                return;
            }

            manager.grantAccessChunk(
                    player.getUniqueId(),
                    target.getUniqueId(),
                    player.getWorld().getName(),
                    player.getLocation().getChunk().getX(),
                    player.getLocation().getChunk().getZ()
            );
            accessGui.openPlayerSelector(player);
            return;
        }

        player.sendMessage("§cUsage: /f perm [p <player>]");
    }

    /* ================= KICK / PROMOTE / DEMOTE ================= */

    private void kick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f kick <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return;
        }

        boolean ok = manager.kick(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(ok ? "§aKicked §f" + target.getName() + "§a." : "§cCannot kick that player.");

        if (ok) target.sendMessage("§cYou were kicked from your faction.");
    }

    private void promote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f promote <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return;
        }

        boolean ok = manager.promoteToOfficer(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(ok ? "§aPromoted §f" + target.getName() + "§a to Officer." : "§cCannot promote that player.");
    }

    private void demote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f demote <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return;
        }

        boolean ok = manager.demoteToMember(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(ok ? "§aDemoted §f" + target.getName() + "§a to Member." : "§cCannot demote that player.");
    }

    /* ================= HOME ================= */

    private void sethome(Player player) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }
        
        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.SET_HOME)) {
            player.sendMessage("§cYou do not have permission to set home.");
            return;
        }
        
        // Check if location is in faction territory
        Chunk chunk = player.getLocation().getChunk();
        FactionManager.Faction chunkOwner = manager.getFactionByChunk(player.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (chunkOwner == null || !chunkOwner.getName().equalsIgnoreCase(faction.getName())) {
            player.sendMessage("§cYou can only set home inside your faction territory!");
            return;
        }
        
        boolean ok = manager.setHome(player.getUniqueId(), player.getLocation());
        player.sendMessage(ok ? "§aFaction home set." : "§cFailed to set faction home.");
    }

    private void home(Player player) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.HOME)) {
            player.sendMessage("§cYou do not have permission to use /f home.");
            return;
        }

        if (!faction.hasHome()) {
            player.sendMessage("§cFaction home is not set.");
            return;
        }

        var loc = faction.getHome();
        if (loc == null) {
            player.sendMessage("§cFaction home world is missing.");
            return;
        }

        player.teleport(loc);
        player.sendMessage("§aTeleported to faction home.");
    }

    /* ================= WARPS ================= */

    private void setwarp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f setwarp <name>");
            return;
        }

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.SET_HOME)) {
            player.sendMessage("§cYou do not have permission to set warps.");
            return;
        }

        // Check if location is in faction territory
        Chunk chunk = player.getLocation().getChunk();
        FactionManager.Faction chunkOwner = manager.getFactionByChunk(player.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (chunkOwner == null || !chunkOwner.getName().equalsIgnoreCase(faction.getName())) {
            player.sendMessage("§cYou can only set warps inside your faction territory!");
            return;
        }

        String warpName = args[1];
        faction.addWarp(warpName, player.getLocation());
        player.sendMessage("§aWarp §f" + warpName + "§a created at your location.");
    }

    private void warp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f warp <name>");
            return;
        }

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.HOME)) {
            player.sendMessage("§cYou do not have permission to use warps.");
            return;
        }

        String warpName = args[1].toLowerCase();
        Location warpLoc = faction.getWarp(warpName);
        if (warpLoc == null) {
            player.sendMessage("§cWarp §f" + warpName + "§c not found.");
            return;
        }

        player.teleport(warpLoc);
        player.sendMessage("§aTeleported to warp §f" + warpName + "§a.");
    }

    private void listWarps(Player player) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        java.util.Map<String, Location> warps = faction.getWarps();
        if (warps.isEmpty()) {
            player.sendMessage("§cNo warps set.");
            return;
        }

        player.sendMessage("§7╔═══════════════════════════════════════╗");
        player.sendMessage("§7║ §b§lWarps§r§7 (§f" + warps.size() + "§7)                    ║");
        player.sendMessage("§7╠═══════════════════════════════════════╣");
        for (String name : warps.keySet()) {
            player.sendMessage("§7║ §a• §f" + padRight(name, 33) + "§7║");
        }
        player.sendMessage("§7╚═══════════════════════════════════════╝");
    }

    private void delwarp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f delwarp <name>");
            return;
        }

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (!manager.hasPerm(player.getUniqueId(), FactionPermission.SET_HOME)) {
            player.sendMessage("§cYou do not have permission to delete warps.");
            return;
        }

        String warpName = args[1].toLowerCase();
        if (faction.getWarp(warpName) == null) {
            player.sendMessage("§cWarp §f" + warpName + "§c not found.");
            return;
        }

        faction.removeWarp(warpName);
        player.sendMessage("§aWarp §f" + warpName + "§a deleted.");
    }

    /* ================= TNT BANK ================= */

    private void tntCommand(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("wand")) {
            if (!hasWandAdminAccess(player)) {
                player.sendMessage("§cYou do not have permission to get faction wands.");
                return;
            }
            giveTntWand(player);
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("set")) {
            tntSet(player, args);
            return;
        }

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /f tnt <deposit|withdraw|bal|fill|siphon|wand|set>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "deposit", "d" -> tntDeposit(player, faction, args);
            case "withdraw", "w" -> tntWithdraw(player, faction, args);
            case "bal", "b", "balance" -> tntBalance(player, faction);
            case "fill", "f" -> tntFill(player, faction, args);
            case "siphon", "s" -> tntSiphon(player, faction, args);
            default -> player.sendMessage("§cUsage: /f tnt <deposit|withdraw|bal|fill|siphon|wand|set>");
        }
    }

    private void tntSet(Player player, String[] args) {
        if (!player.hasPermission("simplefactions.admin")) {
            player.sendMessage("§cYou do not have permission to set faction TNT.");
            return;
        }

        if (args.length == 3) {
            FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
            if (faction == null) {
                player.sendMessage("§cYou are not in a faction. Use §f/f tnt set <faction> <amount>§c.");
                return;
            }

            Integer parsed = parsePositiveIntAllowZero(args[2]);
            if (parsed == null) {
                player.sendMessage("§cAmount must be a non-negative whole number.");
                return;
            }

            faction.setTntBank(parsed);
            player.sendMessage("§aSet faction TNT bank to §f" + parsed + "§a.");
            tntBalance(player, faction);
            return;
        }

        if (args.length == 4) {
            String targetFactionName = args[2];
            FactionManager.Faction targetFaction = manager.getFactionByName(targetFactionName);
            if (targetFaction == null) {
                player.sendMessage("§cFaction not found: §f" + targetFactionName);
                return;
            }

            Integer parsed = parsePositiveIntAllowZero(args[3]);
            if (parsed == null) {
                player.sendMessage("§cAmount must be a non-negative whole number.");
                return;
            }

            targetFaction.setTntBank(parsed);
            player.sendMessage("§aSet TNT bank for §f" + targetFaction.getName() + " §ato §f" + parsed + "§a.");
            return;
        }

        player.sendMessage("§cUsage: /f tnt set <amount>");
        player.sendMessage("§cUsage: /f tnt set <faction> <amount>");
    }

    private void wandCommand(Player player, String[] args) {
        if (!hasWandAdminAccess(player)) {
            player.sendMessage("§cYou do not have permission to get faction wands.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /f wand <tnt|sell|both>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "tnt" -> giveTntWand(player);
            case "sell" -> giveSellWand(player);
            case "both" -> {
                giveTntWand(player);
                giveSellWand(player);
            }
            default -> player.sendMessage("§cUsage: /f wand <tnt|sell|both>");
        }
    }

    private void tntBalance(Player player, FactionManager.Faction faction) {
        int total = faction.getTntBank();
        int stacks = total / 64;
        int singles = total % 64;
        player.sendMessage("§6Faction TNT Bank: §f" + total + " TNT");
        player.sendMessage("§7(" + stacks + " stacks, " + singles + " individual)");
    }

    private void tntDeposit(Player player, FactionManager.Faction faction, String[] args) {
        Container targetContainer = getTargetContainer(player);
        boolean fromContainer = targetContainer != null;

        int available = fromContainer
                ? countTnt(targetContainer.getInventory().getContents())
                : countTnt(player.getInventory().getContents());

        if (available <= 0) {
            player.sendMessage(fromContainer
                    ? "§cThat container has no TNT."
                    : "§cYou don't have any TNT to deposit.");
            return;
        }

        String amountArg = args.length >= 3 ? args[2] : "all";

        int toDeposit;
        if (amountArg.equalsIgnoreCase("all")) {
            toDeposit = available;
        } else {
            Integer parsed = parsePositiveInt(amountArg);
            if (parsed == null) {
                player.sendMessage("§cAmount must be a positive number or 'all'.");
                return;
            }
            toDeposit = Math.min(parsed, available);
        }

        int removed = fromContainer
                ? removeTnt(targetContainer.getInventory().getContents(), targetContainer.getInventory(), toDeposit)
                : removeTnt(player.getInventory().getContents(), player.getInventory(), toDeposit);

        if (removed <= 0) {
            player.sendMessage("§cCould not deposit TNT.");
            return;
        }

        faction.addTnt(removed);

        if (fromContainer) {
            player.sendMessage("§aDeposited §f" + removed + " TNT §afrom targeted container into faction bank.");
        } else {
            player.sendMessage("§aDeposited §f" + removed + " TNT §ainto faction bank.");
        }
        tntBalance(player, faction);
    }

    private void tntWithdraw(Player player, FactionManager.Faction faction, String[] args) {
        int bank = faction.getTntBank();
        if (bank <= 0) {
            player.sendMessage("§cYour faction TNT bank is empty.");
            return;
        }

        String amountArg = args.length >= 3 ? args[2] : "64";

        int requested;
        if (amountArg.equalsIgnoreCase("all")) {
            requested = bank;
        } else {
            Integer parsed = parsePositiveInt(amountArg);
            if (parsed == null) {
                player.sendMessage("§cAmount must be a positive number or 'all'.");
                return;
            }
            requested = Math.min(parsed, bank);
        }

        ItemStack give = new ItemStack(Material.TNT, requested);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(give);

        int leftover = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int given = requested - leftover;

        if (given <= 0) {
            player.sendMessage("§cYour inventory is full.");
            return;
        }

        faction.removeTnt(given);

        if (leftover > 0) {
            player.sendMessage("§eInventory full, withdrew §f" + given + " TNT §e(out of " + requested + ").");
        } else {
            player.sendMessage("§aWithdrew §f" + given + " TNT §afrom faction bank.");
        }
        tntBalance(player, faction);
    }

    private void tntFill(Player player, FactionManager.Faction faction, String[] args) {
        if (args.length < 5) {
            player.sendMessage("§cUsage: /f tnt fill <radius> <amountPerDispenser> <maxPerDispenser>");
            return;
        }

        Integer radiusVal = parsePositiveInt(args[2]);
        Integer amountVal = parsePositiveInt(args[3]);
        Integer maxVal = parsePositiveInt(args[4]);

        if (radiusVal == null || amountVal == null || maxVal == null) {
            player.sendMessage("§cRadius, amount, and max must be positive numbers.");
            return;
        }

        int radius = Math.min(radiusVal, 32);
        int amountPerDispenser = amountVal;
        int maxPerDispenser = maxVal;

        if (faction.getTntBank() <= 0) {
            player.sendMessage("§cYour faction TNT bank is empty.");
            return;
        }

        List<Block> containers = findNearbyFactionTntContainers(player, faction, radius);
        if (containers.isEmpty()) {
            player.sendMessage("§cNo faction TNT containers found within radius " + radius + ".");
            return;
        }

        int totalAdded = 0;
        int affected = 0;

        for (Block block : containers) {
            Container container = getLiveTntContainer(block);
            if (container == null) continue;

            if (faction.getTntBank() <= 0) break;

            Inventory inv = container.getInventory();
            int current = countTnt(inv.getContents());
            if (current >= maxPerDispenser) continue;

            int allowed = maxPerDispenser - current;
            int toAdd = Math.min(amountPerDispenser, allowed);
            toAdd = Math.min(toAdd, faction.getTntBank());

            if (toAdd <= 0) continue;

            Map<Integer, ItemStack> leftovers = inv.addItem(new ItemStack(Material.TNT, toAdd));
            int leftover = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            int attemptedAdded = toAdd - leftover;

            if (attemptedAdded <= 0) {
                continue;
            }

            int after = countTnt(inv.getContents());
            int added = Math.max(0, after - current);

            if (added > 0) {
                faction.removeTnt(added);
                totalAdded += added;
                affected++;
            }
        }

        if (totalAdded <= 0) {
            player.sendMessage("§cNo TNT was added (dispensers may already be full to cap). ");
            return;
        }

        player.sendMessage("§aFilled §f" + affected + " dispensers §awith §f" + totalAdded + " TNT§a.");
        tntBalance(player, faction);
    }

    private void tntSiphon(Player player, FactionManager.Faction faction, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /f tnt siphon <amountPerDispenser|all>");
            return;
        }

        boolean siphonAll = args[2].equalsIgnoreCase("all");
        Integer amountPerDispenser = siphonAll ? null : parsePositiveInt(args[2]);

        if (!siphonAll && amountPerDispenser == null) {
            player.sendMessage("§cAmount must be a positive number or 'all'.");
            return;
        }

        int radius = 16;
        List<Block> containers = findNearbyFactionTntContainers(player, faction, radius);
        if (containers.isEmpty()) {
            player.sendMessage("§cNo faction TNT containers found within radius " + radius + ".");
            return;
        }

        int totalRemoved = 0;
        int affected = 0;

        for (Block block : containers) {
            Container container = getLiveTntContainer(block);
            if (container == null) continue;

            Inventory inv = container.getInventory();
            int current = countTnt(inv.getContents());
            if (current <= 0) continue;

            int toRemove = siphonAll ? current : Math.min(current, amountPerDispenser);
            int removed = removeTnt(inv.getContents(), inv, toRemove);

            if (removed > 0) {
                faction.addTnt(removed);
                totalRemoved += removed;
                affected++;
            }
        }

        if (totalRemoved <= 0) {
            player.sendMessage("§cNo TNT could be siphoned from nearby faction dispensers.");
            return;
        }

        player.sendMessage("§aSiphoned §f" + totalRemoved + " TNT §afrom §f" + affected + " dispensers§a.");
        tntBalance(player, faction);
    }

    private void giveTntWand(Player player) {
        if (!hasWandAdminAccess(player)) {
            player.sendMessage("§cYou do not have permission to get faction wands.");
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(WandItems.createTntWand());
        if (!leftover.isEmpty()) {
            player.sendMessage("§cInventory full.");
            return;
        }
        player.sendMessage("§aYou received a §cTNT Wand§a. Left-click a chest/container to deposit all TNT into faction bank.");
    }

    private void giveSellWand(Player player) {
        if (!hasWandAdminAccess(player)) {
            player.sendMessage("§cYou do not have permission to get faction wands.");
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(WandItems.createSellWand());
        if (!leftover.isEmpty()) {
            player.sendMessage("§cInventory full.");
            return;
        }
        player.sendMessage("§aYou received a §6Sell Wand§a. Left-click a chest/container to run §f/sell all§a.");
    }

    private boolean hasWandAdminAccess(Player player) {
        return player.isOp() || player.hasPermission("simplefactions.admin.wands");
    }

    private Integer parsePositiveInt(String input) {
        try {
            int val = Integer.parseInt(input);
            return val > 0 ? val : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Container getTargetContainer(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) return null;
        if (!(block.getState() instanceof Container container)) return null;
        return container;
    }

    private int countTnt(ItemStack[] contents) {
        int total = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == Material.TNT) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private int removeTnt(ItemStack[] ignoredSnapshot, org.bukkit.inventory.Inventory inventory, int amount) {
        if (amount <= 0) return 0;

        int remaining = amount;
        ItemStack[] contents = inventory.getContents();

        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != Material.TNT) continue;

            int take = Math.min(stack.getAmount(), remaining);
            int left = stack.getAmount() - take;

            if (left <= 0) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(left);
                inventory.setItem(slot, stack);
            }
            remaining -= take;
        }

        return amount - remaining;
    }

    private List<Block> findNearbyFactionTntContainers(Player player, FactionManager.Faction faction, int radius) {
        List<Block> containers = new ArrayList<>();
        Location base = player.getLocation();
        World world = player.getWorld();

        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();

        for (int x = bx - radius; x <= bx + radius; x++) {
            for (int z = bz - radius; z <= bz + radius; z++) {
                for (int y = Math.max(world.getMinHeight(), by - 6); y <= Math.min(world.getMaxHeight() - 1, by + 6); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type != Material.DISPENSER && type != Material.DROPPER) continue;

                    FactionManager.Faction owner = manager.getFactionByChunk(world.getName(), block.getChunk().getX(), block.getChunk().getZ());
                    if (owner == null || !owner.getName().equalsIgnoreCase(faction.getName())) continue;

                    if (block.getState() instanceof Container) {
                        containers.add(block);
                    }
                }
            }
        }

        return containers;
    }

    private Container getLiveTntContainer(Block block) {
        Material type = block.getType();
        if (type != Material.DISPENSER && type != Material.DROPPER) {
            return null;
        }

        if (block.getState() instanceof Container container) {
            return container;
        }

        return null;
    }

    /* ================= CHAT ================= */

    private void chatCommand(Player player, String[] args) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /f c <p|f>");
            player.sendMessage("§6/f c p §7- Switch to public chat");
            player.sendMessage("§6/f c f §7- Switch to faction chat");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "p", "public" -> {
                manager.setFactionChatMode(player.getUniqueId(), false);
                player.sendMessage("§aYou switched to §fpublic §achat.");
            }
            case "f", "faction" -> {
                manager.setFactionChatMode(player.getUniqueId(), true);
                player.sendMessage("§aYou switched to §e" + faction.getName() + " §achat.");
            }
            default -> player.sendMessage("§cUsage: /f c <p|f>");
        }
    }

    /* ================= TITLE ================= */

    private void titleCommand(Player player, String[] args) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        if (args.length < 2) {
            String currentTitle = faction.getPlayerTitle(player.getUniqueId());
            if (currentTitle.isEmpty()) {
                player.sendMessage("§cUsage: /f title <title>");
                player.sendMessage("§6/f title <text> §7- Set your faction title");
            } else {
                player.sendMessage("§aYour current title: §f" + currentTitle);
                player.sendMessage("§6/f title <new_title> §7- Change your title");
            }
            return;
        }

        // Combine all args after "title" into the title string
        StringBuilder titleBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) titleBuilder.append(" ");
            titleBuilder.append(args[i]);
        }
        String newTitle = titleBuilder.toString();

        faction.setPlayerTitle(player.getUniqueId(), newTitle);
        player.sendMessage("§aYour title has been set to: §f" + newTitle);
    }

    /* ================= UPGRADES ================= */

    private void upgradeCommand(Player player, String[] args) {
        upgradeGUI.openUpgradeGUI(player);
    }

    private double getUpgradeCost(String upgradeType, int nextLevel) {
        return switch(upgradeType.toLowerCase()) {
            case "maxmembers" -> 100_000 * nextLevel;      // 100K per level
            case "spawnermult" -> 150_000 * nextLevel;     // 150K per level
            case "maxwarps" -> 50_000 * nextLevel;         // 50K per level
            case "chestslots" -> 75_000 * nextLevel;       // 75K per level
            default -> 0;
        };
    }

    /* ================= TOP FACTIONS ================= */

    private void topFactions(Player player, String[] args) {
        // Get all non-system factions, re-sorted with member balances included
        List<FactionManager.Faction> allFactions = manager.getAllFactionsSortedByWealth().stream()
                .filter(f -> !manager.isSystemFaction(f))
                .sorted((a, b) -> {
                    double wA = manager.getTrackedSpawnerValue(a.getName()) + a.getBalance() + getMemberTotalBalance(a) + a.getPower() * 10.0;
                    double wB = manager.getTrackedSpawnerValue(b.getName()) + b.getBalance() + getMemberTotalBalance(b) + b.getPower() * 10.0;
                    return Double.compare(wB, wA);
                })
                .collect(Collectors.toList());

        if (allFactions.isEmpty()) {
            player.sendMessage("§cNo factions exist yet.");
            return;
        }

        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid page number.");
                return;
            }
        }

        int pageSize = 10;
        int totalPages = Math.max(1, (int) Math.ceil((double) allFactions.size() / pageSize));

        if (page < 1 || page > totalPages) {
            player.sendMessage("§cPage must be between 1 and " + totalPages + ".");
            return;
        }

        // Header
        player.sendMessage("");
        player.sendMessage("§8§m                                            ");
        player.sendMessage("  §6§lFACTION LEADERBOARD  §8│  §7Page " + page + "§8/§7" + totalPages);
        player.sendMessage("  §7Hover over a faction for wealth breakdown.");
        player.sendMessage("§8§m                                            ");
        player.sendMessage("");

        int startIndex = (page - 1) * pageSize;
        int endIndex   = Math.min(startIndex + pageSize, allFactions.size());

        for (int i = startIndex; i < endIndex; i++) {
            FactionManager.Faction faction = allFactions.get(i);
            int rank = i + 1;

            double spawnerValue  = manager.getTrackedSpawnerValue(faction.getName());
            double memberBalance = getMemberTotalBalance(faction);
            double totalWealth   = faction.getBalance() + memberBalance + spawnerValue + faction.getPower() * 10.0;

            String medal;
            if      (rank == 1) medal = "§6§l#1 ";
            else if (rank == 2) medal = "§f§l#2 ";
            else if (rank == 3) medal = "§e§l#3 ";
            else if (rank <= 10) medal = "§e#" + rank + " ";
            else                 medal = "§7#" + rank + " ";

            String lineText = medal + "§f" + faction.getName()
                    + " §8│ §7Wealth: §a$" + formatNumber(totalWealth)
                    + " §8│ §7Members: §f" + faction.getMembers().size();

            // Build hover breakdown
            SpawnerStackManager ssm = manager.getSpawnerStackManager();
            Component hover = Component.text("§6§l" + faction.getName() + "\n\n")
                    .append(Component.text("§7Faction Balance:  §f$" + formatNumber((long)faction.getBalance()) + "\n"))
                    .append(Component.text("§7Member Balances:  §f$" + formatNumber((long)memberBalance) + "\n"))
                    .append(Component.text("§7Spawner Value:    §f$" + formatNumber((long)spawnerValue) + "\n"));

            if (ssm != null) {
                var breakdown = ssm.getBreakdownForFaction(faction.getName());
                if (!breakdown.isEmpty()) {
                    hover = hover.append(Component.text("§8   ─── Spawner Breakdown ───\n"));
                    for (var entry : breakdown.entrySet()) {
                        hover = hover.append(Component.text(
                                "§8   §e" + entry.getKey() + " §8x" + (int) entry.getValue()[0]
                                + " §7→ §f$" + formatNumber((long) entry.getValue()[1]) + "\n"
                        ));
                    }
                }
            }

            hover = hover
                    .append(Component.text("§8────────────────────\n"))
                    .append(Component.text("§aTotal Wealth: §f$" + formatNumber((long)totalWealth)));

            player.sendMessage(Component.text(lineText).hoverEvent(HoverEvent.showText(hover)));
        }

        player.sendMessage("");
        player.sendMessage("§8§m                                            ");
        if (totalPages > 1) {
            player.sendMessage("§7  Use §6/f top <page> §7to navigate.");
        }
        player.sendMessage("");
    }
    
    // ── Wealth / Spawner helpers ───────────────────────────────────────────────

    /** Sum of all online/offline member Vault balances for a faction. */
    private double getMemberTotalBalance(FactionManager.Faction faction) {
        if (economyManager == null || !economyManager.isEnabled()) return 0;
        double total = 0;
        for (UUID member : faction.getMembers()) {
            total += economyManager.getBalance(Bukkit.getOfflinePlayer(member));
        }
        return total;
    }

    /** Human-readable time since a timestamp, e.g. "3h ago", "2d ago". */
    private String getTimeAgo(long timestamp) {
        long diffMs = System.currentTimeMillis() - timestamp;
        long seconds = diffMs / 1000;
        if (seconds < 60)   return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60)   return minutes + "m ago";
        long hours = minutes / 60;
        if (hours   < 24)   return hours + "h ago";
        long days = hours / 24;
        if (days    < 30)   return days + "d ago";
        long weeks = days / 7;
        if (weeks   < 4)    return weeks + "w ago";
        return (days / 30) + "mo ago";
    }

    /** Hover component showing spawner type breakdown + balances. */
    private Component buildSpawnerBreakdownHover(String factionName, double memberBalance, double spawnerValue) {
        SpawnerStackManager ssm = manager.getSpawnerStackManager();
        Component hover = Component.text("§6§lSpawner Breakdown\n\n");
        if (ssm != null) {
            var breakdown = ssm.getBreakdownForFaction(factionName);
            if (breakdown.isEmpty()) {
                hover = hover.append(Component.text("§7No spawners placed.\n"));
            } else {
                for (var entry : breakdown.entrySet()) {
                    hover = hover.append(Component.text(
                            "§e" + entry.getKey() + " §8x" + (int) entry.getValue()[0]
                            + " §7→ §f$" + formatNumber((long) entry.getValue()[1]) + "\n"
                    ));
                }
            }
        } else {
            hover = hover.append(Component.text("§7Spawner tracking not available.\n"));
        }
        hover = hover
                .append(Component.text("§8────────────────\n"))
                .append(Component.text("§7Spawner Value:    §f$" + formatNumber((long)spawnerValue) + "\n"))
                .append(Component.text("§7Member Balances:  §f$" + formatNumber((long)memberBalance) + "\n"));
        return hover;
    }

    /**
     * Builds a Component line listing faction members (online or offline).
     * Each member's name has a hover showing their balance and last login.
     */
    private Component buildMemberLineComponent(FactionManager.Faction faction, boolean showOnline,
                                               int onlineCount, int totalCount) {
        String header = showOnline
                ? "§7Online: §8[§f" + onlineCount + "§8/§f" + totalCount + "§8]§7 "
                : "§7Offline: §8[§f" + (totalCount - onlineCount) + "§8/§f" + totalCount + "§8]§7 ";
        Component line = Component.text(header);

        boolean first = true;
        for (UUID member : faction.getMembers()) {
            Player online = Bukkit.getPlayer(member);
            boolean isOnline = online != null && online.isOnline();
            if (isOnline != showOnline) continue;

            if (!first) line = line.append(Component.text(" §7"));
            first = false;

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member);
            String displayName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
            String roleStr = faction.getRole(member).toString();
            String badge = roleStr.equals("LEADER") ? "§6✯" : roleStr.equals("OFFICER") ? "§e★" : roleStr.equals("MOD") ? "§b✦" : "";
            String color = isOnline ? "§a" : "§c";

            double bal = (economyManager != null && economyManager.isEnabled())
                    ? economyManager.getBalance(offlinePlayer) : 0;

            Component memberHover;
            if (isOnline) {
                memberHover = Component.text("§a" + displayName + " §8[" + roleStr + "]\n")
                        .append(Component.text("§7Balance: §f$" + formatNumber((long) bal) + "\n"))
                        .append(Component.text("§a● Currently Online"));
            } else {
                long lastPlayed = offlinePlayer.getLastPlayed();
                String lastSeen = lastPlayed > 0 ? getTimeAgo(lastPlayed) : "Unknown";
                memberHover = Component.text("§c" + displayName + " §8[" + roleStr + "]\n")
                        .append(Component.text("§7Balance:   §f$" + formatNumber((long) bal) + "\n"))
                        .append(Component.text("§7Last Seen: §f" + lastSeen));
            }

            line = line.append(
                    Component.text(badge + color + displayName)
                            .hoverEvent(HoverEvent.showText(memberHover))
            );
        }

        if (first) line = line.append(Component.text("§7None"));
        return line;
    }

    /**
     * Format a number with K, M, B suffix
     */
    private String formatNumber(double num) {
        if (num < 1000) return String.valueOf((long) num);
        if (num < 1_000_000) return String.format("%.1f", num / 1000) + "K";
        if (num < 1_000_000_000) return String.format("%.1f", num / 1_000_000) + "M";
        return String.format("%.1f", num / 1_000_000_000) + "B";
    }

    private Integer parsePositiveIntAllowZero(String input) {
        try {
            int value = Integer.parseInt(input);
            return value >= 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterByPrefix(args[0], List.of("access", "perm", "permission", "permissions"));
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("access")) {
            if (args.length == 2) {
                return filterByPrefix(args[1], List.of("p"));
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("p")) {
                return onlinePlayerNames(args[2], player.getName());
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("p")) {
                return filterByPrefix(args[3], List.of("all", "radius"));
            }

            if (args.length == 5 && args[1].equalsIgnoreCase("p") && args[3].equalsIgnoreCase("radius")) {
                return filterByPrefix(args[4], List.of("1", "2", "3", "4", "5", "8", "10"));
            }
        }

        if (sub.equals("perm") || sub.equals("permission") || sub.equals("permissions")) {
            if (args.length == 2) {
                return filterByPrefix(args[1], List.of("p"));
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("p")) {
                return onlinePlayerNames(args[2], player.getName());
            }
        }

        if (sub.equals("tnt") && args.length == 2) {
            List<String> base = new ArrayList<>(List.of("deposit", "withdraw", "bal", "fill", "siphon", "wand"));
            if (player.hasPermission("simplefactions.admin")) {
                base.add("set");
            }
            return filterByPrefix(args[1], base);
        }

        if (sub.equals("tnt") && args.length == 3 && args[1].equalsIgnoreCase("set")) {
            if (player.hasPermission("simplefactions.admin")) {
                List<String> options = new ArrayList<>();
                options.addAll(manager.getFactionNames());
                options.addAll(List.of("0", "64", "320", "640", "1024"));
                return filterByPrefix(args[2], options);
            }
            return Collections.emptyList();
        }

        if (sub.equals("tnt") && args.length == 4 && args[1].equalsIgnoreCase("set") && player.hasPermission("simplefactions.admin")) {
            return filterByPrefix(args[3], List.of("0", "64", "320", "640", "1024"));
        }

        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames(String prefix, String excludeName) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            String name = online.getName();
            if (name.equalsIgnoreCase(excludeName)) continue;
            if (name.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                names.add(name);
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private List<String> filterByPrefix(String prefix, List<String> options) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                result.add(option);
            }
        }
        return result;
    }
}