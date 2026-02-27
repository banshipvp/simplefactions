package local.simplefactions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import net.kyori.adventure.text.Component;
import java.util.*;

public class FCommand implements CommandExecutor {

    private final FactionManager manager;

    public FCommand(FactionManager manager) {
        this.manager = manager;
    }

    @Override
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

    if (!(sender instanceof Player player)) {
        sender.sendMessage("Players only.");
        return true;
    }

    if (args.length == 0) {
        sendHelp(player);
        return true;
    }

    switch (args[0].toLowerCase()) {

        case "create":
            create(player, args);
            break;

        case "invite":
            invite(player, args);
            break;

        case "join":
            join(player);
            break;

        case "leave":
            leave(player);
            break;

        case "disband":
            disband(player);
            break;

        case "chest":
            openChest(player);
            break;

        case "f":
        case "info":
            sendInfo(player);
            break;

        case "desc":
            setDescription(player, args);
            break;

        case "map":
            sendMap(player);
            break;

        case "mapgui":
            openClaimMap(player);
            break;

        case "claim":
            handleClaim(player, args);
            break;

        case "unclaim":
            handleUnclaim(player, args);
            break;

        case "unclaimall":
            unclaimAll(player);
            break;

        default:
            sendHelp(player);
            break;
    }

    return true;
}
    private void sendHelp(Player p) {
        p.sendMessage("§6/f create <name>");
        p.sendMessage("§6/f invite <player>");
        p.sendMessage("§6/f join");
        p.sendMessage("§6/f leave");
        p.sendMessage("§6/f disband");
        p.sendMessage("§6/f chest");
        p.sendMessage("§6/f info");
        p.sendMessage("§6/f desc <text>");
        p.sendMessage("§6/f map");
        p.sendMessage("§6/f claim");
        p.sendMessage("§6/f claim radius <1-5>");
        p.sendMessage("§6/f mapgui");
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
private void openClaimMap(Player player) {
    ClaimMapGui.open(player, manager);
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

        if (!manager.isLeader(player)) {
            player.sendMessage("§cOnly leader can invite.");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return;
        }

        manager.invite(player.getUniqueId(), target.getUniqueId());
        target.sendMessage("§aYou were invited. Use /f join");
    }

    private void join(Player player) {
        boolean success = manager.join(player.getUniqueId());
        player.sendMessage(success
                ? "§aJoined faction."
                : "§cNo invite found.");
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

        player.openInventory(faction.getChest());
    }

    /* ================= INFO ================= */

    private void sendInfo(Player player) {

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());

        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        player.sendMessage("§7---- §6Faction Info §7----");
        player.sendMessage("§6Name: §f" + faction.getName());
        player.sendMessage("§6Description: §f" + faction.getDescription());
        player.sendMessage("§6Members: §f" + faction.getMembers().size());
        player.sendMessage("§6Claims: §f" + faction.getClaims().size() + "/" + faction.maxClaims());
    }

    /* ================= DESCRIPTION ================= */

    private void setDescription(Player player, String[] args) {

        if (!manager.isLeader(player)) {
            player.sendMessage("§cOnly leader can change description.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /f desc <text>");
            return;
        }

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        faction.setDescription(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));

        player.sendMessage("§aDescription updated.");
    }

/* ================= MAP ================= */
private void sendMap(Player player) {

    // Cosmic-style dimensions
    final int cols = 21; // 21 symbols with 20 spaces between = 41 chars wide
    final int rows = 10; // about 10 up
    final int halfCols = cols / 2; // 10
    final int halfRows = rows / 2; // 5 (rows offsets: -5..4)

    World world = player.getWorld();
    Chunk center = player.getLocation().getChunk();

    FactionManager.Faction playerFaction = manager.getFaction(player.getUniqueId());

    // Owner of current chunk
    FactionManager.Faction hereFaction = manager.getFactionByChunk(world.getName(), center.getX(), center.getZ());
    String hereOwner = (hereFaction == null) ? "Wilderness" : hereFaction.getName();

    // Coords like Cosmic header line
    int currentChunkX = center.getX();
    int currentChunkZ = center.getZ();
    int currentBlockX = currentChunkX << 4;
    int currentBlockZ = currentChunkZ << 4;

    String ownerColour;
    if (hereFaction == null) ownerColour = "§7";
    else if (playerFaction != null && hereFaction.getName().equalsIgnoreCase(playerFaction.getName())) ownerColour = "§a";
    else ownerColour = "§d";

    // Header (single line, like Cosmic)
    player.sendMessage(ownerColour + hereOwner + " §8[" + "§f" + world.getName() + "§8] "
            + "§b" + currentBlockX + "x§7, §b" + currentBlockZ + "z "
            + "§8(" + currentChunkX + ", " + currentChunkZ + ")");

    // Divider exactly 41 across
    player.sendMessage("§7" + repeat("-", 41));

    LinkedHashSet<String> legend = new LinkedHashSet<>();

    // Build map rows
    for (int row = 0; row < rows; row++) {

        int zOffset = row - halfRows;                 // -5..4
        int cellChunkZ = center.getZ() + zOffset;

        Component line = Component.empty();

        for (int col = 0; col < cols; col++) {

            int xOffset = col - halfCols;             // -10..10
            int cellChunkX = center.getX() + xOffset;

            int cellBlockX = cellChunkX << 4;
            int cellBlockZ = cellChunkZ << 4;

            boolean isPlayerCell = (xOffset == 0 && zOffset == 0);

            // Compass cut-out: top-left 3x3 cells (row 0-2, col 0-2)
            boolean isCompassCell = (row <= 2 && col <= 2);

            Component cell;

            if (isCompassCell) {
                // Exact compass layout:
                // row0: \ N /
                // row1: S + E
                // row2: / W \
                String ch = " ";
                if (row == 0 && col == 0) ch = "§7\\";
                if (row == 0 && col == 1) ch = "§7N";
                if (row == 0 && col == 2) ch = "§7/";
                if (row == 1 && col == 0) ch = "§7S";
                if (row == 1 && col == 1) ch = "§a+";
                if (row == 1 && col == 2) ch = "§7E";
                if (row == 2 && col == 0) ch = "§7/";
                if (row == 2 && col == 1) ch = "§7W";
                if (row == 2 && col == 2) ch = "§7\\";
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
                    // Wilderness is "-"
                    cell = Component.text("§7-")
                            .hoverEvent(HoverEvent.showText(
                                    buildWildernessHover(world.getName(), cellBlockX, cellBlockZ, cellChunkX, cellChunkZ)
                            ));
                } else {
                    boolean isOwn = playerFaction != null
                            && cellFaction.getName().equalsIgnoreCase(playerFaction.getName());

                    // Claims are "/" (own green, others red)
                    String symbol = isOwn ? "§a/" : "§c/";
                    legend.add(cellFaction.getName());

                    Component hover = buildFactionHover(cellFaction, world.getName(), cellBlockX, cellBlockZ, cellChunkX, cellChunkZ);

                    // Click suggests command (swap to /f who <name> if you add it later)
                    String clickCmd = "/f info";

                    cell = Component.text(symbol)
                            .hoverEvent(HoverEvent.showText(hover))
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(clickCmd));
                }
            }

            line = line.append(cell);

            // Add a space between symbols EXCEPT after the last column
            if (col < cols - 1) {
                line = line.append(Component.text(" "));
            }
        }

        player.sendMessage(line);
    }

    // Bottom divider exactly 41 across
    player.sendMessage("§7" + repeat("-", 41));

}
// ================= HELPERS FOR MAP =================

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
            .append(Component.text("§7" + faction.getDescription() + "\n"))
            .append(Component.text("§8\n"))
            .append(Component.text("§7Left-click to §d/f info §7" + faction.getName()));
}

private void updateScoreboard(Player player) {

    ScoreboardManager managerSB = Bukkit.getScoreboardManager();
    if (managerSB == null) return;

    Scoreboard board = managerSB.getNewScoreboard();

    Objective obj = board.registerNewObjective(
            "faction",
            "dummy",
            Component.text("§6Faction")
    );

    obj.setDisplaySlot(DisplaySlot.SIDEBAR);

    FactionManager.Faction faction = manager.getFaction(player.getUniqueId());

    String factionName = (faction == null) ? "None" : faction.getName();

    obj.getScore("§fFaction:").setScore(6);
    obj.getScore("§e" + factionName).setScore(5);

    obj.getScore(" ").setScore(4);

    obj.getScore("§fMoney:").setScore(3);
    obj.getScore("§a$0").setScore(2);

    obj.getScore("  ").setScore(1);

    obj.getScore("§fClaims:").setScore(0);

    player.setScoreboard(board);
}

private void unclaimAll(Player player) {

    FactionManager.Faction faction = manager.getFaction(player.getUniqueId());

    if (faction == null) {
        player.sendMessage("§cYou are not in a faction.");
        return;
    }

    if (!manager.isLeader(player)) {
        player.sendMessage("§cOnly the leader can unclaim all land.");
        return;
    }

    int count = faction.getClaims().size();

    manager.unclaimAll(faction);

    player.sendMessage("§aUnclaimed §f" + count + "§a chunks.");
}
    private void handleUnclaim(Player player, String[] args) {

    FactionManager.Faction faction = manager.getFaction(player.getUniqueId());

    if (faction == null) {
        player.sendMessage("§cYou are not in a faction.");
        return;
    }

    Chunk center = player.getLocation().getChunk();
    World world = player.getWorld();

    if (args.length >= 2 && args[1].equalsIgnoreCase("radius")) {

        if (!manager.isLeader(player)) {
            player.sendMessage("§cOnly leader can radius unclaim.");
            return;
        }

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

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {

                if (manager.unclaimChunk(
                        faction,
                        world.getName(),
                        center.getX() + x,
                        center.getZ() + z)) {

                    removed++;
                }
            }
        }

        player.sendMessage("§aUnclaimed §f" + removed + "§a chunks.");
        return;
    }

    // Single unclaim
    if (manager.unclaimChunk(
            faction,
            world.getName(),
            center.getX(),
            center.getZ())) {

        player.sendMessage("§aChunk unclaimed.");
    } else {
        player.sendMessage("§cCannot unclaim here.");
    }
}

    /* ================= CLAIM ================= */

    private void handleClaim(Player player, String[] args) {

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());

        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        Chunk center = player.getLocation().getChunk();
        World world = player.getWorld();

        // Radius
        if (args.length >= 2 && args[1].equalsIgnoreCase("radius")) {

            if (!manager.isLeader(player)) {
                player.sendMessage("§cOnly leader can radius claim.");
                return;
            }

            if (args.length < 3) {
                player.sendMessage("§cUsage: /f claim radius <1-5>");
                return;
            }

            int radius = Integer.parseInt(args[2]);
            radius = Math.min(radius, 5);

            int claimed = 0;

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {

                    if (manager.getFactionByChunk(world.getName(),
                            center.getX()+x,
                            center.getZ()+z) == null) {

                        if (manager.claimChunk(faction,
                                world.getName(),
                                center.getX()+x,
                                center.getZ()+z)) {

                            claimed++;
                        }
                    }
                }
            }

            player.sendMessage("§aClaimed §f" + claimed + "§a chunks.");
            return;
        }

        // Single claim
        if (manager.claimChunk(faction,
                world.getName(),
                center.getX(),
                center.getZ())) {

            player.sendMessage("§aChunk claimed.");
        } else {
            player.sendMessage("§cCannot claim here.");
        }
    }
}