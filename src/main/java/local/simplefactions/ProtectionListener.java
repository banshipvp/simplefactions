package local.simplefactions;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class ProtectionListener implements Listener {

    private final FactionManager manager;

    public ProtectionListener(FactionManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {

        String world = e.getBlock().getWorld().getName();
        int x = e.getBlock().getChunk().getX();
        int z = e.getBlock().getChunk().getZ();

        String owner = manager.getClaimOwner(world, x, z);
        if (owner == null) return;

        FactionManager.Faction playerFaction =
                manager.getFaction(e.getPlayer().getUniqueId());

        if (playerFaction == null ||
            !playerFaction.getName().equalsIgnoreCase(owner)) {

            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "You cannot break blocks here.");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {

        String world = e.getBlock().getWorld().getName();
        int x = e.getBlock().getChunk().getX();
        int z = e.getBlock().getChunk().getZ();

        String owner = manager.getClaimOwner(world, x, z);
        if (owner == null) return;

        FactionManager.Faction playerFaction =
                manager.getFaction(e.getPlayer().getUniqueId());

        if (playerFaction == null ||
            !playerFaction.getName().equalsIgnoreCase(owner)) {

            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "You cannot place blocks here.");
        }
    }
}