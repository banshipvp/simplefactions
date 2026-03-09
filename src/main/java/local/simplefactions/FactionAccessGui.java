package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FactionAccessGui implements Listener {

    private final FactionManager manager;

    public FactionAccessGui(FactionManager manager) {
        this.manager = manager;
    }

    public void openPlayerSelector(Player viewer) {
        FactionManager.Faction faction = manager.getFaction(viewer.getUniqueId());
        if (faction == null) {
            viewer.sendMessage("§cYou are not in a faction.");
            return;
        }

        Inventory inventory = Bukkit.createInventory(new AccessPlayerListHolder(viewer.getUniqueId()), 54, "Faction Access Players");

        int slot = 0;
        for (UUID targetId : manager.getAccessPlayers(faction)) {
            if (slot >= inventory.getSize() - 9) break;

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetId);
            String name = offlinePlayer.getName() == null ? targetId.toString().substring(0, 8) : offlinePlayer.getName();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
            skullMeta.setOwningPlayer(offlinePlayer);
            skullMeta.setDisplayName("§e" + name);

            List<String> lore = new ArrayList<>();
            lore.add("§7Click to edit permissions");
            lore.add("§7Scope: " + (manager.hasAllClaimsAccess(faction, targetId) ? "§aAll Claims" : "§b" + manager.getSpecificChunkAccessCount(faction, targetId) + " Chunks"));
            lore.add("");

            for (ClaimAccessPermission permission : ClaimAccessPermission.values()) {
                boolean allowed = manager.getAccessPermissions(faction, targetId).contains(permission);
                lore.add((allowed ? "§a✔ " : "§c✖ ") + "§7" + formatPermission(permission));
            }

            skullMeta.setLore(lore);
            head.setItemMeta(skullMeta);
            inventory.setItem(slot++, head);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6How to Add Players");
        infoMeta.setLore(List.of(
                "§7Use /f access p <player>",
                "§7Use /f access p <player> all",
                "§7Use /f access p <player> radius <amount>",
                "§7Then edit with this GUI"
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(49, info);

        viewer.openInventory(inventory);
    }

    /** Called by FactionAccessMapGui after chunk selection is confirmed. */
    public void openPermissionEditorPublic(Player viewer, UUID targetId) {
        openPermissionEditor(viewer, targetId);
    }

    private void openPermissionEditor(Player viewer, UUID targetId) {
        FactionManager.Faction faction = manager.getFaction(viewer.getUniqueId());
        if (faction == null) {
            viewer.sendMessage("§cYou are not in a faction.");
            return;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetId);
        String name = offlinePlayer.getName() == null ? targetId.toString().substring(0, 8) : offlinePlayer.getName();

        Inventory inventory = Bukkit.createInventory(new AccessPermEditorHolder(viewer.getUniqueId(), targetId), 27, "Perms: " + name);

        int slot = 10;
        for (ClaimAccessPermission permission : ClaimAccessPermission.values()) {
            boolean enabled = manager.getAccessPermissions(faction, targetId).contains(permission);
            Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((enabled ? "§a" : "§c") + formatPermission(permission));
            meta.setLore(List.of(
                    "§7Current: " + (enabled ? "§aEnabled" : "§cDisabled"),
                    "§eClick to toggle"
            ));
            item.setItemMeta(meta);

            inventory.setItem(slot++, item);
            if (slot == 13) slot = 14;
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§eBack");
        back.setItemMeta(backMeta);
        inventory.setItem(22, back);

        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory == null) return;

        InventoryHolder holder = topInventory.getHolder();
        if (!(holder instanceof AccessPlayerListHolder) && !(holder instanceof AccessPermEditorHolder)) {
            return;
        }

        event.setCancelled(true);

        if (holder instanceof AccessPlayerListHolder listHolder) {
            if (!listHolder.viewer().equals(player.getUniqueId())) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta == null || meta.getOwningPlayer() == null) return;

            openPermissionEditor(player, meta.getOwningPlayer().getUniqueId());
            return;
        }

        AccessPermEditorHolder editorHolder = (AccessPermEditorHolder) holder;
        if (!editorHolder.viewer().equals(player.getUniqueId())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        if (clicked.getType() == Material.ARROW) {
            openPlayerSelector(player);
            return;
        }

        int slot = event.getRawSlot();
        ClaimAccessPermission permission = permissionByEditorSlot(slot);
        if (permission == null) return;

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.closeInventory();
            return;
        }

        boolean currentlyAllowed = manager.getAccessPermissions(faction, editorHolder.target()).contains(permission);
        manager.setAccessPermission(player.getUniqueId(), editorHolder.target(), permission, !currentlyAllowed);
        openPermissionEditor(player, editorHolder.target());
    }

    private ClaimAccessPermission permissionByEditorSlot(int slot) {
        return switch (slot) {
            case 10 -> ClaimAccessPermission.BREAK_BLOCKS;
            case 11 -> ClaimAccessPermission.PLACE_BLOCKS;
            case 12 -> ClaimAccessPermission.OPEN_CONTAINERS;
            case 14 -> ClaimAccessPermission.USE_DOORS;
            case 15 -> ClaimAccessPermission.USE_REDSTONE;
            case 16 -> ClaimAccessPermission.USE_TNT;
            default -> null;
        };
    }

    private String formatPermission(ClaimAccessPermission permission) {
        return switch (permission) {
            case BREAK_BLOCKS -> "Break Blocks";
            case PLACE_BLOCKS -> "Place Blocks";
            case OPEN_CONTAINERS -> "Open Containers";
            case USE_DOORS -> "Use Doors / Gates";
            case USE_REDSTONE -> "Use Buttons / Levers";
            case USE_TNT -> "Use TNT";
        };
    }

    private record AccessPlayerListHolder(UUID viewer) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record AccessPermEditorHolder(UUID viewer, UUID target) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
