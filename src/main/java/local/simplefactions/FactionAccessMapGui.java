package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chunk-selection GUI for {@code /f access p <player>}.
 *
 * <p>Shows all of the invoking player's faction claims as clickable glass-pane
 * cells.  Clicking a cell toggles selection (LIME = selected, GRAY = not
 * selected).  The bottom row contains Cancel (slot 45) and Confirm (slot 53).
 * On Confirm, access is granted for every selected chunk and the existing
 * per-permission editor is opened.
 */
public class FactionAccessMapGui implements Listener {

    // ── Slots ─────────────────────────────────────────────────────────────────
    private static final int SLOT_CANCEL  = 45;
    private static final int SLOT_CONFIRM = 53;
    private static final int MAX_CLAIM_SLOTS = 44; // slots 0-44 are content cells

    // ── State ─────────────────────────────────────────────────────────────────
    /** viewer UUID → chunk keys ("world:cx:cz") currently selected */
    private final Map<UUID, Set<String>> selectedChunks = new HashMap<>();

    private final FactionManager       factionManager;
    private final FactionAccessGui     permEditor;

    // ── Constants for lore parsing ────────────────────────────────────────────
    private static final String KEY_PREFIX = "§8key:";

    // ── Constructor ───────────────────────────────────────────────────────────

    public FactionAccessMapGui(FactionManager factionManager, FactionAccessGui permEditor) {
        this.factionManager = factionManager;
        this.permEditor     = permEditor;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Opens the chunk-selection map GUI for the given viewer to choose chunks
     * for {@code target}.
     */
    public void open(Player viewer, UUID target) {
        FactionManager.Faction faction = factionManager.getFaction(viewer.getUniqueId());
        if (faction == null) {
            viewer.sendMessage("§cYou are not in a faction.");
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(target);
        String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : target.toString().substring(0, 8);
        String title = "§6Select Chunks §7— §f" + targetName;

        selectedChunks.put(viewer.getUniqueId(), new HashSet<>());

        Inventory gui = Bukkit.createInventory(new MapSelectHolder(viewer.getUniqueId(), target), 54, title);

        // Populate claim slots
        List<String> claims = new ArrayList<>(faction.getClaims());
        // Sort for consistent display
        claims.sort(String::compareToIgnoreCase);

        for (int i = 0; i < Math.min(claims.size(), MAX_CLAIM_SLOTS + 1); i++) {
            gui.setItem(i, buildChunkItem(claims.get(i), false));
        }

        // Bottom row separators
        ItemStack separator = makeNamedItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 45; slot <= 53; slot++) {
            gui.setItem(slot, separator);
        }

        // Cancel button
        ItemStack cancel = makeNamedItem(Material.RED_DYE, "§cCancel");
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setLore(List.of("§7Click to close without granting access."));
        cancel.setItemMeta(cancelMeta);
        gui.setItem(SLOT_CANCEL, cancel);

        // Confirm button
        ItemStack confirm = makeNamedItem(Material.LIME_DYE, "§aConfirm Selection");
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setLore(List.of("§7Grants access to selected chunks,", "§7then opens the permission editor."));
        confirm.setItemMeta(confirmMeta);
        gui.setItem(SLOT_CONFIRM, confirm);

        viewer.openInventory(gui);
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof MapSelectHolder holder)) return;
        if (!holder.viewer().equals(player.getUniqueId())) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        // Ignore clicks in the player's own inventory
        if (slot >= top.getSize()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // ── Cancel ────────────────────────────────────────────────────────────
        if (slot == SLOT_CANCEL) {
            player.closeInventory();
            selectedChunks.remove(player.getUniqueId());
            return;
        }

        // ── Confirm ───────────────────────────────────────────────────────────
        if (slot == SLOT_CONFIRM) {
            confirm(player, holder.viewer(), holder.target(), top);
            return;
        }

        // ── Separator / empty ─────────────────────────────────────────────────
        if (slot >= MAX_CLAIM_SLOTS + 1) return;  // bottom row with chunk==null

        // ── Chunk cell toggle ─────────────────────────────────────────────────
        String chunkKey = extractChunkKey(clicked);
        if (chunkKey == null) return;

        Set<String> selected = selectedChunks.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        boolean nowSelected;
        if (selected.contains(chunkKey)) {
            selected.remove(chunkKey);
            nowSelected = false;
        } else {
            selected.add(chunkKey);
            nowSelected = true;
        }

        top.setItem(slot, buildChunkItem(chunkKey, nowSelected));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MapSelectHolder)) return;
        selectedChunks.remove(player.getUniqueId());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void confirm(Player viewer, UUID viewerUuid, UUID target, Inventory gui) {
        FactionManager.Faction faction = factionManager.getFaction(viewerUuid);
        if (faction == null) {
            viewer.sendMessage("§cFaction not found.");
            viewer.closeInventory();
            return;
        }

        Set<String> selected = selectedChunks.getOrDefault(viewerUuid, Set.of());

        if (selected.isEmpty()) {
            viewer.sendMessage("§eNo chunks selected. Click chunks to select them, then Confirm.");
            return;
        }

        int granted = 0;
        for (String key : selected) {
            String[] parts = key.split(":");
            if (parts.length != 3) continue;
            try {
                String world  = parts[0];
                int    chunkX = Integer.parseInt(parts[1]);
                int    chunkZ = Integer.parseInt(parts[2]);
                boolean ok = factionManager.grantAccessChunk(viewerUuid, target, world, chunkX, chunkZ);
                if (ok) granted++;
            } catch (NumberFormatException ignored) { /* skip malformed keys */ }
        }

        selectedChunks.remove(viewerUuid);
        viewer.closeInventory();

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(target);
        String tName = targetPlayer.getName() != null ? targetPlayer.getName() : target.toString().substring(0, 8);
        viewer.sendMessage("§aGranted §f" + tName + "§a access to §f" + granted + "§a chunk(s). Edit permissions below:");

        // Open the permission editor
        permEditor.openPermissionEditorPublic(viewer, target);
    }

    private ItemStack buildChunkItem(String chunkKey, boolean selected) {
        // Parse key: "world:chunkX:chunkZ"
        String[] parts = chunkKey.split(":");
        String world  = parts.length > 0 ? parts[0] : "?";
        String cx     = parts.length > 1 ? parts[1] : "?";
        String cz     = parts.length > 2 ? parts[2] : "?";

        Material mat = selected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((selected ? "§a✔ " : "§7◻ ") + "§f" + cx + "x, " + cz + "z");
        meta.setLore(List.of(
                "§7World: §f" + world,
                "§7Chunk: §f(" + cx + ", " + cz + ")",
                "",
                selected ? "§aSelected §7— click to deselect" : "§7Click to select",
                KEY_PREFIX + chunkKey
        ));
        item.setItemMeta(meta);
        return item;
    }

    private String extractChunkKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return null;
        for (String line : lore) {
            if (line.startsWith(KEY_PREFIX)) {
                return line.substring(KEY_PREFIX.length());
            }
        }
        return null;
    }

    private ItemStack makeNamedItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    // ── Holder records ────────────────────────────────────────────────────────

    private record MapSelectHolder(UUID viewer, UUID target) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
