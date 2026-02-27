package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class ClaimMapGui {

    private ClaimMapGui() {}

    // 9x6 inventory
    public static final int SIZE = 54;
    public static final String TITLE = "Faction Claim Map";

    // Visible grid inside GUI: 7 cols x 5 rows = 35 chunks
    // Chosen to leave space for controls
    private static final int GRID_COLS = 7;
    private static final int GRID_ROWS = 5;

    // Grid starts at row 1 col 1 (0-indexed), so slots are centered and clean
    private static final int GRID_START_ROW = 1; // inventory row index (0..5)
    private static final int GRID_START_COL = 1; // col index (0..8)

    // Pan by 5 chunks per click as requested
    public static final int PAN_STEP = 5;

    // Session store
    static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static void open(Player player, FactionManager manager) {
        Session session = new Session(player, manager);
        SESSIONS.put(player.getUniqueId(), session);

        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        session.inventory = inv;

        render(session);
        player.openInventory(inv);
    }

    static void render(Session session) {
        Inventory inv = session.inventory;
        if (inv == null) return;

        inv.clear();

        // Fill background
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        // Controls
inv.setItem(slot(0, 4), named(Material.ARROW, "§bNorth §7(+5 chunks)"));
inv.setItem(slot(2, 0), named(Material.ARROW, "§bWest §7(+5 chunks)"));
inv.setItem(slot(2, 8), named(Material.ARROW, "§bEast §7(+5 chunks)"));
inv.setItem(slot(4, 4), named(Material.ARROW, "§bSouth §7(+5 chunks)")); // symmetrical

        inv.setItem(slot(0, 0), named(Material.PAPER, "§dSelection",
                "§7Click or drag to select chunks.",
                "§7Pan with arrows (moves 5 chunks).",
                "§7Then confirm."
        ));

        inv.setItem(slot(5, 8), named(Material.LIME_WOOL, "§aCONFIRM CLAIM"));
        inv.setItem(slot(5, 0), named(Material.RED_WOOL, "§cCANCEL"));

        // Render chunks in grid
        World world = session.player.getWorld();
        Chunk playerChunk = session.player.getLocation().getChunk();

        int halfX = GRID_COLS / 2; // 3
        int halfZ = GRID_ROWS / 2; // 2

        for (int rz = 0; rz < GRID_ROWS; rz++) {
            for (int rx = 0; rx < GRID_COLS; rx++) {

                int offsetX = rx - halfX;
                int offsetZ = rz - halfZ;

                int chunkX = session.viewCenterX + offsetX;
                int chunkZ = session.viewCenterZ + offsetZ;

                boolean isPlayerHere = (playerChunk.getX() == chunkX && playerChunk.getZ() == chunkZ);

                // selection overlay
                boolean selected = session.hasSelection() && session.isInsideSelection(chunkX, chunkZ);

                FactionManager.Faction ownerFaction = session.manager.getFactionByChunk(world.getName(), chunkX, chunkZ);
                FactionManager.Faction playerFaction = session.manager.getFaction(session.player.getUniqueId());

                ItemStack item;

                if (isPlayerHere) {
                    item = named(Material.LIME_STAINED_GLASS_PANE, "§a+ §7You",
                            "§7Chunk: §f(" + chunkX + ", " + chunkZ + ")",
                            "§7World: §f" + world.getName()
                    );
                } else if (selected) {
                    item = named(Material.YELLOW_STAINED_GLASS_PANE, "§eSelected",
                            "§7Chunk: §f(" + chunkX + ", " + chunkZ + ")",
                            "§7World: §f" + world.getName(),
                            "§7Owner: §f" + (ownerFaction == null ? "Wilderness" : ownerFaction.getName())
                    );
                } else if (ownerFaction == null) {
                    item = named(Material.GRAY_STAINED_GLASS_PANE, "§7Wilderness",
                            "§7Chunk: §f(" + chunkX + ", " + chunkZ + ")",
                            "§7World: §f" + world.getName(),
                            "§7Click or drag to select."
                    );
                } else {
                    boolean own = playerFaction != null && ownerFaction.getName().equalsIgnoreCase(playerFaction.getName());
                    if (own) {
                        item = named(Material.GREEN_STAINED_GLASS_PANE, "§aClaimed: §f" + ownerFaction.getName(),
                                "§7Chunk: §f(" + chunkX + ", " + chunkZ + ")",
                                "§7World: §f" + world.getName()
                        );
                    } else {
                        item = named(Material.RED_STAINED_GLASS_PANE, "§cClaimed: §f" + ownerFaction.getName(),
                                "§7Chunk: §f(" + chunkX + ", " + chunkZ + ")",
                                "§7World: §f" + world.getName()
                        );
                    }
                }

                inv.setItem(gridSlot(rx, rz), item);
            }
        }

        // Show selection stats (top-right)
        String selectionLine = session.hasSelection()
                ? "§f" + session.selectionChunkCount() + "§7 chunks selected"
                : "§7No selection";
        inv.setItem(slot(0, 8), named(Material.MAP, "§dView",
                "§7Center chunk: §f(" + session.viewCenterX + ", " + session.viewCenterZ + ")",
                selectionLine
        ));
    }

    static int gridSlot(int rx, int rz) {
        int row = GRID_START_ROW + rz;
        int col = GRID_START_COL + rx;
        return slot(row, col);
    }

    static boolean isGridSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row >= GRID_START_ROW && row < GRID_START_ROW + GRID_ROWS
                && col >= GRID_START_COL && col < GRID_START_COL + GRID_COLS;
    }

    static int slot(int row, int col) {
        return (row * 9) + col;
    }

    static ItemStack named(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (loreLines != null && loreLines.length > 0) {
            meta.setLore(Arrays.asList(loreLines));
        }
        item.setItemMeta(meta);
        return item;
    }

    public static final class Session {
        final Player player;
        final FactionManager manager;
        Inventory inventory;

        int viewCenterX;
        int viewCenterZ;

        Integer selX1 = null;
        Integer selZ1 = null;
        Integer selX2 = null;
        Integer selZ2 = null;

        Session(Player player, FactionManager manager) {
            this.player = player;
            this.manager = manager;

            Chunk c = player.getLocation().getChunk();
            this.viewCenterX = c.getX();
            this.viewCenterZ = c.getZ();
        }

        boolean hasSelection() {
            return selX1 != null && selZ1 != null && selX2 != null && selZ2 != null;
        }

        void clearSelection() {
            selX1 = selZ1 = selX2 = selZ2 = null;
        }

        void setAnchor(int x, int z) {
            selX1 = x; selZ1 = z;
            selX2 = x; selZ2 = z;
        }

        void setOtherCorner(int x, int z) {
            if (selX1 == null || selZ1 == null) setAnchor(x, z);
            selX2 = x; selZ2 = z;
        }

        boolean isInsideSelection(int x, int z) {
            if (!hasSelection()) return false;
            int minX = Math.min(selX1, selX2);
            int maxX = Math.max(selX1, selX2);
            int minZ = Math.min(selZ1, selZ2);
            int maxZ = Math.max(selZ1, selZ2);
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        int selectionChunkCount() {
            if (!hasSelection()) return 0;
            int minX = Math.min(selX1, selX2);
            int maxX = Math.max(selX1, selX2);
            int minZ = Math.min(selZ1, selZ2);
            int maxZ = Math.max(selZ1, selZ2);
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }
    }
}