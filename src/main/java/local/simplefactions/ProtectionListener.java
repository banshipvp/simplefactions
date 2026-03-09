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
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ProtectionListener implements Listener {

    private final FactionManager manager;
    private final EconomyManager economyManager;
    private final PlayerRankManager rankManager;
    private final WarzoneManager warzoneManager;

    public ProtectionListener(FactionManager manager, EconomyManager economyManager, PlayerRankManager rankManager, WarzoneManager warzoneManager) {
        this.manager = manager;
        this.economyManager = economyManager;
        this.rankManager = rankManager;
        this.warzoneManager = warzoneManager;
    }

    /**
     * Prevent spawners from actually spawning mobs.
     * Spawners are purely economic objects on this server — their value ramps
     * up in faction territory, but they do not produce live mobs.
     *
     * We also cancel ALL natural/environmental mob spawning (hostile and passive).
     * The only mobs that should exist are plugin-spawned ones (CUSTOM reason)
     * from the faction spawner system.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {
            // Allow: plugin-spawned mobs (faction spawner system, arenas, etc.)
            case CUSTOM:
            // Allow: player-built constructs
            case BUILD_SNOWMAN:
            case BUILD_IRONGOLEM:
            case BUILD_WITHER:
            // Allow: commands (/summon, admin tools)
            case COMMAND:
                return;
            // Everything else — natural, chunk-gen, vanilla spawner blocks,
            // village invasions, jockeys, reinforcements, breeding, etc. — all cancelled.
            default:
                event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWardenAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager().getType() != EntityType.WARDEN) return;
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        String world = event.getBlock().getWorld().getName();
        int x = event.getBlock().getChunk().getX();
        int z = event.getBlock().getChunk().getZ();

        String owner = manager.getClaimOwner(world, x, z);
        if (owner == null) {
            // Edge-case: tracked spawner in now-unclaimed chunk – still handle silk touch
            if (event.getBlock().getType() == Material.SPAWNER) {
                Block b = event.getBlock();
                manager.onSpawnerRemoved(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
                org.bukkit.enchantments.Enchantment silkKey =
                        org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("silk_touch"));
                boolean hasSilk = silkKey != null && tool != null && tool.containsEnchantment(silkKey);
                if (hasSilk) {
                    event.setCancelled(true);
                    b.setType(Material.AIR);
                    manager.popWholeSpawnerStack(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), event.getPlayer());
                }
            }
            return;
        }

        if (!hasClaimAccess(event.getPlayer(), owner, world, x, z, ClaimAccessPermission.BREAK_BLOCKS)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot break blocks here.");
            return;
        }

        // Break is permitted – handle spawner tracking
        if (event.getBlock().getType() == Material.SPAWNER) {
            Block b = event.getBlock();

            String spawnerType = resolveSpawnerEntityType(b);
            double mineCost = SpawnerType.getBaseValue(spawnerType) * 0.10;

            ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
            // Paper 1.21: Enchantment.SILK_TOUCH static constant is null – must use getByKey()
            org.bukkit.enchantments.Enchantment silkTouchEnchant =
                    org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("silk_touch"));
            boolean hasSilkTouch = silkTouchEnchant != null && tool != null && tool.containsEnchantment(silkTouchEnchant);

            // Creative mode: admins can break spawners freely — deregister without silk touch requirement
            if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
                event.setCancelled(true);
                b.setType(Material.AIR);
                manager.onSpawnerRemoved(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                SpawnerStackManager ssm = manager.getSpawnerStackManager();
                if (ssm != null) ssm.removeStack(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
                return;
            }

            if (!hasSilkTouch) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "You need Silk Touch to mine spawners.");
                return;
            }

            if (mineCost > 0 && economyManager != null && economyManager.isEnabled()) {
                if (!economyManager.has(event.getPlayer(), mineCost)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "You need $" + String.format("%,.0f", mineCost) + " to mine this spawner.");
                    return;
                }
                if (!economyManager.withdrawPlayer(event.getPlayer(), mineCost)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "Failed to process mining payment.");
                    return;
                }
                event.getPlayer().sendMessage(ChatColor.YELLOW + "Paid $" + String.format("%,.0f", mineCost) + " to mine this spawner.");
            }

            // Give the ENTIRE stack (1 or more spawners) and remove the block
            event.setCancelled(true);
            b.setType(Material.AIR);
            manager.popWholeSpawnerStack(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), event.getPlayer());
        }
    }

    private String resolveSpawnerEntityType(Block block) {
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner spawner && spawner.getSpawnedType() != null) {
            return spawner.getSpawnedType().name().toLowerCase();
        }
        SpawnerStack stack = manager.getSpawnerStackManager() != null
                ? manager.getSpawnerStackManager().getStack(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())
                : null;
        if (stack != null) {
            return stack.getEntityTypeKey();
        }
        return "pig";
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

        if (!hasClaimAccess(event.getPlayer(), owner, world, x, z, needed)) {
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

        // Always block utility block interactions in warzone/safezone (via warzoneManager)
        if (warzoneManager != null) {
            WarzoneManager.WarzoneType zone = warzoneManager.getZoneTypeAt(clicked.getLocation());
            if (zone != null && isRestrictedInZone(clicked.getType())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "You cannot use that here.");
                return;
            }
        }

        String owner = manager.getClaimOwner(world, x, z);
        if (owner == null) return;

        ClaimAccessPermission needed;
        if (isContainer(clicked.getType())) {
            needed = ClaimAccessPermission.OPEN_CONTAINERS;
        } else if (isDoorOrGate(clicked.getType())) {
            needed = ClaimAccessPermission.USE_DOORS;
        } else if (isRedstoneInteractive(clicked.getType())) {
            needed = ClaimAccessPermission.USE_REDSTONE;
        } else if (isRestrictedInZone(clicked.getType())) {
            // Anvils, enchanting tables, beacons — require build access in any claim
            needed = ClaimAccessPermission.BREAK_BLOCKS;
        } else {
            return;
        }

        if (!hasClaimAccess(event.getPlayer(), owner, world, x, z, needed)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You don't have access for that in this claim.");
        }
    }

    /**
     * Prevent players from editing signs in any warzone, safezone, or system-faction claim.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSignEdit(SignChangeEvent event) {
        Block block = event.getBlock();
        String world = block.getWorld().getName();
        int x = block.getChunk().getX();
        int z = block.getChunk().getZ();

        // Block sign editing in warzone/safezone regions
        if (warzoneManager != null) {
            WarzoneManager.WarzoneType zone = warzoneManager.getZoneTypeAt(block.getLocation());
            if (zone != null) {
                org.bukkit.entity.Player player = event.getPlayer();
                PlayerRank rank = rankManager != null ? rankManager.getRank(player) : PlayerRank.DEFAULT;
                if (!player.isOp() && !rank.hasFullStaffAccess()) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot edit signs here.");
                    return;
                }
            }
        }

        // Block sign editing in any system-faction claim (spawn, etc.)
        String owner = manager.getClaimOwner(world, x, z);
        if (owner == null) return;
        FactionManager.Faction ownerFaction = manager.getFactionByName(owner);
        if (ownerFaction == null) return;
        if (manager.isSystemFaction(ownerFaction)) {
            org.bukkit.entity.Player player = event.getPlayer();
            if (!hasClaimAccess(player, owner, world, x, z, ClaimAccessPermission.BREAK_BLOCKS)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot edit signs here.");
            }
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

        if (!hasClaimAccess(event.getPlayer(), owner, world, x, z, ClaimAccessPermission.USE_TNT)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot ignite blocks/TNT in this claim.");
        }
    }

    /**
     * Prevent players from trampling farmland (turning it to dirt by jumping on it)
     * in any claimed territory they don't have build access to.
     * This protects crop farms at spawn and in faction claims.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFarmlandTrample(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player player)) return;
        if (event.getBlock().getType() != Material.FARMLAND) return;
        if (event.getTo() != Material.DIRT && event.getTo() != Material.COARSE_DIRT) return;

        Block block = event.getBlock();
        String world = block.getWorld().getName();
        int cx = block.getChunk().getX();
        int cz = block.getChunk().getZ();

        // Always protect farmland inside warzones (tracked separately from faction claims)
        if (warzoneManager != null) {
            WarzoneManager.WarzoneType zone = warzoneManager.getZoneTypeAt(block.getLocation());
            if (zone == WarzoneManager.WarzoneType.WARZONE || zone == WarzoneManager.WarzoneType.SAFEZONE) {
                event.setCancelled(true);
                return;
            }
        }

        String owner = manager.getClaimOwner(world, cx, cz);
        if (owner == null) return; // unclaimed – allow trampling

        if (!hasClaimAccess(player, owner, world, cx, cz, ClaimAccessPermission.BREAK_BLOCKS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // Collect spawners that will actually be destroyed (not protected by TNT flag)
        // so we can remove them from tracking after the block list is finalised.
        java.util.List<Block> spawnersToDrop = new java.util.ArrayList<>();

        event.blockList().removeIf(block -> {
            // Warzone / Safezone: always explosion-proof
            if (warzoneManager != null) {
                WarzoneManager.WarzoneType zone = warzoneManager.getZoneTypeAt(block.getLocation());
                if (zone != null) return true;
            }

            String world = block.getWorld().getName();
            int x = block.getChunk().getX();
            int z = block.getChunk().getZ();

            String owner = manager.getClaimOwner(world, x, z);
            if (owner == null) return false;

            FactionManager.Faction ownerFaction = manager.getFactionByName(owner);
            if (ownerFaction == null) return false;

            // System factions (Warzone claim, Safezone claim) are always explosion-proof
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

    /**
     * Block explosions (beds, respawn anchors, etc.) are also prevented in warzones/safezones
     * and any claimed territory with TNT protection enabled.
     */
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            // Warzone / Safezone: always explosion-proof
            if (warzoneManager != null) {
                WarzoneManager.WarzoneType zone = warzoneManager.getZoneTypeAt(block.getLocation());
                if (zone != null) return true;
            }

            String world = block.getWorld().getName();
            int x = block.getChunk().getX();
            int z = block.getChunk().getZ();

            String owner = manager.getClaimOwner(world, x, z);
            if (owner == null) return false;

            FactionManager.Faction ownerFaction = manager.getFactionByName(owner);
            if (ownerFaction == null) return false;

            if (manager.isSystemFaction(ownerFaction)) return true;

            return !ownerFaction.isFlagEnabled(ClaimFlag.TNT);
        });
    }

    private boolean hasClaimAccess(org.bukkit.entity.Player player, String ownerFactionName, String world, int x, int z, ClaimAccessPermission permission) {
        java.util.UUID playerId = player.getUniqueId();
        FactionManager.Faction playerFaction = manager.getFaction(playerId);
        if (playerFaction != null && playerFaction.getName().equalsIgnoreCase(ownerFactionName)) {
            return true;
        }

        FactionManager.Faction ownerFaction = manager.getFactionByName(ownerFactionName);
        if (ownerFaction != null && manager.isSystemFaction(ownerFaction)) {
            if (player.isOp()) return true;
            PlayerRank rank = rankManager != null ? rankManager.getRank(player) : PlayerRank.DEFAULT;
            if (rank.hasFullStaffAccess()) return true;
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

    /** Syncs faction chest view back to the backing inventory when a player closes it. */
    @EventHandler
    public void onFactionChestClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof FactionManager.FactionChestHolder holder)) return;
        FactionManager.Faction faction = manager.getFactionByName(holder.getFactionName());
        if (faction == null) return;
        faction.syncChestView(event.getInventory());
    }

    /** Blocks that should be restricted in warzones/safezones and system-faction claims. */
    private boolean isRestrictedInZone(Material material) {
        return material == Material.ANVIL
                || material == Material.CHIPPED_ANVIL
                || material == Material.DAMAGED_ANVIL
                || material == Material.ENCHANTING_TABLE
                || material == Material.BEACON
                || material.name().endsWith("_SIGN")
                || material.name().endsWith("_WALL_SIGN")
                || material.name().endsWith("_HANGING_SIGN")
                || material.name().endsWith("_WALL_HANGING_SIGN");
    }
}