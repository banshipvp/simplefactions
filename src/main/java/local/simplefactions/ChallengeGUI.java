package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Handles the /challenges GUI system:
 *
 *   ─ Main view (27 slots, "§6✦ Daily Challenge"):
 *       Center slot has the challenge's theme block. Lore shows top 3 + time left.
 *       Clicking it opens the full leaderboard.
 *
 *   ─ Leaderboard view (54 slots, "§6✦ Challenge Leaderboard"):
 *       Player heads for up to 28 players, with position, name, and score in lore.
 *       Back button at slot 49 returns to the main view.
 */
public class ChallengeGUI implements Listener {

    static final String TITLE_MAIN  = "§6✦ Daily Challenge";
    static final String TITLE_BOARD = "§6✦ Challenge Leaderboard";

    // Slot in main GUI that holds the challenge block
    private static final int SLOT_BLOCK  = 13;
    private static final int SLOT_TIMER  = 11;
    private static final int SLOT_PRIZES = 15;

    private final ChallengeManager manager;
    private final Plugin plugin;

    public ChallengeGUI(ChallengeManager manager, Plugin plugin) {
        this.manager = manager;
        this.plugin  = plugin;
    }

    // ── Open main view ────────────────────────────────────────────────────────

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN);

        ItemStack pane = pane(Material.PURPLE_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        ChallengeManager.ChallengeDefinition current = manager.getCurrent();

        if (current == null) {
            inv.setItem(SLOT_BLOCK, icon(Material.BARRIER, "§cNo Active Challenge",
                    "§7Check back later!"));
            player.openInventory(inv);
            return;
        }

        // ── Timer ────────────────────────────────────────────────────────────
        long secsLeft = manager.secondsRemaining();
        inv.setItem(SLOT_TIMER, icon(Material.CLOCK, "§e⏱ Time Remaining",
                "§f" + ChallengeManager.fmtTime(secsLeft),
                "§8New challenge in " + ChallengeManager.fmtTime(secsLeft)));

        // ── Prize breakdown ───────────────────────────────────────────────────
        inv.setItem(SLOT_PRIZES, icon(Material.GOLD_INGOT, "§6✦ Prizes",
                "§6#1 §f$1,000,000",
                "§7#2 §f$500,000",
                "§c#3 §f$250,000",
                "§8Run §e/claim §8after challenge ends."));

        // ── Challenge block (main clickable) ──────────────────────────────────
        List<Map.Entry<UUID, Long>> top3  = manager.getLeaderboard(3);
        Map<UUID, String>             names = manager.getNames();

        List<String> lore = new ArrayList<>();
        lore.add("§7" + current.description);
        lore.add("§8─────────────────────────");

        String[] medals = {"§6§l#1", "§7§l#2", "§c§l#3"};
        if (top3.isEmpty()) {
            lore.add("§7No scores yet — be the first!");
        } else {
            for (int i = 0; i < top3.size(); i++) {
                String name  = names.getOrDefault(top3.get(i).getKey(), "?");
                long   score = top3.get(i).getValue();
                lore.add(medals[i] + " §f" + name + " §8— §e" + ChallengeManager.fmt(score));
            }
        }
        lore.add("§8─────────────────────────");
        lore.add("§aClick to view full leaderboard");

        inv.setItem(SLOT_BLOCK, buildIcon(current.icon,
                "§6§l" + current.displayName, lore));

        player.openInventory(inv);
    }

    // ── Open leaderboard ──────────────────────────────────────────────────────

    public void openLeaderboard(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_BOARD);

        ChallengeManager.ChallengeDefinition current = manager.getCurrent();

        // Header row (0-8): purple panes, challenge icon at slot 4
        ItemStack hPane = pane(Material.PURPLE_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, hPane);
        if (current != null) {
            inv.setItem(4, buildIcon(current.icon,
                    "§6§l" + current.displayName,
                    List.of("§7" + current.description,
                            "§8─────────────────────────",
                            "§7Time left: §e" + ChallengeManager.fmtTime(manager.secondsRemaining()))));
        }

        // Footer row (45-53): gray panes, back button at 49
        ItemStack fPane = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inv.setItem(i, fPane);
        inv.setItem(49, icon(Material.ARROW, "§7◀ Back",
                "§7Return to challenge overview"));

        // Side columns: panes at col 0 and col 8 for rows 1-4
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     pane(Material.GRAY_STAINED_GLASS_PANE));
            inv.setItem(row * 9 + 8, pane(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Player heads: valid slots 10-16, 19-25, 28-34, 37-43  (7 per row, 4 rows = 28 total)
        List<Map.Entry<UUID, Long>> board = manager.getLeaderboard(28);
        Map<UUID, String> names = manager.getNames();
        String[] medals = {"§6§l#1", "§7§l#2", "§c§l#3"};

        int[] slots = buildSlots();
        for (int i = 0; i < Math.min(board.size(), slots.length); i++) {
            UUID uuid  = board.get(i).getKey();
            long score = board.get(i).getValue();
            String dname = names.getOrDefault(uuid, "?");
            String rank  = i < medals.length ? medals[i] : "§f#" + (i + 1);
            long   prize = i < ChallengeManager.PRIZES.length ? ChallengeManager.PRIZES[i] : 0;

            ItemStack head = playerHead(uuid, dname,
                    rank + " §f" + dname,
                    "§7Score: §e" + ChallengeManager.fmt(score),
                    prize > 0 ? "§7Prize: §6$" + ChallengeManager.fmt(prize) : "§8No prize");
            inv.setItem(slots[i], head);
        }

        if (board.isEmpty()) {
            inv.setItem(22, icon(Material.PAPER, "§7No scores yet",
                    "§7Be the first to compete!"));
        }

        player.openInventory(inv);
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String title = e.getView().getTitle();
        if (!title.equals(TITLE_MAIN) && !title.equals(TITLE_BOARD)) return;
        e.setCancelled(true);

        if (e.getClickedInventory() == null
                || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (title.equals(TITLE_MAIN) && e.getRawSlot() == SLOT_BLOCK) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openLeaderboard(player), 1L);
        } else if (title.equals(TITLE_BOARD) && e.getRawSlot() == 49) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openMain(player), 1L);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Valid content slots for the leaderboard: slots 10-16, 19-25, 28-34, 37-43 */
    private static int[] buildSlots() {
        int[] slots = new int[28];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[idx++] = row * 9 + col;
            }
        }
        return slots;
    }

    private static ItemStack playerHead(UUID uuid, String ownerName, String displayName, String... lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.setDisplayName(displayName);
        meta.setLore(Arrays.asList(lore));
        head.setItemMeta(meta);
        return head;
    }

    private static ItemStack buildIcon(Material mat, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack icon(Material mat, String name, String... lore) {
        return buildIcon(mat, name, Arrays.asList(lore));
    }

    private static ItemStack pane(Material mat) {
        ItemStack p = new ItemStack(mat);
        ItemMeta m  = p.getItemMeta();
        m.setDisplayName("§r");
        p.setItemMeta(m);
        return p;
    }
}
