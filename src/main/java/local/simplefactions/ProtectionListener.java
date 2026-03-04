package local.simplefactions;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ProtectionListener implements Listener {

    private final FactionManager manager;

    public ProtectionListener(FactionManager manager) {
        this.manager = manager;
    }

    /**
     * Prevent spawners from actually spawning mobs.
     * Spawners are purely economic objects on this server — their value ramps
     * up in faction territory, but they do not produce live mobs.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        String world = event.getBlock().getWorld().getName();
        int x = event.getBlock().getChunk().getX();
        int z = event.getBlock().getChunk().getZ();

        String owner = manager.getClaimOwner(world, x, z);
        if (owner == null) {
            // Edge-case: tracked spawner in now-unclaimed chunk – clean up tracking
            if (event.getBlock().getType() == Material.SPAWNER) {
                Block b = event.getBlock();
                manager.onSpawnerRemoved(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            }
            return;
        }

        if (!hasClaimAccess(event.getPlayer().getUniqueId(), owner, world, x, z, ClaimAccessPermission.BREAK_BLOCKS)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot break blocks here.");
            return;
        }

        // Break is permitted – handle spawner tracking
        if (event.getBlock().getType() == Material.SPAWNER) {
            Block b = event.getBlock();

            // If this is a stacked spawner (count > 1): pop one off, give item, keep the block
            if (manager.popSpawnerFromStack(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), event.getPlayer())) {
                event.setCancelled(true);
                return;
            }

            // Stack is count == 1 (or no stack manager): let the block break, clean up tracking
            PlacedSpawnerRecord removed = manager.onSpawnerRemoved(
                    b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            if (removed != null) {
                double lostValue = removed.getCurrentValue();
                SpawnerType st = SpawnerType.fromEntityKey(removed.getEntityType());
                String stName = st != null ? st.getDisplayName() : removed.getEntityType();
                event.getPlayer().sendMessage(
                        ChatColor.YELLOW + "⚠ " + stName + " Spawner removed from faction territory."
                        + ChatColor.GRAY + " (was worth §e$" + String.format("%,.0f", lostValue) + ChatColor.GRAY + ")");
            }
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
            return;
        }

        // Placement is permitted – register spawner tracking if applicable
        if (event.getBlockPlaced().getType() == Material.SPAWNER) {
            String entityTypeKey = "pig"; // fallback
            ItemStack item = event.getItemInHand();
            if (item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta bsm) {
                BlockState bState = bsm.getBlockState();
                if (bState instanceof CreatureSpawner cs && cs.getSpawnedType() != null) {
                    entityTypeKey = cs.getSpawnedType().name().toLowerCase();
                }
            }
            Block b = event.getBlock();
            manager.onSpawnerPlaced(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(),
                    entityTypeKey, owner);

            // Inform the player of the ramp-up schedule
            double base = SpawnerType.getBaseValue(entityTypeKey);
            SpawnerType st = SpawnerType.fromEntityKey(entityTypeKey);
            String stName = st != null ? st.getDisplayName() : entityTypeKey;
            event.getPlayer().sendMessage(ChatColor.GREEN + "✦ " + stName + " Spawner added to faction territory!");
            event.getPlayer().sendMessage(
                    ChatColor.YELLOW + "  Now: §e$" + String.format("%,.0f", base * 0.5)
                    + ChatColor.YELLOW + "  →  24h: §e$" + String.format("%,.0f", base * 0.75)
                    + ChatColor.YELLOW + "  →  48h: §e$" + String.format("%,.0f", base));
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
        // Collect spawners that will actually be destroyed (not protected by TNT flag)
        // so we can remove them from tracking after the block list is finalised.
        java.util.List<Block> spawnersToDrop = new java.util.ArrayList<>();

        event.blockList().removeIf(block -> {
            String world = block.getWorld().getName();
            int x = block.getChunk().getX();
            int z = block.getChunk().getZ();

            String owner = manager.getClaimOwner(world, x, z);
            if (owner == null) return false;

            FactionManager.Faction ownerFaction = manager.getFactionByName(owner);
            if (ownerFaction == null) return false;

            // Warzone (and any system faction) is always explosion-proof
            if (manager.isSystemFaction(ownerFaction)) return true;

            boolean tntProtected = !ownerFaction.isFlagEnabled(ClaimFlag.TNT);
            // If this spawner will be destroyed, queue it for tracking removal
            if (!tntProtected && block.getType() == Material.SPAWNER) {
                spawnersToDrop.add(block);
            }
            return tntProtected;
        });

        // Remove destroyed spawners from the tracker
        for (Block b : spawnersToDrop) {
            manager.onSpawnerRemoved(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        }
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