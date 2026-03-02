package local.simplefactions;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ProtectionListener implements Listener {

    private final FactionManager manager;

    public ProtectionListener(FactionManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        String world = event.getBlock().getWorld().getName();
        int x = event.getBlock().getChunk().getX();
        int z = event.getBlock().getChunk().getZ();

        String owner = manager.getClaimOwner(world, x, z);
        if (owner == null) return;

        if (!hasClaimAccess(event.getPlayer().getUniqueId(), owner, world, x, z, ClaimAccessPermission.BREAK_BLOCKS)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot break blocks here.");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        String world = event.getBlock().getWorld().getName();
        int x = event.getBlock().getChunk().getX();
        int z = event.getBlock().getChunk().getZ();

        String owner = manager.getClaimOwner(world, x, z);
        if (owner == null) return;

        ClaimAccessPermission needed = event.getBlockPlaced().getType() == Material.TNT
                ? ClaimAccessPermission.USE_TNT
                : ClaimAccessPermission.PLACE_BLOCKS;

        if (!hasClaimAccess(event.getPlayer().getUniqueId(), owner, world, x, z, needed)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place blocks here.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        String world = clicked.getWorld().getName();
        int x = clicked.getChunk().getX();
        int z = clicked.getChunk().getZ();

        String owner = manager.getClaimOwner(world, x, z);
        if (owner == null) return;

        ClaimAccessPermission needed;
        if (isContainer(clicked.getType())) {
            needed = ClaimAccessPermission.OPEN_CONTAINERS;
        } else if (isDoorOrGate(clicked.getType())) {
            needed = ClaimAccessPermission.USE_DOORS;
        } else if (isRedstoneInteractive(clicked.getType())) {
            needed = ClaimAccessPermission.USE_REDSTONE;
        } else {
            return;
        }

        if (!hasClaimAccess(event.getPlayer().getUniqueId(), owner, world, x, z, needed)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You don't have access for that in this claim.");
        }
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() == null) return;

        String world = event.getBlock().getWorld().getName();
        int x = event.getBlock().getChunk().getX();
        int z = event.getBlock().getChunk().getZ();

        String owner = manager.getClaimOwner(world, x, z);
        if (owner == null) return;

        if (!hasClaimAccess(event.getPlayer().getUniqueId(), owner, world, x, z, ClaimAccessPermission.USE_TNT)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot ignite blocks/TNT in this claim.");
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            String world = block.getWorld().getName();
            int x = block.getChunk().getX();
            int z = block.getChunk().getZ();

            String owner = manager.getClaimOwner(world, x, z);
            if (owner == null) return false;

            FactionManager.Faction ownerFaction = manager.getFactionByName(owner);
            if (ownerFaction == null) return false;
            return !ownerFaction.isFlagEnabled(ClaimFlag.TNT);
        });
    }

    private boolean hasClaimAccess(java.util.UUID playerId, String ownerFactionName, String world, int x, int z, ClaimAccessPermission permission) {
        FactionManager.Faction playerFaction = manager.getFaction(playerId);
        if (playerFaction != null && playerFaction.getName().equalsIgnoreCase(ownerFactionName)) {
            return true;
        }
        return manager.canAccessClaim(playerId, world, x, z, permission);
    }

    private boolean isContainer(Material material) {
        return material == Material.CHEST
                || material == Material.TRAPPED_CHEST
                || material == Material.BARREL
                || material == Material.SHULKER_BOX
                || material.name().endsWith("_SHULKER_BOX")
                || material == Material.FURNACE
                || material == Material.BLAST_FURNACE
                || material == Material.SMOKER
                || material == Material.HOPPER
                || material == Material.DISPENSER
                || material == Material.DROPPER;
    }

    private boolean isDoorOrGate(Material material) {
        String name = material.name();
        return name.endsWith("_DOOR") || name.endsWith("_FENCE_GATE") || name.endsWith("_TRAPDOOR");
    }

    private boolean isRedstoneInteractive(Material material) {
        String name = material.name();
        return name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE")
                || material == Material.LEVER
                || material == Material.REPEATER
                || material == Material.COMPARATOR;
    }
}