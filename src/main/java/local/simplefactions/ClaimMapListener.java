package local.simplefactions;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

public final class ClaimMapListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getView() == null) return;
        if (!ClaimMapGui.TITLE.equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        ClaimMapGui.Session session = ClaimMapGui.SESSIONS.get(player.getUniqueId());
        if (session == null) return;

        int slot = e.getRawSlot();

        // Cancel
        if (slot == ClaimMapGui.slot(5, 0)) {
            session.clearSelection();
            player.closeInventory();
            player.sendMessage("§cClaim selection cancelled.");
            return;
        }

        // Confirm
        if (slot == ClaimMapGui.slot(5, 8)) {
            confirmClaim(session);
            return;
        }

        // Pan controls (moves 5 chunks per click)
        if (slot == ClaimMapGui.slot(0, 4)) { // North
            session.viewCenterZ -= ClaimMapGui.PAN_STEP;
            ClaimMapGui.render(session);
            return;
        }
if (slot == ClaimMapGui.slot(4, 4)) { // South
    session.viewCenterZ += ClaimMapGui.PAN_STEP;
    ClaimMapGui.render(session);
    return;
}
        if (slot == ClaimMapGui.slot(2, 0)) { // West
            session.viewCenterX -= ClaimMapGui.PAN_STEP;
            ClaimMapGui.render(session);
            return;
        }
        if (slot == ClaimMapGui.slot(2, 8)) { // East
            session.viewCenterX += ClaimMapGui.PAN_STEP;
            ClaimMapGui.render(session);
            return;
        }

        // Click selection in grid
        if (!ClaimMapGui.isGridSlot(slot)) return;

        int[] chunk = slotToChunk(session, slot);
        int chunkX = chunk[0];
        int chunkZ = chunk[1];

        // First click sets anchor, subsequent click sets second corner
        if (session.selX1 == null) {
            session.setAnchor(chunkX, chunkZ);
        } else {
            session.setOtherCorner(chunkX, chunkZ);
        }

        ClaimMapGui.render(session);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getView() == null) return;
        if (!ClaimMapGui.TITLE.equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        ClaimMapGui.Session session = ClaimMapGui.SESSIONS.get(player.getUniqueId());
        if (session == null) return;

        // Find the last grid slot dragged over, use it as the other corner
        Integer lastGridSlot = null;
        for (int raw : e.getRawSlots()) {
            if (ClaimMapGui.isGridSlot(raw)) lastGridSlot = raw;
        }
        if (lastGridSlot == null) return;

        int[] chunk = slotToChunk(session, lastGridSlot);
        int chunkX = chunk[0];
        int chunkZ = chunk[1];

        if (session.selX1 == null) {
            // If they start dragging without clicking, anchor becomes first cell
            session.setAnchor(chunkX, chunkZ);
        } else {
            session.setOtherCorner(chunkX, chunkZ);
        }

        ClaimMapGui.render(session);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (e.getView() == null) return;
        if (!ClaimMapGui.TITLE.equals(e.getView().getTitle())) return;

        // Cleanup session on close
        UUID id = player.getUniqueId();
        ClaimMapGui.SESSIONS.remove(id);
    }

    private void confirmClaim(ClaimMapGui.Session session) {
        Player player = session.player;

        FactionManager.Faction faction = session.manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }
        if (!session.manager.isLeader(player)) {
            player.sendMessage("§cOnly the leader can claim land.");
            return;
        }
        if (!session.hasSelection()) {
            player.sendMessage("§cSelect an area first.");
            return;
        }

        World world = player.getWorld();
        int minX = Math.min(session.selX1, session.selX2);
        int maxX = Math.max(session.selX1, session.selX2);
        int minZ = Math.min(session.selZ1, session.selZ2);
        int maxZ = Math.max(session.selZ1, session.selZ2);

        int claimed = 0;
        int blocked = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Skip if already claimed
                if (session.manager.getFactionByChunk(world.getName(), x, z) != null) {
                    blocked++;
                    continue;
                }
                if (session.manager.claimChunk(faction, world.getName(), x, z)) {
                    claimed++;
                }
            }
        }

        player.sendMessage("§aClaimed §f" + claimed + "§a chunks.");
        if (blocked > 0) player.sendMessage("§7Skipped §f" + blocked + "§7 chunks already claimed.");

        session.clearSelection();
        ClaimMapGui.render(session);
    }

    private int[] slotToChunk(ClaimMapGui.Session session, int slot) {
        int row = slot / 9;
        int col = slot % 9;

        int rx = col - 1; // GRID_START_COL = 1
        int rz = row - 1; // GRID_START_ROW = 1

        int halfX = 7 / 2; // 3
        int halfZ = 5 / 2; // 2

        int offsetX = rx - halfX;
        int offsetZ = rz - halfZ;

        int chunkX = session.viewCenterX + offsetX;
        int chunkZ = session.viewCenterZ + offsetZ;

        return new int[]{chunkX, chunkZ};
    }
}